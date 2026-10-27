package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.FilmLogDao
import com.example.data.dao.FilmStockDao
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmStock
import com.example.data.repository.FilmRepository
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.meter.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersistenceRestoreTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var application: Application
  private val stores = mutableListOf<ViewModelStore>()
  private val logs = MemoryLogDao()
  private val stocks = EmptyStockDao()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    application = ApplicationProvider.getApplicationContext()
    preferences("meter_setup").edit().clear().commit()
    preferences("meter_device_calibration").edit().clear().commit()
  }

  @After
  fun tearDown() {
    stores.forEach { it.clear() }
    dispatcher.scheduler.runCurrent()
    Dispatchers.resetMain()
  }

  private fun preferences(name: String) = application.getSharedPreferences(name, Context.MODE_PRIVATE)

  private fun meter(): LightMeterViewModel {
    val model = LightMeterViewModel(application, FilmRepository(stocks, logs))
    stores += ViewModelStore().also { it.put("meter", model) }
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(model.setupReady.value)
    return model
  }

  private fun saveFrame(model: LightMeterViewModel): FilmLogEntry {
    if (model.readingStatus.value == ReadingStatus.HELD) model.toggleHold()
    model.onOpticalEvMeasured(12.0)
    model.toggleHold()
    model.logExposure()
    dispatcher.scheduler.advanceUntilIdle()
    return model.savedEntry.value!!
  }

  @Test
  fun `deleting the last saved record preserves physical frame number after restore`() {
    val model = meter()
    repeat(3) { saveFrame(model) }
    model.deleteLogEntry(model.savedEntry.value!!.id)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf(1, 2), logs.entries.value.map { it.frameNumber })
    assertEquals(3, model.activeRollFrameCount.value)
    val restored = meter()
    assertEquals(3, restored.activeRollFrameCount.value)
    assertEquals(4, saveFrame(restored).frameNumber)
    assertEquals(listOf(1, 2, 4), logs.entries.value.map { it.frameNumber })
  }

  @Test
  fun `undo explicitly rewinds the persisted frame counter`() {
    val model = meter()
    repeat(3) { saveFrame(model) }
    model.undoLastSavedExposure()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(2, model.activeRollFrameCount.value)
    val restored = meter()
    assertEquals(2, restored.activeRollFrameCount.value)
    assertEquals(3, saveFrame(restored).frameNumber)
  }

  @Test
  fun `committed frames recover a counter that was not persisted before interruption`() {
    val model = meter()
    repeat(3) { saveFrame(model) }
    preferences("meter_setup").edit().putInt("roll_frame_count", 1).commit()

    val restored = meter()
    assertEquals(3, restored.activeRollFrameCount.value)
    assertEquals(4, saveFrame(restored).frameNumber)
  }

  @Test
  fun `new rolls reset the persisted frame counter without removing old frames`() {
    val model = meter()
    repeat(3) { saveFrame(model) }
    val oldRollId = model.currentRollId.value
    model.startNewRoll("Next roll")

    val restored = meter()
    assertEquals(0, restored.activeRollFrameCount.value)
    assertEquals(1, saveFrame(restored).frameNumber)
    assertEquals(3, logs.entries.value.count { it.rollId == oldRollId })
  }

  @Test
  fun `restored legacy setup calibration is ignored and removed`() {
    preferences("meter_setup").edit().putFloat("calibration", 2f).commit()
    val model = meter()

    assertEquals(0f, model.calibrationOffsetEv.value, 0f)
    assertFalse(preferences("meter_setup").contains("calibration"))
    model.onOpticalEvMeasured(12.0)
    assertEquals(12.0, model.computeEv100(), 0.0001)
  }

  @Test
  fun `calibration survives local recreation only in the excluded device preferences`() {
    val model = meter()
    model.setCalibrationOffset(0.5f)

    assertEquals(0.5f, preferences("meter_device_calibration").getFloat("calibration", 0f), 0f)
    assertFalse(preferences("meter_setup").contains("calibration"))
    assertEquals(0.5f, meter().calibrationOffsetEv.value, 0f)
  }

  @Test
  fun `backup excludes calibration and cloud photos without restricting rolls or device photo transfer`() {
    assertEquals(
      setOf("full-backup-content:sharedpref:meter_device_calibration.xml", "full-backup-content:file:shot-photos/"),
      backupExclusions(R.xml.backup_rules)
    )
    assertEquals(
      setOf("cloud-backup:sharedpref:meter_device_calibration.xml", "device-transfer:sharedpref:meter_device_calibration.xml",
        "cloud-backup:file:shot-photos/"),
      backupExclusions(R.xml.data_extraction_rules)
    )
  }

  private fun backupExclusions(resource: Int): Set<String> {
    val exclusions = mutableSetOf<String>()
    var section = ""
    application.resources.getXml(resource).use { parser ->
      while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType != XmlPullParser.START_TAG) continue
        assertFalse("Include rules would disable default backup of other data", parser.name == "include")
        when (parser.name) {
          "full-backup-content", "cloud-backup", "device-transfer" -> section = parser.name
          "exclude" -> exclusions += "$section:${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
        }
      }
    }
    return exclusions
  }

  private class MemoryLogDao : FilmLogDao {
    val entries = MutableStateFlow<List<FilmLogEntry>>(emptyList())
    private var nextId = 1
    override fun getAllLogEntries() = entries
    override fun getEntriesForRoll(rollId: String) = entries.map { list -> list.filter { it.rollId == rollId } }
    override suspend fun getLatestEntryForRoll(rollId: String) = entries.value.filter { it.rollId == rollId }.maxByOrNull { it.frameNumber }
    override suspend fun getLogEntry(id: Int) = entries.value.firstOrNull { it.id == id }
    override suspend fun updateLogPhoto(id: Int, name: String?) {
      entries.value = entries.value.map { if (it.id == id) it.copy(photoFileName = name) else it }
    }
    override suspend fun insertLogEntry(entry: FilmLogEntry): Long {
      val id = nextId++
      entries.value += entry.copy(id = id)
      return id.toLong()
    }
    override suspend fun deleteEntryById(id: Int) { entries.value = entries.value.filterNot { it.id == id } }
    override suspend fun updateLogNotes(id: Int, notes: String) {
      entries.value = entries.value.map { if (it.id == id) it.copy(notes = notes) else it }
    }
    override suspend fun deleteRollEntries(rollId: String) { entries.value = entries.value.filterNot { it.rollId == rollId } }
  }

  private class EmptyStockDao : FilmStockDao {
    override fun getAllFilmStocks() = MutableStateFlow<List<FilmStock>>(emptyList())
    override suspend fun getFilmStockById(id: Int): FilmStock? = null
    override suspend fun insertFilmStock(stock: FilmStock) = 1L
    override suspend fun insertAllStocks(stocks: List<FilmStock>) = Unit
    override suspend fun deleteFilmStockById(id: Int) = Unit
  }
}
