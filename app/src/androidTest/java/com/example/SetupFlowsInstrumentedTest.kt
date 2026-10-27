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
import com.example.ui.settings.RollsScreen
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
  private val ownedRollIds = mutableSetOf<String>()
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
    runBlocking(Dispatchers.IO) {
      val repository = AppDatabase.getDatabase(application).repository()
      ownedRollIds.forEach { repository.deleteRoll(it) }
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
  fun newRollStartsAtNormalUnlessPushIsExplicitlyChosen() {
    val model = realMeter()
    val hp5 = runBlocking(Dispatchers.IO) {
      AppDatabase.getDatabase(application).filmStockDao().getAllFilmStocks().first().first { it.badgeStyle == "HP5" }
    }
    compose.runOnIdle {
      model.startNewRoll("Push test ${UUID.randomUUID()}")
      ownedRollIds += model.currentRollId.value
      assertTrue(model.applyFilmSettings(hp5, 1600, 2))
    }
    compose.setContent { MyApplicationTheme { RollsScreen(model) } }
    compose.onNodeWithText("Start a new roll").performClick()
    compose.onNodeWithText("Meter at EI 400").assertExists()
    compose.onNodeWithText("Ask the lab: Normal processing").assertExists()
    compose.onNodeWithText("+2").performScrollTo().performClick()
    compose.onNodeWithText("Meter at EI 1600").assertExists()
    compose.onNodeWithText("Ask the lab: Push +2 stops").assertExists()
    val rollName = "Keyboard push test ${UUID.randomUUID()}"
    val previousRollId = model.currentRollId.value
    compose.onNodeWithText("Roll name").performScrollTo().performClick().performTextReplacement(rollName)
    compose.waitForIdle()
    compose.onNodeWithText("Start roll").assertIsDisplayed().performClick()
    compose.waitUntil(timeoutMillis = 10_000) {
      model.allRolls.value.any { it.name == rollName } && !model.isSaving.value
    }
    compose.runOnIdle {
      val created = model.allRolls.value.single { it.name == rollName }
      ownedRollIds += created.id
      assertEquals(previousRollId, model.currentRollId.value)
      assertEquals(rollName, created.name)
      assertEquals(1600, created.iso)
      assertEquals(2, created.pushStops)
      assertEquals(400, created.boxIso)
    }
    compose.onNodeWithText("Continue this roll").performScrollTo().performClick()
    compose.waitUntil(timeoutMillis = 10_000) { model.currentRollId.value != previousRollId && !model.isSaving.value }
    compose.runOnIdle {
      assertEquals(rollName, model.currentRoll.value!!.name)
      assertEquals(1600, model.currentRoll.value!!.iso)
      assertEquals(2, model.currentRoll.value!!.pushStops)
      assertEquals(400, model.currentRoll.value!!.boxIso)
    }
    compose.onNodeWithText("Processing requested: Push +2 stops").assertExists()
    val portra = runBlocking(Dispatchers.IO) {
      AppDatabase.getDatabase(application).filmStockDao().getAllFilmStocks().first().first { it.badgeStyle == "PORTRA" }
    }
    compose.runOnIdle { model.setCurrentFrame(1) }
    compose.waitUntil(timeoutMillis = 10_000) { model.currentRoll.value?.lastFrameNumber == 1 && !model.isSaving.value }
    compose.runOnIdle { model.setFilmStock(portra) }
    compose.onNodeWithText("Edit film and processing").performClick()
    compose.onNodeWithText("Meter at EI 1600").assertExists()
    compose.onNodeWithText("Ask lab: Push +2 stops").assertExists()
    compose.onNodeWithText("Use for this roll").performClick()
    compose.runOnIdle {
      assertEquals(hp5.id, model.selectedFilmStock.value!!.id)
      assertEquals(2, model.currentRoll.value!!.pushStops)
      assertEquals(1600, model.activeIso)
    }
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
