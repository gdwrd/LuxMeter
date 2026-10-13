package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.model.CameraFormat
import com.example.data.model.CameraSetup
import com.example.ui.components.CalibrationDialog
import com.example.ui.components.CameraSetupDialog
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.meter.MeasurementSource
import com.example.ui.meter.ReadingStatus
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SetupFlowsInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val stores = mutableListOf<ViewModelStore>()
  private val ownedFilmNames = mutableSetOf<String>()
  private var calibrationToRestore: Pair<LightMeterViewModel, Float>? = null
  private val application: Application
    get() = ApplicationProvider.getApplicationContext()

  @After
  fun cleanUpOwnedState() {
    compose.runOnIdle {
      calibrationToRestore?.let { (model, offset) ->
        model.setCalibrationOffset(offset, MeasurementSource.CAMERA)
      }
      stores.forEach { it.clear() }
    }
    if (ownedFilmNames.isNotEmpty()) {
      runBlocking(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(application).filmStockDao()
        dao.getAllFilmStocks().first()
          .filter { it.isCustom && it.name in ownedFilmNames }
          .forEach { dao.deleteFilmStockById(it.id) }
      }
    }
  }

  private fun realMeter(): LightMeterViewModel {
    lateinit var model: LightMeterViewModel
    compose.runOnIdle {
      model = LightMeterViewModel(application)
      stores += ViewModelStore().also { it.put("setup-test", model) }
    }
    compose.waitUntil(timeoutMillis = 10_000) { model.setupReady.value }
    return model
  }

  @Test
  fun cameraSetupValidatesInputsAndKeepsActualMarkedSettings() {
    var saved: CameraSetup? = null
    compose.setContent {
      MyApplicationTheme {
        CameraSetupDialog(saving = false, onDismiss = {}, onSave = { saved = it })
      }
    }
    compose.onNodeWithText("Setup name").performScrollTo().performTextReplacement("Rangefinder test setup")
    compose.onNodeWithText("Lens focal length (mm)").performScrollTo().performTextReplacement("50")
    compose.onNodeWithText("Widest aperture (f-number)").performScrollTo()
      .performTextReplacement(NumberFormat.getNumberInstance().format(3.5))
    compose.onNodeWithText("Smallest aperture (f-number)").performScrollTo().performTextReplacement("22")
    compose.onNodeWithText("Shutter speeds").performScrollTo().performTextReplacement("1/0")
    compose.onNodeWithText("Save setup").assertIsNotEnabled()

    compose.onNodeWithText("Shutter speeds")
      .performTextReplacement("1/500 1/250 1/100 1/50 1/25 1/10 1/5 1/2 1")
    compose.onNodeWithText("Save setup").assertIsEnabled()
    compose.onNodeWithText("Setup name").performScrollTo().performTextReplacement("x".repeat(101))
    compose.onNodeWithText("Save setup").assertIsNotEnabled()
    compose.onNodeWithText("Use 100 characters or fewer.").assertExists()
    compose.onNodeWithText("Setup name").performTextReplacement("Rangefinder test setup")
    compose.onNodeWithText("Bulb available for longer exposures").performScrollTo().performClick()
    compose.onNodeWithText("Save setup").assertIsEnabled().performClick()

    compose.runOnIdle {
      val setup = saved
      assertNotNull(setup)
      assertEquals("Rangefinder test setup", setup!!.name)
      assertEquals(CameraFormat.FORMAT_35MM.name, setup.format)
      assertEquals(50, setup.focalLengthMm)
      assertEquals(3.5, setup.minAperture, 0.0)
      assertEquals(22.0, setup.maxAperture, 0.0)
      assertEquals(
        listOf(1.0 / 500, 1.0 / 250, 1.0 / 100, 1.0 / 50, 1.0 / 25, 1.0 / 10, 1.0 / 5, 0.5, 1.0),
        setup.shutterSpeeds.split(',').map(String::toDouble)
      )
      assertTrue(setup.allowsBulb)
    }
  }

  @Test
  fun unlistedFilmSavesNameAndIsoWithoutInventingAProfile() {
    val name = "setup-test-film-" + UUID.randomUUID().toString()
    ownedFilmNames += name
    val model = realMeter()
    compose.setContent { MyApplicationTheme { SettingsScreen(model) } }
    compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add film"))
    compose.onNodeWithText("Add film").performClick()
    compose.onNodeWithText("Film name").performScrollTo().performTextReplacement(name)
    compose.onNodeWithText("Box ISO").performScrollTo().performTextReplacement("125")
    compose.onNodeWithText("Reciprocity exponent").assertDoesNotExist()
    compose.onNodeWithText("Save film").assertIsEnabled().performClick()
    compose.waitUntil(timeoutMillis = 10_000) { model.allStocks.value.any { it.name == name } }
    compose.onNodeWithText("Add unlisted film").assertDoesNotExist()

    val stored = runBlocking(Dispatchers.IO) {
      AppDatabase.getDatabase(application).filmStockDao().getAllFilmStocks().first().single { it.name == name }
    }
    assertEquals(125, stored.iso)
    assertTrue(stored.isCustom)
    assertFalse(stored.hasReciprocityProfile)
    assertEquals(1.0, stored.reciprocityFactor, 0.0)
    assertNull(stored.calculateCorrectedExposure(10.0))
  }

  @Test
  fun heldCameraReadingProposesAndExplicitlyAppliesReferenceOffset() {
    val model = realMeter()
    var previousSensorOffset = 0f
    compose.runOnIdle {
      calibrationToRestore = model to model.calibrationOffsetFor(MeasurementSource.CAMERA)
      previousSensorOffset = model.calibrationOffsetFor(MeasurementSource.SENSOR)
      model.setMeasurementSource(MeasurementSource.CAMERA)
      model.setCalibrationOffset(0f, MeasurementSource.CAMERA)
      model.onOpticalEvMeasured(12.0)
      model.toggleHold()
      assertEquals(ReadingStatus.HELD, model.readingStatus.value)
    }
    var open by mutableStateOf(true)
    compose.setContent { MyApplicationTheme { if (open) CalibrationDialog(model) { open = false } } }
    compose.onNodeWithText("Reference aperture (f-number)").performScrollTo().performTextReplacement("4")
    compose.onNodeWithText("Reference shutter speed").performScrollTo().performTextReplacement("1/512")
    compose.onNodeWithText("Reference ISO").performScrollTo().performTextReplacement("100")
    compose.onNodeWithText(String.format(Locale.getDefault(), "%+.1f EV", 1f)).performScrollTo().assertIsDisplayed()
    compose.runOnIdle { assertEquals(0f, model.calibrationOffsetFor(MeasurementSource.CAMERA), 0f) }
    compose.onNodeWithText("Apply calibration").assertIsEnabled().performClick()
    compose.runOnIdle {
      assertFalse(open)
      assertEquals(1f, model.calibrationOffsetFor(MeasurementSource.CAMERA), 0.0001f)
      assertEquals(previousSensorOffset, model.calibrationOffsetFor(MeasurementSource.SENSOR), 0f)
    }
  }
}
