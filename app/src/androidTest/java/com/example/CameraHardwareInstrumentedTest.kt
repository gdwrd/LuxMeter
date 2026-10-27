package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.CameraFormat
import com.example.data.AppDatabase
import com.example.data.ShotPhotoStore
import com.example.sensor.HapticManager
import com.example.ui.components.CameraViewfinder
import com.example.ui.meter.ExposureMode
import com.example.ui.meter.LightMeterScreen
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.meter.MeasurementSource
import com.example.ui.meter.ReadingStatus
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt in with -e hardwareCamera true, camera permission granted and a well-lit scene.
 * Use test data or back up Preview first: temporary rolls/photos are cleaned, but meter preferences change.
 */
@RunWith(AndroidJUnit4::class)
class CameraHardwareInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun realMeterSwipesAndAutomaticallySavesTheHeldPhoto() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("hardwareCamera") == "true")
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(PackageManager.PERMISSION_GRANTED, ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
    val repository = AppDatabase.getDatabase(context).repository()
    val film = runBlocking(Dispatchers.IO) { repository.allFilmStocks.first().first { it.badgeStyle == "HP5" } }
    val output = File(context.cacheDir, "hardware-camera-check").apply { mkdirs() }
    val report = StringBuilder("Production meter, real CameraX reading and automatic image save.\n")
    val store = ViewModelStore()
    lateinit var model: LightMeterViewModel
    var ownedRollId: String? = null
    var showing by mutableStateOf(true)
    try {
      compose.runOnIdle {
        model = LightMeterViewModel(context.applicationContext as Application)
        store.put("hardware-meter", model)
      }
      compose.waitUntil(timeoutMillis = 10_000) { model.setupReady.value }
      compose.runOnIdle {
        model.startNewRoll("Hardware check ${UUID.randomUUID()}", 36)
        ownedRollId = model.currentRollId.value
        model.selectCameraSetup(null)
      }
      compose.waitUntil(timeoutMillis = 5_000) { !model.isSaving.value && model.currentCameraSetup.value == null }
      compose.runOnIdle {
        model.setFilmStock(film)
        model.setMeasurementSource(MeasurementSource.CAMERA)
        model.setFormat(CameraFormat.FORMAT_35MM)
        model.setFocalLength(50)
        model.setExposureMode(ExposureMode.APERTURE_PRIORITY)
        model.setApertureDirectly("f/5.6")
        assertEquals(film.id, model.selectedFilmStock.value?.id)
      }
      compose.setContent { MyApplicationTheme { if (showing) LightMeterScreen(model) } }
      compose.waitUntil(timeoutMillis = 30_000) { model.readingStatus.value == ReadingStatus.LIVE }
      val beforeAperture = model.selectedApertureIndex.value
      compose.onNodeWithContentDescription("Aperture dial").performScrollTo().performTouchInput { swipeRight() }
      compose.runOnIdle { assertTrue("Aperture swipe must change the setting", model.selectedApertureIndex.value > beforeAperture) }
      report.append("apertureSwipe=$beforeAperture->${model.selectedApertureIndex.value}\n")
      compose.onNodeWithText("Choose aperture").performScrollTo().performClick()
      compose.onNodeWithText("Choose shutter").performClick()
      val beforeShutter = model.selectedShutterIndex.value
      compose.onNodeWithContentDescription("Shutter dial").performScrollTo().performTouchInput { swipeLeft() }
      compose.runOnIdle { assertTrue("Shutter swipe must change the setting", model.selectedShutterIndex.value < beforeShutter) }
      report.append("shutterSwipe=$beforeShutter->${model.selectedShutterIndex.value}\n")
      compose.onNodeWithText("Choose shutter").performScrollTo().performClick()
      compose.onNodeWithText("Choose aperture").performClick()
      compose.waitUntil(timeoutMillis = 15_000) { model.readingStatus.value == ReadingStatus.LIVE }
      compose.onNodeWithText("Hold reading").assertIsEnabled().performClick()
      compose.waitUntil(timeoutMillis = 5_000) {
        compose.onAllNodesWithContentDescription("Held camera frame").fetchSemanticsNodes().isNotEmpty()
      }
      compose.waitForIdle()
      compose.onNodeWithText("Save frame").assertIsEnabled().performClick()
      compose.waitUntil(timeoutMillis = 10_000) { model.savedEntry.value != null && !model.isSaving.value }
      val entry = model.savedEntry.value!!
      assertEquals(ownedRollId, entry.rollId)
      assertTrue("Saved exposure must retain its real camera reading", entry.hasMeterReading && !entry.isManual)
      assertEquals(MeasurementSource.CAMERA.name, entry.meteringSource)
      assertNotNull("Meter save must attach its held image", entry.photoFileName)
      val photo = model.photoFile(entry.photoFileName)
      assertNotNull("Automatic image must exist on disk", photo)
      val image = BitmapFactory.decodeFile(photo!!.absolutePath)
      assertNotNull("Automatic image must decode", image)
      assertTrue("Reference photo must stay small", maxOf(image.width, image.height) <= 512)
      assertTrue("B&W film must save a B&W image", statistics(image).maxChroma <= 3)
      report.append("entry=${entry.id}, frame=${entry.frameNumber}, aperture=${entry.apertureText}, shutter=${entry.shutterText}\n")
      save(image, File(output, "meter-saved-monochrome.png"), report)
      report.append("result=PASS\n")
      image.recycle()
    } catch (error: Throwable) {
      report.append("result=FAIL: ${error.javaClass.simpleName}: ${error.message}\n")
      throw error
    } finally {
      File(output, "meter-save-report.txt").writeText(report.toString())
      compose.runOnIdle { showing = false }
      compose.waitForIdle()
      compose.runOnIdle { store.clear() }
      runBlocking(Dispatchers.IO) {
        ownedRollId?.let { id ->
          val photos = repository.getEntriesForRoll(id).first().mapNotNull { it.photoFileName }
          repository.deleteRoll(id)
          val photoStore = ShotPhotoStore(context)
          photos.forEach { photoStore.delete(it) }
        }
      }
    }
  }

  @Test
  fun realCameraDisplaysMonochromeAndPreservesTheHeldSnapshot() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("hardwareCamera") == "true")
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    assertEquals("Grant camera permission before this opt-in check", PackageManager.PERMISSION_GRANTED,
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
    val output = File(context.cacheDir, "hardware-camera-check").apply { mkdirs() }
    val report = StringBuilder("Real CameraX preview; no reference-meter accuracy claim.\n")
    val readingCount = AtomicInteger()
    val lastEv = AtomicReference<Double?>()
    val cameraStatus = AtomicReference<String?>()
    val held = AtomicReference<Bitmap?>()
    var monochrome by mutableStateOf(false)
    var locked by mutableStateOf(false)
    try {
      compose.setContent {
        MyApplicationTheme {
          CameraViewfinder(
            format = CameraFormat.FORMAT_35MM, focalLengthMm = 50,
            onFocalLengthChanged = {}, hapticManager = HapticManager(context),
            isLocked = locked, onToggleLock = { locked = !locked },
            onMeasuredEvChanged = { lastEv.set(it); readingCount.incrementAndGet() },
            onCameraStatusChanged = { status, _ -> cameraStatus.set(status) },
            isBlackAndWhite = monochrome, onHeldFrameChanged = { held.set(it) }
          )
        }
      }
      compose.waitUntil(timeoutMillis = 30_000) { readingCount.get() > 0 }
      assertTrue(lastEv.get()!!.isFinite())
      save(screenCentre(), File(output, "live-colour.png"), report)
      report.append("initialEv100=${lastEv.get()}\n")

      compose.runOnIdle { monochrome = true }
      var liveMonochrome: Bitmap? = null
      compose.waitUntil(timeoutMillis = 5_000) {
        screenCentre().let { image ->
          val stats = statistics(image)
          if (stats.maxChroma <= 3 && stats.maxLuma > 2) { liveMonochrome = image; true }
          else { image.recycle(); false }
        }
      }
      save(liveMonochrome!!, File(output, "live-monochrome.png"), report)

      compose.runOnIdle { locked = true }
      compose.waitUntil(timeoutMillis = 5_000) { held.get() != null }
      val snapshot = held.get()!!
      assertTrue("Held camera snapshot must have pixels", snapshot.width > 0 && snapshot.height > 0)
      assertTrue("Exported held snapshot must be monochrome", statistics(snapshot).maxChroma <= 1)
      val frozenPixels = pixels(snapshot)
      save(snapshot, File(output, "held-monochrome.png"), report)
      SystemClock.sleep(400)
      assertNotNull(held.get())
      assertArrayEquals("Hold must not update the saved frame", frozenPixels, pixels(held.get()!!))

      compose.runOnIdle { monochrome = false }
      compose.waitUntil(timeoutMillis = 5_000) { held.get() != null && held.get() !== snapshot }
      save(held.get()!!, File(output, "held-colour.png"), report)
      val previousReadings = readingCount.get()
      compose.runOnIdle { locked = false }
      compose.waitUntil(timeoutMillis = 15_000) { held.get() == null && readingCount.get() > previousReadings }
      save(screenCentre(), File(output, "resumed-colour.png"), report)
      report.append("resumedEv100=${lastEv.get()}\nresult=PASS\n")
    } catch (error: Throwable) {
      report.append("result=FAIL: ${error.javaClass.simpleName}: ${error.message}\n")
      runCatching { save(screenCentre(), File(output, "failure-preview.png"), report) }
      throw error
    } finally {
      report.append("lastCameraStatus=${cameraStatus.get()}\nreadings=${readingCount.get()}\n")
      File(output, "report.txt").writeText(report.toString())
    }
  }

  private fun screenCentre(): Bitmap {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val bounds = Rect()
    instrumentation.runOnMainSync {
      val preview = findPreview(compose.activity.window.decorView) ?: error("PreviewView is absent")
      val position = IntArray(2).also(preview::getLocationOnScreen)
      bounds.set(position[0] + preview.width / 5, position[1] + preview.height / 5,
        position[0] + preview.width * 4 / 5, position[1] + preview.height * 4 / 5)
    }
    val screen = instrumentation.uiAutomation.takeScreenshot() ?: error("Screen capture is unavailable")
    require(bounds.width() > 0 && bounds.height() > 0 && bounds.left >= 0 && bounds.top >= 0 &&
      bounds.right <= screen.width && bounds.bottom <= screen.height) { "Preview is outside the visible screen: $bounds" }
    val crop = Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())
    val readable = crop.copy(Bitmap.Config.ARGB_8888, false) ?: error("Screen pixels are unavailable")
    crop.recycle()
    screen.recycle()
    return readable
  }

  private fun findPreview(view: View): PreviewView? {
    if (view is PreviewView) return view
    if (view is ViewGroup) for (index in 0 until view.childCount) findPreview(view.getChildAt(index))?.let { return it }
    return null
  }

  private data class PixelStatistics(val maxChroma: Int, val meanChroma: Double, val maxLuma: Int)

  private fun statistics(bitmap: Bitmap): PixelStatistics {
    var maxChroma = 0
    var totalChroma = 0L
    var maxLuma = 0
    var samples = 0
    for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
      val pixel = bitmap.getPixel(x, y)
      val channels = intArrayOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
      val chroma = channels.max() - channels.min()
      maxChroma = maxOf(maxChroma, chroma)
      maxLuma = maxOf(maxLuma, channels.sum() / 3)
      totalChroma += chroma
      samples++
    }
    return PixelStatistics(maxChroma, totalChroma.toDouble() / samples, maxLuma)
  }

  private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
  }

  private fun save(bitmap: Bitmap, file: File, report: StringBuilder) {
    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    report.append("${file.name}: ${bitmap.width}x${bitmap.height}, ${statistics(bitmap)}\n")
  }
}
