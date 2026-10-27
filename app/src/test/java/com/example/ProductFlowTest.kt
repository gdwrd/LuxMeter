package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.FilmLogDao
import com.example.data.dao.FilmStockDao
import com.example.data.model.CameraFormat
import com.example.data.model.CameraSetup
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmRoll
import com.example.data.model.FilmStock
import com.example.data.repository.FilmRepository
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.meter.ExposureMode
import com.example.ui.meter.MeasurementSource
import com.example.ui.meter.ReadingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.log2
import kotlin.math.pow
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProductFlowTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var application: Application
  private val stores = mutableListOf<ViewModelStore>()
  private val stocks = Stocks()
  private val logs = Logs()
  private val repository = FilmRepository(stocks, logs)

  @Before fun setUp() {
    Dispatchers.setMain(dispatcher)
    application = ApplicationProvider.getApplicationContext()
    for (name in listOf("meter_setup", "meter_device_calibration")) {
      application.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
  }

  @After fun tearDown() {
    stores.forEach { it.clear() }
    dispatcher.scheduler.runCurrent()
    Dispatchers.resetMain()
  }

  private fun drain() = dispatcher.scheduler.advanceUntilIdle()

  private fun finishSave(model: LightMeterViewModel) {
    val deadline = System.nanoTime() + 5_000_000_000L
    do {
      drain()
      if (!model.isSaving.value) return
      Thread.sleep(5)
    } while (System.nanoTime() < deadline)
    assertFalse("Photo IO should complete", model.isSaving.value)
  }

  private fun meter(): LightMeterViewModel = LightMeterViewModel(application, repository).also { model ->
    stores += ViewModelStore().also { it.put("meter", model) }
    drain()
    assertTrue(model.setupReady.value)
  }

  private fun camera(model: LightMeterViewModel, setup: CameraSetup): Int {
    model.saveCameraSetup(setup)
    drain()
    return model.cameraSetupSavedId.value!!
  }

  @Test fun sparseShutterDialsRoundToTheNearestStepInsteadOfReportingOutOfRange() {
    val model = meter()
    model.setIso(100)
    camera(model, CameraSetup(name = "Box camera", shutterSpeeds = "1/30,1/125,1/500", allowsBulb = false))
    model.setApertureDirectly("f/8")
    // The metered 1/60 sits a full stop from the nearest dial value on a dial with two-stop gaps.
    model.onOpticalEvMeasured(log2(64.0 * 60.0))
    assertEquals(1.0 / 30, model.getCorrectedShutterSeconds()!!, 0.000001)
    assertFalse(model.getExposureGuidance().orEmpty().contains("Outside"))
    // Beyond the fastest speed there is no neighbour to round to, so the scene is out of range.
    model.onOpticalEvMeasured(log2(64.0 * 4000.0))
    assertNull(model.getCorrectedShutterSeconds())
    assertTrue(model.getExposureGuidance()!!.contains("Outside"))
  }

  @Test fun newRollsKeepTheChosenExposureIndex() {
    val model = meter()
    model.setFilmStock(stocks.hp5)
    model.setIso(320)
    model.startNewRoll("Next roll")
    drain()
    assertEquals(320, model.activeIso)
    assertEquals(320, model.currentRoll.value!!.iso)
  }

  @Test fun aCustomEiChosenAfterAPushCarriesToTheNextRoll() {
    val model = meter()
    assertTrue(model.applyFilmSettings(stocks.hp5, 1600, 2))
    model.setIso(1250)
    model.startNewRoll("Next roll")
    drain()
    assertEquals(0, model.pushStops.value)
    assertEquals(1250, model.activeIso)
  }

  @Test fun positiveCompensationAddsExposureAndFiltersAreAppliedBeforeFilmCorrection() {
    val model = meter()
    model.setIso(100)
    model.setApertureDirectly("f/4")
    model.onOpticalEvMeasured(6.0)
    assertEquals(0.25, model.getCorrectedShutterSeconds()!!, 0.000001)
    model.setExposureCompensation(1.0)
    assertEquals(0.5, model.getCorrectedShutterSeconds()!!, 0.000001)
    assertEquals(6.0, model.computeEv100(), 0.000001)

    model.resetExposureAdjustments()
    model.setFilmStock(stocks.hp5)
    model.setIso(100)
    model.onOpticalEvMeasured(4.0)
    model.setFilterStops(2.0)
    assertEquals(1.0, model.getBaseMeteredSeconds()!!, 0.000001)
    assertEquals(4.0, model.getFilteredMeteredSeconds()!!, 0.000001)
    assertEquals(4.0, model.getRawShutterSeconds()!!, 0.000001)
    assertEquals(6.15, model.getCorrectedShutterSeconds()!!, 0.01)
  }

  @Test fun calibrationBelongsToItsSourceAndReferenceComparisonIgnoresCreativeAdjustments() {
    val model = meter()
    model.setCalibrationOffset(0.75f, MeasurementSource.CAMERA)
    model.setCalibrationOffset(-0.5f, MeasurementSource.SENSOR)
    model.onOpticalEvMeasured(4.0)
    model.setExposureCompensation(1.0)
    model.setFilterStops(2.0)
    model.toggleHold()
    assertEquals(4.75, model.computeEv100(), 0.000001)
    assertEquals(1f, model.proposeCalibrationOffset(4.0, 0.5, 100)!!, 0.000001f)
    model.setCalibrationOffset(1f, MeasurementSource.CAMERA)
    assertEquals(5.0, model.computeEv100(), 0.000001)

    model.setMeasurementSource(MeasurementSource.SENSOR)
    model.onSensorLuxMeasured(40f)
    model.toggleHold()
    assertEquals(3.5, model.computeEv100(), 0.000001)
    assertEquals(-1f, model.proposeCalibrationOffset(4.0, 2.0, 100)!!, 0.000001f)
    model.setPresetEv100(4.0)
    assertEquals(4.0, model.computeEv100(), 0.000001)
    assertNull(model.proposeCalibrationOffset(4.0, 0.5, 100))

    stores.last().clear()
    val restored = meter()
    assertEquals(1f, restored.calibrationOffsetFor(MeasurementSource.CAMERA), 0f)
    assertEquals(-0.5f, restored.calibrationOffsetFor(MeasurementSource.SENSOR), 0f)
    assertEquals(0f, restored.calibrationOffsetFor(MeasurementSource.SUNNY_16), 0f)
  }

  @Test fun cameraDialsKeepNonstandardValuesAndOnlyBulbCanExtendPastTheLastDial() {
    val model = meter()
    val setup = CameraSetup(name = "Leaf shutter", format = CameraFormat.MEDIUM_66.name,
      focalLengthMm = 80, minAperture = 3.5, maxAperture = 22.0,
      shutterSpeeds = "1/200,1/100,1/25,1", allowsBulb = false)
    val id = camera(model, setup)
    assertEquals(3.5, model.availableApertures.first(), 0.0)
    assertEquals(22.0, model.availableApertures.last(), 0.0)
    assertEquals(listOf(0.005, 0.01, 0.04, 1.0), model.availableShutterSeconds)
    model.setIso(100)
    model.setApertureDirectly("f/3.5")
    model.onOpticalEvMeasured(log2(3.5 * 3.5 / 0.04))
    assertEquals(3.5, model.availableApertures[model.selectedApertureIndex.value], 0.0)
    assertEquals(0.04, model.getCorrectedShutterSeconds()!!, 0.000001)
    assertEquals("1/25s", model.shutterLabels[model.selectedShutterIndex.value])

    model.setApertureDirectly("f/4")
    model.onOpticalEvMeasured(1.5)
    assertNull(model.getCorrectedShutterSeconds())
    assertNotNull(model.getExposureGuidance())
    camera(model, setup.copy(id = id, allowsBulb = true))
    model.onOpticalEvMeasured(1.5)
    assertEquals(5.656854249, model.getRawShutterSeconds()!!, 0.000001)
    assertEquals(5.656854249, model.getCorrectedShutterSeconds()!!, 0.000001)
    assertFalse(model.availableShutterSeconds.contains(model.getCorrectedShutterSeconds()!!))
  }

  @Test fun sunny16KeepsTheGuideReferenceWhenTheCameraCannotDialThoseExactValues() {
    val model = meter()
    camera(model, CameraSetup(name = "Older shutter", minAperture = 3.5, maxAperture = 11.0,
      shutterSpeeds = "1/250,1/100,1/25,1", allowsBulb = false))
    for (previousShutter in listOf("1/25", "1s")) {
      model.setApertureDirectly("f/4")
      model.setShutterDirectly(previousShutter)
      model.applySunny16Preset("f/16", "1/125", 100)
      assertEquals(MeasurementSource.SUNNY_16, model.measurementSource.value)
      assertEquals(log2(16.0 * 16.0 * 125.0), model.computeEv100(), 0.000001)
      assertEquals(11.0, model.availableApertures[model.selectedApertureIndex.value], 0.0)
      assertEquals(1.0 / 250, model.getCorrectedShutterSeconds()!!, 0.000001)
    }
  }

  @Test fun aperturePriorityUsesExactBulbTimesAndActualDialsWithFilmCorrection() {
    val model = meter()
    model.setFilmStock(stocks.hp5)
    model.setIso(100)
    model.setApertureDirectly("f/4")
    val scene = log2(16.0 / 1.5)
    model.onOpticalEvMeasured(scene)
    assertEquals(1.5, model.getRawShutterSeconds()!!, 0.000001)
    assertEquals(1.5.pow(1.31), model.getCorrectedShutterSeconds()!!, 0.000001)
    val setup = CameraSetup(name = "Actual dial", minAperture = 4.0, maxAperture = 16.0,
      shutterSpeeds = "1/8,1/4,1/2,1,2,4", allowsBulb = false)
    camera(model, setup)
    model.onOpticalEvMeasured(scene)
    assertEquals(2.0, model.getCorrectedShutterSeconds()!!, 0.000001)
    assertEquals(2.0.pow(1.0 / 1.31), model.getRawShutterSeconds()!!, 0.000001)
    model.setFilmStock(stocks.hp5.copy(name = "Tri-X 400", brand = "Kodak"))
    model.setIso(100)
    model.onOpticalEvMeasured(log2(16.0 / 0.25))
    assertEquals(0.25, model.getCorrectedShutterSeconds()!!, 0.000001)
    assertTrue(model.availableShutterSeconds.contains(model.getCorrectedShutterSeconds()!!))
    assertTrue(model.getRawShutterSeconds()!! < 0.25)
    model.setFilmStock(stocks.hp5.copy(name = "Portra 400", brand = "Kodak"))
    model.setIso(100)
    model.onOpticalEvMeasured(log2(16.0 / 1.1))
    assertEquals(1.1, model.getRawShutterSeconds()!!, 0.000001)
    assertNull(model.getCorrectedShutterSeconds())
    assertNotNull(model.getExposureGuidance())
  }

  @Test fun savedFramesSnapshotTheShootingSetupAndEditingNotesChangesNothingElse() {
    val model = meter()
    camera(model, CameraSetup(name = "My TLR", format = CameraFormat.MEDIUM_66.name,
      focalLengthMm = 80, minAperture = 3.5, maxAperture = 22.0))
    model.setFilmStock(stocks.hp5)
    model.setIso(200)
    model.setCalibrationOffset(0.5f, MeasurementSource.CAMERA)
    model.setExposureCompensation(1.0)
    model.setFilterStops(2.0)
    model.setApertureDirectly("f/8")
    model.onOpticalEvMeasured(7.5)
    val rawSeconds = model.getRawShutterSeconds()!!
    val actualSeconds = model.getCorrectedShutterSeconds()!!
    model.toggleHold()
    model.logExposure("Red door")
    model.setExposureCompensation(-1.0)
    drain()
    val saved = logs.entries.value.single()
    assertEquals("My TLR", saved.cameraName)
    assertEquals(80, saved.focalLengthMm)
    assertEquals(200, saved.iso)
    assertEquals("6×6", saved.format)
    assertEquals("CAMERA", saved.meteringSource)
    assertEquals(1.0, saved.exposureCompensation, 0.0)
    assertEquals(2.0, saved.filterStops, 0.0)
    assertEquals(0.5f, saved.calibrationEv, 0f)
    assertEquals(rawSeconds, saved.rawSeconds, 0.000001)
    assertEquals(actualSeconds, saved.correctedSeconds, 0.000001)
    assertEquals("Manufacturer guidance", saved.correctionKind)
    assertTrue(saved.hasMeterReading)
    assertFalse(saved.isManual)

    model.updateLogNotes(saved.id, "Red door, developed +1")
    drain()
    assertEquals(listOf(saved.copy(notes = "Red door, developed +1")), logs.entries.value)
    assertEquals(1, model.activeRollFrameCount.value)
  }

  @Test fun genericCameraClippingIsNotPresentedAsACorrectAutomaticExposure() {
    val model = meter()
    model.setIso(100)
    model.setApertureDirectly("f/4")
    model.onOpticalEvMeasured(20.0)
    assertNull(model.getCorrectedShutterSeconds())
    assertNotNull(model.getExposureGuidance())
    model.toggleHold()
    model.logExposure()
    drain()
    assertTrue(logs.entries.value.isEmpty())
    model.recordManualExposure(4.0, 1.0 / 8000)
    drain()
    assertTrue(logs.entries.value.single().isManual)
  }

  @Test fun cameraGeometryDoesNotUnfreezeHeldSensorOrSunny16References() {
    val model = meter()
    model.setMeasurementSource(MeasurementSource.SENSOR)
    model.onSensorLuxMeasured(100f)
    model.toggleHold()
    val sensorEv = model.computeEv100()
    model.saveCameraSetup(CameraSetup(name = "Square", format = CameraFormat.MEDIUM_66.name, focalLengthMm = 80))
    drain()
    assertEquals(ReadingStatus.HELD, model.readingStatus.value)
    assertTrue(model.sensorManager.isLocked.value)
    model.onSensorLuxMeasured(200f)
    assertEquals(sensorEv, model.computeEv100(), 0.0)
    model.applySunny16Preset("f/16", "1/125", 100)
    model.toggleHold()
    val sunnyEv = model.computeEv100()
    model.selectCameraSetup(null)
    drain()
    assertEquals(ReadingStatus.HELD, model.readingStatus.value)
    assertTrue(model.sensorManager.isLocked.value)
    assertEquals(sunnyEv, model.computeEv100(), 0.0)
  }

  @Test fun anUnknownFilmCanRecordChosenSettingsWithoutInventingACorrectionOrReading() {
    val model = meter()
    model.setFilmStock(stocks.unknown)
    model.setIso(100)
    model.setApertureDirectly("f/4")
    model.onOpticalEvMeasured(4.0)
    model.toggleHold()
    model.logExposure()
    drain()
    assertTrue(logs.entries.value.isEmpty())
    assertNull(model.getCorrectedShutterSeconds())
    model.recordManualExposure(5.6, 4.0, "My tested exposure")
    drain()
    val measured = logs.entries.value.single()
    assertTrue(measured.isManual)
    assertTrue(measured.hasMeterReading)
    assertEquals("Manual", measured.correctionKind)
    assertEquals(4.0, measured.correctedSeconds, 0.0)
    assertEquals("f/5.6", measured.apertureText)

    model.toggleHold()
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    model.recordManualExposure(8.0, 8.0, "Entered without metering")
    drain()
    val unmeasured = logs.entries.value.last()
    assertTrue(unmeasured.isManual)
    assertFalse(unmeasured.hasMeterReading)
    assertEquals("Manual", unmeasured.correctionKind)
    assertEquals(0.0, unmeasured.rawSeconds, 0.0)
    assertEquals(8.0, unmeasured.correctedSeconds, 0.0)
    assertTrue(model.beginNextManualFrame())
    model.onOpticalEvMeasured(10.0)
    model.recordManualExposure(8.0, 4.0, "No held reference")
    drain()
    assertFalse(logs.entries.value.last().hasMeterReading)
    assertEquals(0.0, logs.entries.value.last().rawSeconds, 0.0)
  }

  @Test fun unavailableReadingsCanRecordConsecutiveManualFramesInEveryExposureMode() {
    val model = meter()
    model.setFilmStock(stocks.unknown)
    model.setCameraStatus("Camera unavailable")
    for (mode in ExposureMode.entries) {
      if (model.savedEntry.value != null) assertTrue(model.beginNextManualFrame())
      model.setExposureMode(mode)
      assertEquals(ReadingStatus.UNAVAILABLE, model.readingStatus.value)
      assertNull(model.getCorrectedShutterSeconds())
      val before = logs.entries.value.size
      model.recordManualExposure(5.6, 4.0)
      assertFalse(model.beginNextManualFrame())
      drain()
      model.recordManualExposure(5.6, 4.0)
      drain()
      assertEquals(before + 1, logs.entries.value.size)
      assertTrue(model.beginNextManualFrame())
      model.recordManualExposure(8.0, 8.0)
      drain()
      assertEquals(before + 2, logs.entries.value.size)
      assertEquals(before + 2, model.activeRollFrameCount.value)
    }
    assertEquals((1..6).toList(), logs.entries.value.map { it.frameNumber })
    assertTrue(logs.entries.value.all { it.isManual && !it.hasMeterReading && it.correctionKind == "Manual" })
    model.finishCurrentRoll()
    drain()
    assertFalse(model.beginNextManualFrame())
  }

  @Test fun loadedRollsKeepTheirPhysicalSetupAndCanContinueAfterFinishingAndRecreation() {
    var model = meter()
    val cameraA = camera(model, CameraSetup(name = "Rangefinder", maxAperture = 16.0))
    model.setFilmStock(stocks.hp5)
    model.setIso(800)
    model.startNewRoll("Street", 24, pushStops = 1)
    drain()
    val rollA = model.currentRollId.value
    model.setCurrentFrame(7)
    drain()
    model.recordManualExposure(8.0, 0.008)
    drain()
    val physicalA = runBlocking { repository.getRoll(rollA) }!!
    assertEquals(8, physicalA.lastFrameNumber)

    val cameraB = camera(model, CameraSetup(name = "Square camera", format = CameraFormat.MEDIUM_66.name,
      focalLengthMm = 80, minAperture = 3.5, maxAperture = 22.0))
    model.setFilmStock(stocks.unknown)
    model.setIso(160)
    drain()
    assertFalse(model.canSaveToCurrentRoll())
    assertEquals(physicalA, runBlocking { repository.getRoll(rollA) })
    model.startNewRoll("Garden", 12)
    model.setIso(160)
    drain()
    val rollB = model.currentRollId.value
    model.setCurrentFrame(2)
    drain()
    model.recordManualExposure(5.6, 0.04)
    drain()

    model.continueRoll(rollA)
    drain()
    assertEquals(cameraA, model.currentCameraSetup.value!!.id)
    assertEquals(stocks.hp5.id, model.selectedFilmStock.value!!.id)
    assertEquals(800, model.activeIso)
    assertEquals(CameraFormat.FORMAT_35MM, model.selectedFormat.value)
    assertEquals(50, model.focalLengthMm.value)
    assertEquals(24, model.currentRoll.value!!.frameCapacity)
    assertEquals(8, model.activeRollFrameCount.value)
    model.setCurrentFrame(7)
    drain()
    assertEquals(8, model.activeRollFrameCount.value)
    model.setCurrentFrame(10)
    drain()
    model.setCurrentRollFrameCapacity(9)
    drain()
    assertEquals(24, model.currentRoll.value!!.frameCapacity)
    model.finishCurrentRoll()
    drain()
    stores.last().clear()
    model = meter()
    assertTrue(model.currentRoll.value!!.isFinished)
    assertFalse(model.canSaveToCurrentRoll())
    assertEquals(10, model.activeRollFrameCount.value)

    model.continueRoll(rollA)
    drain()
    model.recordManualExposure(8.0, 0.008)
    drain()
    assertEquals(11, model.activeRollFrameCount.value)
    model.setCurrentFrame(10)
    drain()
    assertEquals(11, model.activeRollFrameCount.value)
    model.undoLastSavedExposure()
    drain()
    assertEquals(10, model.activeRollFrameCount.value)
    assertEquals(listOf(8), logs.entries.value.filter { it.rollId == rollA }.map { it.frameNumber })

    model.continueRoll(rollB)
    drain()
    assertEquals(cameraB, model.currentCameraSetup.value!!.id)
    assertEquals(stocks.unknown.id, model.selectedFilmStock.value!!.id)
    assertEquals(160, model.activeIso)
    assertEquals(CameraFormat.MEDIUM_66, model.selectedFormat.value)
    assertEquals(80, model.focalLengthMm.value)
    assertEquals(12, model.currentRoll.value!!.frameCapacity)
    assertEquals(3, model.activeRollFrameCount.value)
  }

  @Test fun pendingAndFailedSavesDoNotAdvanceCountersOrAllowDuplicateFrames() {
    val model = meter()
    val rollId = model.currentRollId.value
    model.onOpticalEvMeasured(12.0)
    model.toggleHold()
    logs.gate = CompletableDeferred()
    model.logExposure()
    model.logExposure()
    dispatcher.scheduler.runCurrent()
    assertTrue(model.isSaving.value)
    assertEquals(1, logs.insertAttempts)
    assertEquals(0, model.activeRollFrameCount.value)
    assertEquals(0, runBlocking { repository.getRoll(rollId) }!!.lastFrameNumber)
    model.startNewRoll("Wrong roll")
    model.setCurrentFrame(5)
    model.setFilmStock(stocks.unknown)
    assertEquals(rollId, model.currentRollId.value)
    assertNull(model.selectedFilmStock.value)
    logs.gate!!.completeExceptionally(IllegalStateException("Disk unavailable"))
    drain()
    assertFalse(model.isSaving.value)
    assertEquals(0, model.activeRollFrameCount.value)
    assertEquals(0, runBlocking { repository.getRoll(rollId) }!!.lastFrameNumber)
    assertTrue(logs.entries.value.isEmpty())

    logs.gate = null
    model.logExposure()
    model.logExposure()
    drain()
    assertEquals(2, logs.insertAttempts)
    assertEquals(1, logs.entries.value.size)
    assertEquals(1, model.activeRollFrameCount.value)
    assertEquals(1, runBlocking { repository.getRoll(rollId) }!!.lastFrameNumber)
  }

  @Test fun firstVersionTwoLaunchAdoptsTheActiveLegacySetupAndPhysicalFrameCounter() {
    val legacy = FilmLogEntry(44, "legacy-active", "Earlier name", 1, stocks.hp5.name,
      400, "35mm", "f/8", "1/125", 0.008, 0.008, 13.0, 20_480f, timestamp = 1000L)
    runBlocking {
      repository.insertLogEntry(legacy)
      repository.saveRoll(FilmRoll(id = legacy.rollId, name = legacy.rollName,
        filmStockId = stocks.hp5.id, filmStockName = stocks.hp5.name, iso = 400,
        format = CameraFormat.FORMAT_35MM.name, focalLengthMm = 50,
        frameCapacity = 36, lastFrameNumber = 1, isFinished = true, createdAt = 1000L))
    }
    application.getSharedPreferences("meter_setup", Context.MODE_PRIVATE).edit()
      .putString("roll_id", legacy.rollId).putString("roll_name", "Current roll name")
      .putInt("stock_id", stocks.hp5.id).putInt("iso", 800)
      .putString("format", CameraFormat.MEDIUM_66.name).putInt("focal_length", 100)
      .putInt("roll_frame_count", 9).putBoolean("rolls_v2", false).commit()

    val model = meter()
    assertEquals("Current roll name", model.currentRollName.value)
    assertEquals(800, model.activeIso)
    assertEquals(CameraFormat.MEDIUM_66, model.selectedFormat.value)
    assertEquals(100, model.focalLengthMm.value)
    assertEquals(9, model.activeRollFrameCount.value)
    assertEquals(12, model.currentRoll.value!!.frameCapacity)
    assertFalse(model.currentRoll.value!!.isFinished)
    assertEquals(listOf(legacy), logs.entries.value)
    assertEquals(9, runBlocking { repository.getRoll(legacy.rollId) }!!.lastFrameNumber)
  }

  @Test fun pushUsesWorkingEiOnceAndKeepsCompensationAndReciprocitySeparate() {
    val model = meter()
    assertTrue(model.applyFilmSettings(stocks.hp5, 400, 0))
    model.setApertureDirectly("f/4")
    model.onOpticalEvMeasured(7.0)
    val normal = model.getBaseMeteredSeconds()!!
    assertTrue(model.applyFilmSettings(stocks.hp5, 1600, 2))
    assertEquals(400, model.selectedFilmStock.value!!.iso)
    assertEquals(1600, model.activeIso)
    assertEquals(normal / 4, model.getBaseMeteredSeconds()!!, 0.000001)
    assertEquals(0.0, model.exposureCompensation.value, 0.0)
    model.setExposureCompensation(1.0)
    model.setFilterStops(1.0)
    assertEquals(normal, model.getFilteredMeteredSeconds()!!, 0.000001)
    model.onOpticalEvMeasured(1.0)
    val raw = model.getFilteredMeteredSeconds()!!
    assertEquals(raw.pow(1.31), model.getCorrectedShutterSeconds()!!, 0.000001)
    model.setMeasurementSource(MeasurementSource.SENSOR)
    model.onSensorLuxMeasured(320f)
    assertEquals(7.0, model.computeEv100(), 0.000001)
    assertEquals(normal, model.getFilteredMeteredSeconds()!!, 0.000001)
  }

  @Test fun populatedRollRequiresExplicitPlanEditAndKeepsFrameSnapshots() {
    val model = meter()
    assertTrue(model.applyFilmSettings(stocks.hp5, 1600, 2))
    model.recordManualExposure(8.0, 0.008)
    drain()
    val first = logs.entries.value.single()
    assertEquals(400, first.boxIso)
    assertEquals(1600, first.iso)
    model.setFocalLength(85)
    drain()
    assertEquals(85, model.currentRoll.value!!.focalLengthMm)
    val original = model.currentRoll.value!!
    model.setCustomIso(800)
    drain()
    assertEquals(original, model.currentRoll.value)
    assertEquals(2, model.pushStops.value)
    assertTrue(model.needsFilmSettingsConfirmation(stocks.hp5, 800, 1))
    assertFalse(model.applyFilmSettings(stocks.hp5, 800, 1))
    assertEquals(original, model.currentRoll.value)
    assertTrue(model.applyFilmSettings(stocks.hp5, 800, 1, confirmRollChange = true))
    drain()
    assertEquals(800, model.currentRoll.value!!.iso)
    assertEquals(1, model.currentRoll.value!!.pushStops)
    assertEquals(listOf(first), logs.entries.value)
    assertEquals(400, model.currentRoll.value!!.boxIso)
    model.setFilmStock(stocks.unknown)
    drain()
    assertFalse(model.canSaveToCurrentRoll())
    assertEquals(1, model.currentRoll.value!!.pushStops)
    assertEquals(800, model.currentRoll.value!!.iso)
  }

  @Test fun pushPlanSurvivesSunny16FinishRestoreAndContinueAndNewRollDefaultsToNormal() {
    var model = meter()
    assertTrue(model.applyFilmSettings(stocks.hp5, 1600, 2))
    val rollId = model.currentRollId.value
    model.applySunny16Preset("f/16", "1/2000", 1600)
    assertEquals(2, model.pushStops.value)
    assertEquals(1600, model.activeIso)
    model.recordManualExposure(8.0, 0.008)
    drain()
    model.finishCurrentRoll()
    drain()
    stores.last().clear()
    model = meter()
    assertEquals(2, model.pushStops.value)
    assertEquals(1600, model.activeIso)
    assertTrue(model.currentRoll.value!!.isFinished)
    model.startNewRoll("Normal next roll")
    drain()
    assertEquals(0, model.pushStops.value)
    assertEquals(400, model.activeIso)
    model.continueRoll(rollId)
    drain()
    assertEquals(2, model.pushStops.value)
    assertEquals(1600, model.activeIso)
    assertEquals(400, model.currentRoll.value!!.boxIso)
    assertEquals(1, model.activeRollFrameCount.value)
  }

  @Test fun legacyWorkingIsoDoesNotInventProcessingAndPushBoundsAreValidated() {
    application.getSharedPreferences("meter_setup", Context.MODE_PRIVATE).edit()
      .putInt("stock_id", stocks.hp5.id).putInt("iso", 1600).commit()
    val model = meter()
    assertEquals(1600, model.activeIso)
    assertNull(model.pushStops.value)
    assertNull(model.currentRoll.value!!.pushStops)
    for ((stops, iso) in listOf(0 to 400, 1 to 800, 2 to 1600, 4 to 6400)) {
      assertEquals(iso, stocks.hp5.isoForPush(stops))
    }
    assertNull(stocks.hp5.isoForPush(-1))
    assertNull(stocks.hp5.isoForPush(5))
    assertNull(stocks.hp5.copy(iso = 0).isoForPush(2))
    assertNull(stocks.hp5.copy(iso = Int.MAX_VALUE).isoForPush(4))
    assertEquals(102400, stocks.hp5.copy(iso = 6400).isoForPush(4))
    assertNull(stocks.hp5.copy(iso = 12800).isoForPush(4))
    assertFalse(model.applyFilmSettings(null, 1600, 2))
    assertFalse(model.applyFilmSettings(stocks.hp5, 1600, 5))
    model.startNewRoll("Invalid", pushStops = 5)
    assertEquals(1600, model.activeIso)
    assertNull(model.pushStops.value)
  }

  @Test fun manualShotsTargetTheirRollWithoutChangingTheHeldMeterAndCanRepeatAndUndo() {
    val model = meter()
    model.setFilmStock(stocks.hp5)
    model.setExposureCompensation(1.0)
    model.onOpticalEvMeasured(12.0)
    model.toggleHold()
    model.logExposure()
    drain()
    val heldReceipt = model.savedEntry.value!!
    assertTrue(model.userMessage.value!!.contains("saved without a photo"))
    val activeRoll = model.currentRoll.value
    val targetCamera = CameraSetup(name = "Other camera", format = CameraFormat.MEDIUM_66.name, focalLengthMm = 80)
    val cameraId = runBlocking { repository.saveCameraSetup(targetCamera).toInt() }
    val target = FilmRoll(id = "manual-target", name = "Manual roll", filmStockName = "Other film",
      iso = 800, boxIso = 400, pushStops = 1, format = CameraFormat.MEDIUM_66.name,
      cameraSetupId = cameraId, focalLengthMm = 80, lastFrameNumber = 3, frameCapacity = 12)
    runBlocking { repository.saveRoll(target) }

    model.recordManualExposureForRoll(target.id, 8.0, 1.0 / 125, "Red door")
    drain()
    val first = model.manualSavedEntry.value!!
    assertEquals(4, first.frameNumber)
    assertEquals(800, first.iso)
    assertEquals("Other film", first.filmStockName)
    assertEquals("Other camera", first.cameraName)
    assertEquals("6×6", first.format)
    assertTrue(first.isManual)
    assertFalse(first.hasMeterReading)
    assertEquals("", first.meteringSource)
    assertEquals(0.0, first.rawSeconds, 0.0)
    assertEquals(0.0, first.exposureCompensation, 0.0)
    assertEquals(0f, first.calibrationEv, 0f)
    assertEquals(heldReceipt, model.savedEntry.value)
    assertEquals(activeRoll, model.currentRoll.value)
    assertEquals(ReadingStatus.HELD, model.readingStatus.value)
    assertEquals(1.0, model.exposureCompensation.value, 0.0)
    model.recordManualExposureForRoll(target.id, 8.0, 1.0 / 125)
    drain()
    assertEquals(1, logs.entries.value.count { it.rollId == target.id })

    model.clearManualSavedReceipt()
    model.recordManualExposureForRoll(target.id, 5.6, 0.5)
    drain()
    assertEquals(5, model.manualSavedEntry.value!!.frameNumber)
    model.undoManualSavedExposure()
    drain()
    assertNull(model.manualSavedEntry.value)
    assertEquals(listOf(first), logs.entries.value.filter { it.rollId == target.id })
    assertEquals(4, runBlocking { repository.getRoll(target.id) }!!.lastFrameNumber)
    assertEquals(activeRoll, model.currentRoll.value)
    assertEquals(heldReceipt, model.savedEntry.value)
  }

  @Test fun manualRollSaveRejectsDuplicatesAndKeepsCounterOnFailureOrClosedRoll() {
    val model = meter()
    val target = FilmRoll(id = "manual-failure", name = "Manual", frameCapacity = 1)
    runBlocking { repository.saveRoll(target) }
    logs.gate = CompletableDeferred()
    model.recordManualExposureForRoll(target.id, 8.0, 0.008, "Keep draft")
    model.recordManualExposureForRoll(target.id, 8.0, 0.008, "Duplicate")
    dispatcher.scheduler.runCurrent()
    assertEquals(1, logs.insertAttempts)
    assertEquals(0, runBlocking { repository.getRoll(target.id) }!!.lastFrameNumber)
    logs.gate!!.completeExceptionally(IllegalStateException("Disk unavailable"))
    drain()
    assertNull(model.manualSavedEntry.value)
    assertTrue(logs.entries.value.isEmpty())
    assertEquals(target, runBlocking { repository.getRoll(target.id) })
    logs.gate = null
    model.recordManualExposureForRoll(target.id, 8.0, 0.008, "Keep draft")
    drain()
    assertEquals("Keep draft", model.manualSavedEntry.value!!.notes)
    model.clearManualSavedReceipt()
    model.recordManualExposureForRoll(target.id, 8.0, 0.008)
    drain()
    assertNull(model.manualSavedEntry.value)
    assertEquals(1, logs.entries.value.size)
    runBlocking { repository.saveRoll(target.copy(isFinished = true)) }
    model.recordManualExposureForRoll(target.id, 8.0, 0.008)
    drain()
    assertNull(model.manualSavedEntry.value)
    assertTrue(model.userMessage.value!!.contains("Reopen"))
  }

  @Test fun creatingRollFromDraftPreservesMeterAndStoresItsOwnFilmCameraAndProcessing() {
    val model = meter()
    model.setFilmStock(stocks.unknown)
    model.onOpticalEvMeasured(10.0)
    model.toggleHold()
    val current = model.currentRoll.value
    val camera = CameraSetup(name = "Square", format = CameraFormat.MEDIUM_66.name, focalLengthMm = 80)
    val savedCamera = camera.copy(id = runBlocking { repository.saveCameraSetup(camera).toInt() })
    model.createRoll("  Weekend  ", 12, stocks.hp5, savedCamera, CameraFormat.MEDIUM_66, 80, 1600, 2)
    drain()
    val created = runBlocking { repository.getRoll(model.createdRollId.value!!) }!!
    assertEquals("Weekend", created.name)
    assertEquals(stocks.hp5.id, created.filmStockId)
    assertEquals("Black & White", created.filmType)
    assertEquals(1600, created.iso)
    assertEquals(2, created.pushStops)
    assertEquals(savedCamera.id, created.cameraSetupId)
    assertEquals(12, created.frameCapacity)
    assertEquals(current, model.currentRoll.value)
    assertEquals(stocks.unknown, model.selectedFilmStock.value)
    assertEquals(ReadingStatus.HELD, model.readingStatus.value)
    model.clearCreatedRollReceipt()
    assertNull(model.createdRollId.value)
    model.createRoll("Invalid push", 12, stocks.hp5, null, CameraFormat.MEDIUM_66, 80, 800, 5)
    drain()
    assertNull(model.createdRollId.value)
    model.createRoll("Custom EI", 12, stocks.hp5, null, CameraFormat.MEDIUM_66, 80, 800, 2)
    drain()
    val custom = runBlocking { repository.getRoll(model.createdRollId.value!!) }!!
    assertEquals(800, custom.iso)
    assertEquals(2, custom.pushStops)
    assertEquals(current, model.currentRoll.value)
  }

  @Test
  @Config(sdk = [28])
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun photoFailureStillSavesShotAndPhotoEditsAndUndoCleanOwnedFiles() {
    val model = meter()
    val target = FilmRoll(id = "photo-target", name = "Photo roll")
    runBlocking { repository.saveRoll(target) }
    model.recordManualExposureForRoll(target.id, 8.0, 0.008, photoUri = Uri.parse("file:///missing-shot-photo.jpg"))
    finishSave(model)
    val first = model.manualSavedEntry.value!!
    assertNull(first.photoFileName)
    assertTrue(model.userMessage.value!!.contains("saved without a photo"))
    val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
    val source = File(application.cacheDir, "photo-source.png")
    source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    model.updateLogPhoto(first.id, Uri.fromFile(source))
    finishSave(model)
    val withPhoto = logs.entries.value.single()
    val photoFile = model.photoFile(withPhoto.photoFileName)!!
    assertTrue(photoFile.exists())
    model.updateLogPhoto(first.id, Uri.parse("file:///missing-replacement.jpg"))
    finishSave(model)
    assertEquals(withPhoto.photoFileName, logs.entries.value.single().photoFileName)
    assertTrue(photoFile.exists())
    model.undoManualSavedExposure()
    finishSave(model)
    assertTrue(logs.entries.value.isEmpty())
    assertFalse(photoFile.exists())
    assertEquals(0, runBlocking { repository.getRoll(target.id) }!!.lastFrameNumber)
    bitmap.recycle()
    source.delete()
  }

  @Test fun rollKeepsBlackAndWhiteTypeAfterItsCustomFilmIsDeleted() {
    val model = meter()
    val custom = stocks.unknown.copy(id = 0, name = "Custom monochrome", type = "Black & White")
    val stored = custom.copy(id = runBlocking { repository.insertStock(custom).toInt() })
    model.createRoll("Monochrome", 36, stored, null, CameraFormat.FORMAT_35MM, 50, 100, 0)
    drain()
    val id = model.createdRollId.value!!
    model.deleteFilmStock(stored.id)
    drain()
    model.continueRoll(id)
    drain()
    assertNull(model.selectedFilmStock.value)
    assertEquals("Black & White", model.currentRoll.value!!.filmType)
    assertEquals("Black & White", runBlocking { repository.getRoll(id) }!!.filmType)
  }

  @Test
  @Config(sdk = [28])
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun failedShotInsertRemovesNewPhotoAndDeletingRecordKeepsPhysicalCounter() {
    val model = meter()
    val target = FilmRoll(id = "photo-failure", name = "Photo failure")
    runBlocking { repository.saveRoll(target) }
    val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
    val source = File(application.cacheDir, "failed-photo-source.png")
    source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val directory = File(application.filesDir, "shot-photos")
    val before = directory.listFiles().orEmpty().map { it.name }.toSet()
    logs.gate = CompletableDeferred<Unit>().also { it.completeExceptionally(IllegalStateException("Disk unavailable")) }
    model.recordManualExposureForRoll(target.id, 8.0, 0.008, photoUri = Uri.fromFile(source))
    finishSave(model)
    assertTrue(logs.entries.value.isEmpty())
    assertEquals(before, directory.listFiles().orEmpty().map { it.name }.toSet())
    logs.gate = null
    model.recordManualExposureForRoll(target.id, 8.0, 0.008, photoUri = Uri.fromFile(source))
    finishSave(model)
    val saved = model.manualSavedEntry.value!!
    val owned = model.photoFile(saved.photoFileName)!!
    model.deleteLogEntry(saved.id)
    finishSave(model)
    assertFalse(owned.exists())
    assertEquals(1, runBlocking { repository.getRoll(target.id) }!!.lastFrameNumber)
    bitmap.recycle()
    source.delete()
  }

  private class Stocks : FilmStockDao {
    val hp5 = FilmStock(1, "HP5 Plus 400", "Ilford", 400, "Black & White", 1.31,
      description = "Test", badgeStyle = "HP5")
    val unknown = FilmStock(2, "My unlisted film", "My lab", 100, "Color Negative", 1.0,
      description = "Unknown correction", badgeStyle = "CUSTOM", isCustom = true, hasReciprocityProfile = false)
    private val entries = MutableStateFlow(listOf(hp5, unknown))
    override fun getAllFilmStocks() = entries
    override suspend fun getFilmStockById(id: Int) = entries.value.find { it.id == id }
    override suspend fun insertFilmStock(stock: FilmStock): Long {
      val id = stock.id.takeIf { it > 0 } ?: ((entries.value.maxOfOrNull { it.id } ?: 0) + 1)
      entries.value = entries.value.filterNot { it.id == id } + stock.copy(id = id)
      return id.toLong()
    }
    override suspend fun insertAllStocks(stocks: List<FilmStock>) { stocks.forEach { insertFilmStock(it) } }
    override suspend fun deleteFilmStockById(id: Int) { entries.value = entries.value.filterNot { it.id == id } }
  }

  private class Logs : FilmLogDao {
    val entries = MutableStateFlow<List<FilmLogEntry>>(emptyList())
    var gate: CompletableDeferred<Unit>? = null
    var insertAttempts = 0
    private var nextId = 1
    override fun getAllLogEntries() = entries
    override fun getEntriesForRoll(rollId: String) = entries.map { list -> list.filter { it.rollId == rollId } }
    override suspend fun getLatestEntryForRoll(rollId: String) = entries.value.filter { it.rollId == rollId }.maxByOrNull { it.frameNumber }
    override suspend fun getLogEntry(id: Int) = entries.value.firstOrNull { it.id == id }
    override suspend fun updateLogPhoto(id: Int, name: String?) {
      entries.value = entries.value.map { if (it.id == id) it.copy(photoFileName = name) else it }
    }
    override suspend fun insertLogEntry(entry: FilmLogEntry): Long {
      insertAttempts++
      gate?.await()
      val id = entry.id.takeIf { it > 0 } ?: nextId++
      entries.value = entries.value.filterNot { it.id == id } + entry.copy(id = id)
      return id.toLong()
    }
    override suspend fun deleteEntryById(id: Int) { entries.value = entries.value.filterNot { it.id == id } }
    override suspend fun updateLogNotes(id: Int, notes: String) {
      entries.value = entries.value.map { if (it.id == id) it.copy(notes = notes) else it }
    }
    override suspend fun deleteRollEntries(rollId: String) { entries.value = entries.value.filterNot { it.rollId == rollId } }
  }
}
