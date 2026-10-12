package com.example

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmStock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FilmStockDatabaseTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val name = "film-profiles-${UUID.randomUUID()}.db"
  private val databases = mutableListOf<AppDatabase>()

  private fun open(repair: Boolean = true): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
    .allowMainThreadQueries()
    .apply { if (repair) addCallback(AppDatabase.Companion.DatabaseCallback()) }
    .build().also { databases += it }

  @After fun cleanUp() {
    databases.forEach { it.close() }
    context.deleteDatabase(name)
  }

  @Test fun firstQueryGetsAllProfilesAndReopeningDoesNotDuplicateThem() = runBlocking {
    val first = open()
    val initial = first.filmStockDao().getAllFilmStocks().first()
    assertEquals(9, initial.size)
    assertEquals(2.0, initial.single { it.name == "Tri-X 400" }.calculateCorrectedExposure(1.0)!!, 0.000001)
    first.close()
    val reopened = open().filmStockDao().getAllFilmStocks().first()
    assertEquals(initial, reopened)
  }

  @Test fun openingAnOldEmptyDatabaseRepairsMissingSeed() = runBlocking {
    val old = open(repair = false)
    assertTrue(old.filmStockDao().getAllFilmStocks().first().isEmpty())
    old.close()
    assertEquals(9, open().filmStockDao().getAllFilmStocks().first().size)
  }

  @Test fun repairPreservesBuiltinIdsCustomProfilesAndSavedExposures() = runBlocking {
    val old = open(repair = false)
    val legacy = FilmStock(41, "Portra 400", "Kodak", 400, "Color Negative", 1.22,
      description = "Existing description", badgeStyle = "PORTRA")
    val custom = legacy.copy(id = 87, reciprocityFactor = 1.6, description = "My tests", isCustom = true)
    old.filmStockDao().insertFilmStock(legacy)
    old.filmStockDao().insertFilmStock(custom)
    val log = FilmLogEntry(19, "roll", "My roll", 3, legacy.name, 200, "35mm", "f/8", "4s",
      4.0, 5.426, 4.0, 40f, "Keep this", 1234L)
    old.filmLogDao().insertLogEntry(log)
    old.close()

    val repaired = open()
    val profiles = repaired.filmStockDao().getAllFilmStocks().first()
    assertEquals(10, profiles.size)
    assertEquals(legacy.copy(reciprocityFactor = 1.0), profiles.single { it.id == 41 })
    assertEquals(custom, profiles.single { it.id == 87 })
    assertEquals(listOf(log), repaired.filmLogDao().getAllLogEntries().first())
  }

  @Test fun failedRepairRollsBackEverySeedAndProfileUpdate() = runBlocking {
    val database = open(repair = false)
    val legacy = FilmStock(41, "Portra 400", "Kodak", 400, "Color Negative", 1.22,
      description = "Existing", badgeStyle = "PORTRA")
    database.filmStockDao().insertFilmStock(legacy)
    val sqlite = database.openHelper.writableDatabase
    sqlite.execSQL("CREATE TRIGGER fail_seed BEFORE INSERT ON film_stocks WHEN NEW.name = 'HP5 Plus 400' BEGIN SELECT RAISE(ABORT, 'seed failure'); END")
    try {
      AppDatabase.Companion.DatabaseCallback().onOpen(sqlite)
      fail("Expected the seed insertion to fail")
    } catch (_: SQLiteException) {
      assertEquals(listOf(legacy), database.filmStockDao().getAllFilmStocks().first())
    }
  }
}
