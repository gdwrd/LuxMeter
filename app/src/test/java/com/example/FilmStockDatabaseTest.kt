package com.example

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmRoll
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
    assertEquals(27, initial.size)
    assertEquals(27, initial.map { it.brand to it.name }.toSet().size)
    val additions = listOf(
      Triple("Kodak", "Portra 160", 160), Triple("Kodak", "Portra 800", 800),
      Triple("Kodak", "UltraMax 400", 400), Triple("Kodak", "ColorPlus 200", 200),
      Triple("Kodak", "Ektachrome", 100), Triple("Kodak", "T-MAX 100", 100),
      Triple("Kodak", "T-MAX 400", 400), Triple("Ilford", "FP4 Plus", 125),
      Triple("Ilford", "Delta 100", 100), Triple("Ilford", "Delta 400", 400),
      Triple("Ilford", "Pan F Plus", 50), Triple("Ilford", "XP2 Super", 400),
      Triple("Kentmere", "Pan 100", 100), Triple("Kentmere", "Pan 400", 400),
      Triple("CineStill", "50D", 50), Triple("CineStill", "400D", 400),
      Triple("Foma", "Fomapan 100", 100), Triple("Fujifilm", "Acros II", 100)
    )
    assertTrue(initial.map { Triple(it.brand, it.name, it.iso) }.containsAll(additions))
    assertTrue(initial.all { !it.isCustom && it.hasReciprocityProfile })
    assertTrue(initial.all { it.calculateCorrectedExposure(1.0 / 125.0) == 1.0 / 125.0 })
    assertEquals(2.0, initial.single { it.name == "Tri-X 400" }.calculateCorrectedExposure(1.0)!!, 0.000001)
    first.close()
    val reopened = open().filmStockDao().getAllFilmStocks().first()
    assertEquals(initial, reopened)
  }

  @Test fun openingAnOldEmptyDatabaseRepairsMissingSeed() = runBlocking {
    val old = open(repair = false)
    assertTrue(old.filmStockDao().getAllFilmStocks().first().isEmpty())
    old.close()
    assertEquals(27, open().filmStockDao().getAllFilmStocks().first().size)
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
    val roll = FilmRoll("roll", "My roll", filmStockId = legacy.id, filmStockName = legacy.name,
      iso = 200, lastFrameNumber = 3, createdAt = 1234L)
    old.filmRollDao().saveRoll(roll)
    old.close()

    val repaired = open()
    val profiles = repaired.filmStockDao().getAllFilmStocks().first()
    assertEquals(28, profiles.size)
    assertEquals(legacy.copy(reciprocityFactor = 1.0), profiles.single { it.id == 41 })
    assertEquals(custom, profiles.single { it.id == 87 })
    assertEquals(listOf(log), repaired.filmLogDao().getAllLogEntries().first())
    assertEquals(roll, repaired.filmRollDao().getRoll("roll"))
  }

  @Test fun expandingNinePresetCataloguePreservesEveryBuiltinIdentity() = runBlocking {
    val old = open(repair = false)
    val legacyNames = listOf(
      "Kodak" to "Portra 400", "Kodak" to "Tri-X 400", "Ilford" to "HP5 Plus 400",
      "Kodak" to "Gold 200", "CineStill" to "CineStill 800T", "Fujifilm" to "Velvia 50",
      "Fujifilm" to "Superia X-TRA 400", "Kodak" to "Ektar 100", "Ilford" to "Delta 3200"
    )
    val legacy = legacyNames.mapIndexed { index, (brand, name) ->
      FilmStock(101 + index, name, brand, 400, "Existing type", 1.22,
        description = "Saved description", badgeStyle = "Saved style", hasReciprocityProfile = false)
    }
    legacy.forEach { old.filmStockDao().insertFilmStock(it) }
    old.close()

    val profiles = open().filmStockDao().getAllFilmStocks().first()
    assertEquals(27, profiles.size)
    legacy.forEach { previous ->
      val current = profiles.single { it.brand == previous.brand && it.name == previous.name }
      assertEquals(previous.id, current.id)
      assertEquals(previous.iso, current.iso)
      assertEquals(previous.type, current.type)
      assertEquals(previous.description, current.description)
      assertEquals(previous.badgeStyle, current.badgeStyle)
      assertTrue(current.hasReciprocityProfile)
    }
  }

  @Test fun failedRepairRollsBackEverySeedAndProfileUpdate() = runBlocking {
    val database = open(repair = false)
    val legacy = FilmStock(41, "Portra 400", "Kodak", 400, "Color Negative", 1.22,
      description = "Existing", badgeStyle = "PORTRA")
    database.filmStockDao().insertFilmStock(legacy)
    val sqlite = database.openHelper.writableDatabase
    sqlite.execSQL("CREATE TRIGGER fail_seed BEFORE INSERT ON film_stocks WHEN NEW.name = 'Delta 400' BEGIN SELECT RAISE(ABORT, 'seed failure'); END")
    try {
      AppDatabase.Companion.DatabaseCallback().onOpen(sqlite)
      fail("Expected the seed insertion to fail")
    } catch (_: SQLiteException) {
      assertEquals(listOf(legacy), database.filmStockDao().getAllFilmStocks().first())
    }
  }
}
