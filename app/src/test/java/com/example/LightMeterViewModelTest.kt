package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.FilmLogDao
import com.example.data.dao.FilmStockDao
import com.example.data.model.CameraFormat
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmStock
import com.example.data.repository.FilmRepository
import com.example.sensor.ExposureCalculator
import com.example.ui.meter.ExposureMode
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.meter.MeasurementSource
import com.example.ui.meter.ReadingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LightMeterViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var application: Application
  private val stores = mutableListOf<ViewModelStore>()
  private val logs = TestLogDao()
  private val stocks = TestStockDao()

  @Before fun setUp() {
    Dispatchers.setMain(dispatcher)
    application = ApplicationProvider.getApplicationContext()
    application.getSharedPreferences("meter_setup", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @After fun tearDown() {
    stores.forEach { it.clear() }
    Dispatchers.resetMain()
  }

  private fun meter(): LightMeterViewModel {
    val model = LightMeterViewModel(application, FilmRepository(stocks, logs))
    stores += ViewModelStore().also { it.put("meter", model) }
    dispatcher.scheduler.advanceUntilIdle()
    return model
  }

  @Test fun `camera is isolated calibrated once and held until resumed`() {
    val model = meter()
    assertEquals(MeasurementSource.CAMERA, model.measurementSource.value)
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    model.toggleHold()
    model.logExposure()
    assertFalse(model.sensorManager.isLocked.value)
    assertTrue(logs.entries.value.isEmpty())

    model.setIso(100)
    model.setCalibrationOffset(1f)
    model.setApertureDirectly("f/16")
    model.onSensorLuxMeasured(81920f)
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    model.onOpticalEvMeasured(14.0)
    model.setCameraStatus(null)
    assertEquals(15.0, model.computeEv100(model.sensorManager.lux.value), 0.001)
    assertEquals("1/125", ExposureCalculator.SHUTTER_LABELS[model.selectedShutterIndex.value])

    model.toggleHold()
    model.onOpticalEvMeasured(5.0)
    model.onSensorLuxMeasured(10f)
    model.setFormat(CameraFormat.MEDIUM_66)
    model.setFocalLength(85)
    model.setCameraStatus("Camera unavailable")
    assertEquals(ReadingStatus.HELD, model.readingStatus.value)
    assertEquals(15.0, model.computeEv100(model.sensorManager.lux.value), 0.001)
    assertEquals(CameraFormat.FORMAT_35MM, model.selectedFormat.value)
    assertEquals(50, model.focalLengthMm.value)

    model.pauseMetering()
    model.resumeMetering()
    assertEquals(ReadingStatus.HELD, model.readingStatus.value)
    model.toggleHold()
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    model.onOpticalEvMeasured(13.0)
    assertEquals("1/60", ExposureCalculator.SHUTTER_LABELS[model.selectedShutterIndex.value])
  }

  @Test fun `source crop and lifecycle changes require a fresh reading`() {
    val model = meter()
    model.onOpticalEvMeasured(Double.NaN)
    model.setCameraStatus(null)
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    model.setCameraStatus("Measuring frame", measuring = true)
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    model.onOpticalEvMeasured(12.0)
    model.setFocalLength(85)
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    model.onOpticalEvMeasured(16.0, CameraFormat.FORMAT_35MM, 50)
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    assertFalse(model.hasReading.value)
    model.onOpticalEvMeasured(12.0)
    model.pauseMetering()
    model.onOpticalEvMeasured(16.0)
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    model.resumeMetering()
    model.onOpticalEvMeasured(12.0)
    model.setMeasurementSource(MeasurementSource.SENSOR)
    model.onOpticalEvMeasured(16.0)
    assertNotEquals(ReadingStatus.LIVE, model.readingStatus.value)
    model.onSensorLuxMeasured(81920f)
    assertEquals(ReadingStatus.LIVE, model.readingStatus.value)
    assertEquals(15.0, model.computeEv100(model.sensorManager.lux.value), 0.001)
    model.setMeasurementSource(MeasurementSource.CAMERA)
    model.setCameraStatus("Camera permission required")
    assertEquals(ReadingStatus.UNAVAILABLE, model.readingStatus.value)
    model.toggleHold()
    model.logExposure()
    assertFalse(model.isSaving.value)
  }

  @Test fun `camera adjustment retains the last reading but cannot hold until settled`() {
    val model = meter()
    model.onOpticalEvMeasured(12.0)
    val shutter = model.selectedShutterIndex.value
    model.setCameraStatus("Measuring frame", measuring = true)
    assertTrue(model.hasReading.value)
    assertEquals(ReadingStatus.WAITING, model.readingStatus.value)
    assertEquals(shutter, model.selectedShutterIndex.value)
    assertEquals(12.0, model.computeEv100(model.sensorManager.lux.value), 0.001)
    model.toggleHold()
    assertFalse(model.sensorManager.isLocked.value)
    model.onOpticalEvMeasured(13.0)
    assertEquals(ReadingStatus.LIVE, model.readingStatus.value)
    model.toggleHold()
    assertEquals(ReadingStatus.HELD, model.readingStatus.value)
    model.toggleHold()
    assertFalse(model.hasReading.value)
    model.onOpticalEvMeasured(12.0)
    model.setCameraStatus("Camera unavailable")
    assertFalse(model.hasReading.value)
  }

  @Test fun `setup roll and frame count survive a new view model`() {
    val model = meter()
    val film = stocks.film
    model.setFilmStock(film)
    model.setCustomIso(800)
    model.setFormat(CameraFormat.MEDIUM_66)
    model.setFocalLength(100)
    model.setCalibrationOffset(0.5f)
    model.setExposureMode(ExposureMode.MANUAL_MATCH)
    model.setApertureDirectly("f/8")
    model.setShutterDirectly("4s")
    model.setHapticsEnabled(false)
    model.startNewRoll("Autumn")
    model.onOpticalEvMeasured(8.0)
    model.toggleHold()
    model.logExposure()
    dispatcher.scheduler.advanceUntilIdle()

    val restored = meter()
    assertTrue(restored.setupReady.value)
    assertEquals(film, restored.selectedFilmStock.value)
    assertEquals(800, restored.activeIso)
    assertEquals(CameraFormat.MEDIUM_66, restored.selectedFormat.value)
    assertEquals(100, restored.focalLengthMm.value)
    assertEquals(0.5f, restored.calibrationOffsetEv.value)
    assertEquals(ExposureMode.MANUAL_MATCH, restored.exposureMode.value)
    assertEquals(model.selectedApertureIndex.value, restored.selectedApertureIndex.value)
    assertEquals(model.currentRollId.value, restored.currentRollId.value)
    assertEquals("Autumn", restored.currentRollName.value)
    assertEquals(1, restored.activeRollFrameCount.value)
    assertTrue(restored.getCorrectedShutterSeconds() > restored.getRawShutterSeconds())
    assertFalse(restored.hapticManager.isHapticsEnabled)
    assertEquals(ReadingStatus.WAITING, restored.readingStatus.value)
  }

  @Test fun `one held reading saves once and undo removes only that entry`() {
    val model = meter()
    model.onOpticalEvMeasured(12.0)
    model.toggleHold()
    model.logExposure()
    dispatcher.scheduler.advanceUntilIdle()
    val firstId = model.savedEntry.value!!.id
    model.toggleHold()
    model.onOpticalEvMeasured(13.0)
    model.toggleHold()
    logs.insertGate = CompletableDeferred()
    model.logExposure()
    model.logExposure()
    dispatcher.scheduler.runCurrent()
    assertTrue(model.isSaving.value)
    assertEquals(1, model.activeRollFrameCount.value)
    assertNull(model.savedEntry.value)
    logs.insertGate!!.complete(Unit)
    dispatcher.scheduler.advanceUntilIdle()
    model.logExposure()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(2, logs.entries.value.size)
    assertEquals(2, model.savedEntry.value?.frameNumber)

    model.undoLastSavedExposure()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(listOf(firstId), logs.entries.value.map { it.id })
    assertEquals(1, model.activeRollFrameCount.value)
    assertNull(model.savedEntry.value)
    assertEquals(ReadingStatus.HELD, model.readingStatus.value)
  }

  @Test fun `save failure keeps held reading available for retry`() {
    val model = meter()
    model.onOpticalEvMeasured(12.0)
    model.toggleHold()
    logs.failInsert = true
    model.logExposure()
    dispatcher.scheduler.advanceUntilIdle()
    assertFalse(model.isSaving.value)
    assertNull(model.savedEntry.value)
    assertEquals(0, model.activeRollFrameCount.value)
    assertEquals(ReadingStatus.HELD, model.readingStatus.value)
    assertNotNull(model.userMessage.value)
    logs.failInsert = false
    model.logExposure()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(1, logs.entries.value.size)
  }

  @Test fun `Sunny 16 keeps the film and does not apply hardware calibration`() {
    val model = meter()
    model.setFilmStock(stocks.film)
    model.setCalibrationOffset(1f)
    model.pauseMetering()
    model.applySunny16Preset("f/16", "1/125", 100)
    assertEquals(MeasurementSource.SUNNY_16, model.measurementSource.value)
    assertEquals(ReadingStatus.LIVE, model.readingStatus.value)
    assertEquals(stocks.film, model.selectedFilmStock.value)
    assertEquals(100, model.activeIso)
    assertEquals("1/125", ExposureCalculator.SHUTTER_LABELS[model.selectedShutterIndex.value])
    model.onOpticalEvMeasured(2.0)
    assertEquals("1/125", ExposureCalculator.SHUTTER_LABELS[model.selectedShutterIndex.value])
  }

  private class TestLogDao : FilmLogDao {
    val entries = MutableStateFlow<List<FilmLogEntry>>(emptyList())
    var insertGate: CompletableDeferred<Unit>? = null
    var failInsert = false
    override fun getAllLogEntries() = entries
    override fun getEntriesForRoll(rollId: String) = entries.map { list -> list.filter { it.rollId == rollId } }
    override suspend fun getLatestEntryForRoll(rollId: String) = entries.value
      .filter { it.rollId == rollId }.maxByOrNull { it.frameNumber }
    override suspend fun insertLogEntry(entry: FilmLogEntry): Long {
      insertGate?.await()
      if (failInsert) error("Storage unavailable")
      val id = (entries.value.maxOfOrNull { it.id } ?: 0) + 1
      entries.value += entry.copy(id = id)
      return id.toLong()
    }
    override suspend fun deleteEntryById(id: Int) { entries.value = entries.value.filterNot { it.id == id } }
    override suspend fun deleteRollEntries(rollId: String) { entries.value = entries.value.filterNot { it.rollId == rollId } }
  }

  private class TestStockDao : FilmStockDao {
    val film = FilmStock(7, "HP5 Plus", "Ilford", 400, "Black & White", 1.31,
      description = "Test film", badgeStyle = "HP5")
    override fun getAllFilmStocks() = MutableStateFlow(listOf(film))
    override suspend fun getFilmStockById(id: Int) = film.takeIf { it.id == id }
    override suspend fun insertFilmStock(stock: FilmStock) = 7L
    override suspend fun insertAllStocks(stocks: List<FilmStock>) = Unit
    override suspend fun deleteFilmStockById(id: Int) = Unit
  }
}
