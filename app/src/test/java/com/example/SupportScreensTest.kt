package com.example

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.FilmLogDao
import com.example.data.dao.FilmStockDao
import com.example.data.model.FilmLogEntry
import com.example.data.model.CameraSetup
import com.example.data.model.FilmStock
import com.example.data.repository.FilmRepository
import com.example.sensor.ExposureCalculator
import com.example.sensor.HapticManager
import com.example.ui.components.ExposureTimerDialog
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.meter.LightMeterScreen
import com.example.ui.meter.ExposureMode
import com.example.ui.meter.MeasurementSource
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.RollsScreen
import com.example.ui.sunny16.Sunny16GuideScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SupportScreensTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var application: Application
  private val store = ViewModelStore()
  private val stocks = TestStocks()
  private val logs = TestLogs()

  @Before
  fun setUp() {
    application = ApplicationProvider.getApplicationContext()
    application.getSharedPreferences("meter_setup", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @After
  fun tearDown() {
    store.clear()
  }

  private fun meter(): LightMeterViewModel {
    lateinit var model: LightMeterViewModel
    compose.runOnIdle {
      model = LightMeterViewModel(application, FilmRepository(stocks, logs))
      store.put("meter", model)
    }
    return model
  }

  @Test
  fun sunny16KeepsTheEntryRatingAndFilmBoxSpeedAfterChangingIso() {
    val model = meter()
    model.setFilmStock(FilmStock(
      id = 7, name = "Test 125", brand = "Test", iso = 125, type = "Black & White",
      reciprocityFactor = 1.31, description = "", badgeStyle = "CUSTOM"
    ))
    model.setCustomIso(80)
    compose.setContent { MyApplicationTheme { Sunny16GuideScreen(model) } }
    compose.onNodeWithText("ISO 80").performClick()
    compose.onNodeWithText("ISO 400").performClick()
    compose.onNodeWithText("ISO 400").performClick()
    compose.onNodeWithText("ISO 80").assertExists()
    compose.onNodeWithText("ISO 125").assertExists()
  }

  @Test
  fun timerRejectsDurationsAboveTheSharedLimit() {
    compose.setContent {
      MyApplicationTheme {
        ExposureTimerDialog(60.0, ExposureCalculator.MAX_EXPOSURE_SECONDS + 1.0, "Test film", HapticManager(application), {})
      }
    }
    compose.onNodeWithText("Start timer").assertDoesNotExist()
    compose.onNodeWithText("This exposure cannot be timed.", substring = true).assertExists()
  }

  @Test
  fun timerAcceptsTheSharedLimit() {
    compose.setContent {
      MyApplicationTheme {
        ExposureTimerDialog(60.0, ExposureCalculator.MAX_EXPOSURE_SECONDS, "Test film", HapticManager(application), {})
      }
    }
    compose.onNodeWithText("Start timer").assertIsEnabled()
  }

  @Test
  fun shutterPriorityLogShowsActualTimeAndNumericMeteredTime() {
    val model = meter()
    compose.runOnIdle {
      model.setFilmStock(hp5)
      model.setPresetEv100(0.0)
      model.setExposureMode(ExposureMode.SHUTTER_PRIORITY)
      model.setShutterIndex(ExposureCalculator.SHUTTER_LABELS.indexOf("30s"))
      model.toggleHold()
      model.logExposure()
    }
    compose.runOnIdle {
      assertEquals(30.0, logs.entries.value.single().correctedSeconds, 0.000001)
      assertEquals(13.4, logs.entries.value.single().rawSeconds, 0.1)
    }
    compose.setContent { MyApplicationTheme { RollsScreen(model) } }
    compose.onNodeWithText("Current roll").performClick()
    compose.onNodeWithText(
      logs.entries.value.single().apertureText + " · 30s"
    ).assertIsDisplayed()
    compose.onNodeWithText("Metered 13s · film correction applied").assertIsDisplayed()
    compose.onNodeWithText("Metered 30s · film correction applied").assertDoesNotExist()
  }

  @Test
  fun unsupportedCorrectionDisablesSaveButKeepsFilmRecoveryAvailable() {
    val model = meter()
    compose.runOnIdle {
      model.setFilmStock(portra)
      model.setPresetEv100(-3.0)
      model.setApertureIndex(ExposureCalculator.APERTURE_LABELS.indexOf("f/16"))
      model.toggleHold()
    }
    compose.setContent { MyApplicationTheme { LightMeterScreen(model) } }
    compose.onNodeWithText("Save frame").assertIsNotEnabled()
    compose.onNodeWithText("Portra 400").assertIsEnabled().performClick()
    compose.onNodeWithText("Film").assertIsDisplayed()
    compose.onNodeWithText("HP5 Plus 400").performClick()
    compose.onNodeWithText("Save frame").assertIsEnabled()
  }

  @Test
  fun runningTimerRemainsOpenWhenLiveCorrectionBecomesUnsupported() {
    val model = meter()
    compose.runOnIdle {
      model.setFilmStock(portra)
      model.setPresetEv100(4.0)
      model.setApertureIndex(ExposureCalculator.APERTURE_LABELS.indexOf("f/8"))
    }
    compose.setContent { MyApplicationTheme { LightMeterScreen(model) } }
    compose.onNodeWithText("Timer").performScrollTo().performClick()
    compose.onNodeWithText("Start timer").assertIsEnabled()
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText("Start timer").performClick()
    compose.mainClock.advanceTimeByFrame()
    compose.runOnIdle { model.setPresetEv100(3.0) }
    repeat(2) { compose.mainClock.advanceTimeByFrame() }

    compose.runOnIdle {
      assertNull(model.getCorrectedShutterSeconds())
      assertTrue(model.showTimerDialog.value)
    }
    compose.onNodeWithText("Exposure timer").assertExists()
    compose.onNodeWithText("Pause").assertExists()
    compose.onNodeWithText("This exposure cannot be timed.", substring = true).assertDoesNotExist()
    compose.onNodeWithText("Pause").performClick()
    repeat(2) { compose.mainClock.advanceTimeByFrame() }
    compose.mainClock.autoAdvance = true
  }

  @Test
  fun timerKeepsItsOpeningExposureAcrossStateRestoration() {
    val liveDuration = mutableStateOf(30.0)
    val restoration = StateRestorationTester(compose)
    restoration.setContent {
      MyApplicationTheme {
        ExposureTimerDialog(13.4, liveDuration.value, "HP5 Plus 400", HapticManager(application), {})
      }
    }
    compose.runOnIdle { liveDuration.value = 0.0 }
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithText(String.format(Locale.getDefault(), "%.1f s", 30.0)).assertExists()
    compose.onNodeWithText("Start timer").assertIsEnabled()
  }

  @Test
  fun finishedRollCanBeReopenedWithoutLosingItsPhysicalFrame() {
    val model = meter()
    compose.runOnIdle { model.startNewRoll("Autumn", 24) }
    compose.runOnIdle { model.setCurrentFrame(8) }
    compose.runOnIdle { model.finishCurrentRoll() }
    compose.runOnIdle { model.startNewRoll("Winter", 36) }
    compose.setContent { MyApplicationTheme { RollsScreen(model) } }
    compose.onNodeWithText("Finished rolls").assertExists()
    compose.onNodeWithText("Autumn").performClick()
    compose.onNodeWithText("Reopen roll").performClick()
    compose.runOnIdle {
      assertEquals("Autumn", model.currentRoll.value!!.name)
      assertEquals(8, model.currentRoll.value!!.lastFrameNumber)
      assertEquals(24, model.currentRoll.value!!.frameCapacity)
      assertFalse(model.currentRoll.value!!.isFinished)
    }
    compose.onNodeWithText("Finish roll").assertIsEnabled()
  }

  @Test
  fun savedNotesAppearWithoutChangingExposureFields() {
    val model = meter()
    compose.runOnIdle {
      model.setFilmStock(hp5)
      model.setPresetEv100(12.0)
      model.toggleHold()
      model.logExposure()
    }
    lateinit var before: FilmLogEntry
    compose.runOnIdle { before = logs.entries.value.single() }
    compose.setContent { MyApplicationTheme { RollsScreen(model) } }
    compose.onNodeWithText("Current roll").performClick()
    compose.runOnIdle { model.updateLogNotes(before.id, "Red door, yellow filter") }
    compose.onNodeWithText("Red door, yellow filter").assertIsDisplayed()
    compose.runOnIdle { assertEquals(before.copy(notes = "Red door, yellow filter"), logs.entries.value.single()) }
  }

  @Test
  fun settingsCanChangeTheInactiveSourcesCalibration() {
    val model = meter()
    compose.runOnIdle {
      model.setCalibrationOffset(0.5f, MeasurementSource.CAMERA)
      model.setCalibrationOffset(-1f, MeasurementSource.SENSOR)
    }
    compose.setContent { MyApplicationTheme { SettingsScreen(model) } }
    compose.onNodeWithText("Manual adjustment").performScrollTo().performClick()
    compose.onNodeWithText("Light sensor").performClick()
    compose.onNodeWithText(String.format(Locale.getDefault(), "%+.1f EV", -1f)).assertExists()
    compose.runOnIdle { model.setCalibrationOffset(-1.5f, MeasurementSource.SENSOR) }
    compose.onNodeWithText(String.format(Locale.getDefault(), "%+.1f EV", -1.5f)).assertExists()
    compose.runOnIdle { assertEquals(0.5f, model.calibrationOffsetFor(MeasurementSource.CAMERA), 0f) }
  }

  @Test
  fun savedCameraCanBeSelectedFromSettings() {
    val model = meter()
    compose.runOnIdle {
      model.saveCameraSetup(CameraSetup(name = "Canon P", minAperture = 2.8, maxAperture = 16.0,
        shutterSpeeds = "0.008,0.016666666666666666,1.0"))
    }
    compose.setContent { MyApplicationTheme { SettingsScreen(model) } }
    compose.onNodeWithText("Canon P").assertExists()
    compose.onNodeWithText("Use without a saved camera").performClick()
    compose.runOnIdle { assertNull(model.currentCameraSetup.value) }
    compose.onNodeWithText("Canon P").performClick()
    compose.runOnIdle { assertEquals("Canon P", model.currentCameraSetup.value!!.name) }
  }

  private class TestStocks : FilmStockDao {
    override fun getAllFilmStocks() = MutableStateFlow(listOf(hp5, portra))
    override suspend fun getFilmStockById(id: Int): FilmStock? = null
    override suspend fun insertFilmStock(stock: FilmStock) = stock.id.toLong()
    override suspend fun insertAllStocks(stocks: List<FilmStock>) = Unit
    override suspend fun deleteFilmStockById(id: Int) = Unit
  }

  private class TestLogs : FilmLogDao {
    val entries = MutableStateFlow(emptyList<FilmLogEntry>())
    override fun getAllLogEntries() = entries
    override fun getEntriesForRoll(rollId: String) = entries.map { list -> list.filter { it.rollId == rollId } }
    override suspend fun getLatestEntryForRoll(rollId: String) =
      entries.value.filter { it.rollId == rollId }.maxByOrNull { it.frameNumber }
    private var nextId = 1
    override suspend fun insertLogEntry(entry: FilmLogEntry): Long {
      val id = if (entry.id > 0) entry.id else nextId++
      entries.value = entries.value.filterNot { it.id == id } + entry.copy(id = id)
      return id.toLong()
    }
    override suspend fun updateLogNotes(id: Int, notes: String) {
      entries.value = entries.value.map { if (it.id == id) it.copy(notes = notes) else it }
    }
    override suspend fun deleteEntryById(id: Int) { entries.value = entries.value.filterNot { it.id == id } }
    override suspend fun deleteRollEntries(rollId: String) { entries.value = entries.value.filterNot { it.rollId == rollId } }
  }

  private companion object {
    val hp5 = FilmStock(
      id = 7, name = "HP5 Plus 400", brand = "Ilford", iso = 400, type = "Black & White",
      reciprocityFactor = 1.31, description = "", badgeStyle = "HP5"
    )
    val portra = FilmStock(
      id = 8, name = "Portra 400", brand = "Kodak", iso = 400, type = "Color Negative",
      reciprocityFactor = 1.22, description = "", badgeStyle = "PORTRA"
    )
  }
}
