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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FilmStockSaveTest {
  private val dispatcher = StandardTestDispatcher()
  private val store = ViewModelStore()
  private val stocks = PendingStocks()
  private lateinit var model: LightMeterViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    val application = ApplicationProvider.getApplicationContext<Application>()
    application.getSharedPreferences("meter_setup", Context.MODE_PRIVATE).edit().clear().commit()
    model = LightMeterViewModel(application, FilmRepository(stocks, EmptyLogs()))
    store.put("meter", model)
    dispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    store.clear()
    Dispatchers.resetMain()
  }

  @Test
  fun pendingSaveRejectsDuplicatesAndFailedDraftCanRetryWithTheSameData() {
    val draft = FilmStock(
      name = "Test film", brand = "Test brand", iso = 125, type = "Black & White",
      reciprocityFactor = 1.31, description = "Custom film profile",
      badgeStyle = "CUSTOM", isCustom = true
    )

    model.addCustomFilmStock(draft)
    model.addCustomFilmStock(draft.copy(name = "Duplicate attempt"))
    assertTrue(model.isAddingFilm.value)
    assertNull(model.filmAddedId.value)
    dispatcher.scheduler.runCurrent()
    assertEquals(listOf(draft), stocks.attempts)

    stocks.result.completeExceptionally(IllegalStateException("Storage unavailable"))
    dispatcher.scheduler.advanceUntilIdle()
    assertFalse(model.isAddingFilm.value)
    assertNull(model.filmAddedId.value)
    assertEquals("Could not add the film. Try again.", model.userMessage.value)

    stocks.result = CompletableDeferred()
    model.addCustomFilmStock(draft)
    assertTrue(model.isAddingFilm.value)
    dispatcher.scheduler.runCurrent()
    assertEquals(listOf(draft, draft), stocks.attempts)
    stocks.result.complete(42L)
    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(model.isAddingFilm.value)
    assertEquals(42, model.filmAddedId.value)
    assertEquals("Added stock: Test film", model.userMessage.value)
    model.clearFilmAddedReceipt()
    assertNull(model.filmAddedId.value)
  }

  private class PendingStocks : FilmStockDao {
    var result = CompletableDeferred<Long>()
    val attempts = mutableListOf<FilmStock>()
    override fun getAllFilmStocks() = MutableStateFlow(emptyList<FilmStock>())
    override suspend fun getFilmStockById(id: Int): FilmStock? = null
    override suspend fun insertFilmStock(stock: FilmStock): Long {
      attempts += stock
      return result.await()
    }
    override suspend fun insertAllStocks(stocks: List<FilmStock>) = Unit
    override suspend fun deleteFilmStockById(id: Int) = Unit
  }

  private class EmptyLogs : FilmLogDao {
    override fun getAllLogEntries() = MutableStateFlow(emptyList<FilmLogEntry>())
    override fun getEntriesForRoll(rollId: String) = MutableStateFlow(emptyList<FilmLogEntry>())
    override suspend fun getLatestEntryForRoll(rollId: String): FilmLogEntry? = null
    override suspend fun insertLogEntry(entry: FilmLogEntry) = 1L
    override suspend fun deleteEntryById(id: Int) = Unit
    override suspend fun deleteRollEntries(rollId: String) = Unit
  }
}
