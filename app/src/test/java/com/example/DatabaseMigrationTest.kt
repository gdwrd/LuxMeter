package com.example

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.CameraFormat
import com.example.data.model.CameraSetup
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
class DatabaseMigrationTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val name = "migration-${UUID.randomUUID()}.db"
  private val databases = mutableListOf<AppDatabase>()

  private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
    .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
    .addCallback(AppDatabase.Companion.DatabaseCallback())
    .allowMainThreadQueries()
    .build().also { databases += it }

  @After fun cleanUp() {
    databases.forEach { it.close() }
    context.deleteDatabase(name)
  }

  @Test fun populatedVersionOnePreservesFramesAndCustomFilmsAndBackfillsRolls() = runBlocking {
    val custom = FilmStock(87, "Home emulsion", "My lab", 200, "Black & White", 1.6,
      description = "My tested correction", badgeStyle = "CUSTOM", isCustom = true)
    val builtin = FilmStock(41, "Portra 400", "Kodak", 400, "Color Negative", 1.22,
      description = "Existing description", badgeStyle = "PORTRA")
    val first = frame(7, "square", 2, "6×6", 1000L).copy(
      rollName = "Winter square", filmStockName = custom.name, iso = 200, notes = "Keep this note")
    val frames = listOf(
      first,
      first.copy(id = 41, frameNumber = 19, timestamp = 3000L, rollName = "Later name",
        filmStockName = "Later film", iso = 800, format = "6×7"),
      first.copy(id = 23, frameNumber = 3, timestamp = 4000L),
      frame(99, "645", 15, "6×4.5", 2000L),
      frame(108, "35mm", 24, "35mm", 2500L),
      frame(109, "67", 10, "6×7", 3500L)
    )
    createVersionOne(listOf(custom, builtin), frames)

    val database = open()
    val repository = database.repository()
    assertEquals(custom, repository.getFilmStock(custom.id))
    assertEquals(builtin.copy(reciprocityFactor = 1.0), repository.getFilmStock(builtin.id))
    assertEquals(frames.sortedBy { it.id }, repository.allLogEntries.first().sortedBy { it.id })
    assertEquals(FilmRoll(
      id = "square", name = "Winter square", filmStockId = custom.id, filmStockName = custom.name,
      iso = 200, format = CameraFormat.MEDIUM_66.name, frameCapacity = 19, lastFrameNumber = 19,
      isFinished = true, createdAt = 1000L
    ), repository.getRoll("square"))
    val rolls = repository.allRolls.first().associateBy { it.id }
    assertEquals(4, rolls.size)
    assertEquals(mapOf("square" to "MEDIUM_66", "645" to "MEDIUM_645", "35mm" to "FORMAT_35MM", "67" to "MEDIUM_67"),
      rolls.mapValues { it.value.format })
    assertEquals(16, rolls.getValue("645").frameCapacity)
    assertEquals(36, rolls.getValue("35mm").frameCapacity)
    assertEquals(10, rolls.getValue("67").frameCapacity)
    assertTrue(rolls.values.all { it.isFinished })
    assertTrue(rolls.values.all { it.boxIso == null && it.pushStops == null })
    assertTrue(repository.allLogEntries.first().all { it.boxIso == null })
    assertNull(rolls.getValue("645").filmStockId)
    assertTrue(repository.allCameraSetups.first().isEmpty())
    database.close()

    val reopened = open().repository()
    assertEquals(rolls, reopened.allRolls.first().associateBy { it.id })
    assertEquals(frames.size, reopened.allLogEntries.first().size)
    assertEquals(custom, reopened.getFilmStock(custom.id))
  }

  @Test fun emptyVersionOneMigratesAndSeedsWithoutInventingRolls() = runBlocking {
    createVersionOne(emptyList(), emptyList())
    val database = open()
    val repository = database.repository()
    assertEquals(3, database.openHelper.readableDatabase.version)
    assertEquals(9, repository.allFilmStocks.first().size)
    assertTrue(repository.allLogEntries.first().isEmpty())
    assertTrue(repository.allRolls.first().isEmpty())
    assertTrue(repository.allCameraSetups.first().isEmpty())
  }

  @Test fun versionTwoPreservesShootingIsoAndLeavesProcessingAndBoxSpeedUnknown() = runBlocking {
    val stock = FilmStock(87, "Home emulsion", "My lab", 400, "Black & White", 1.6,
      description = "My tested correction", badgeStyle = "CUSTOM", isCustom = true)
    val entry = frame(7, "loaded", 3, "35mm", 123L).copy(
      filmStockName = stock.name, iso = 1600)
    createVersionOne(listOf(stock), listOf(entry))
    val old = FrameworkSQLiteOpenHelperFactory().create(
      SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
        .callback(object : SupportSQLiteOpenHelper.Callback(2) {
          override fun onCreate(db: SupportSQLiteDatabase) = error("Version 1 fixture must exist")
          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            AppDatabase.MIGRATION_1_2.migrate(db)
          }
        }).build()
    )
    old.writableDatabase.execSQL("UPDATE film_rolls SET name = 'Loaded legacy roll', isFinished = 0, frameCapacity = 24 WHERE id = 'loaded'")
    old.writableDatabase.execSQL("UPDATE film_log_entries SET cameraName = 'My camera', exposureCompensation = 1, filterStops = 2, calibrationEv = 0.5 WHERE id = 7")
    old.close()

    val database = open()
    val repository = database.repository()
    val expectedRoll = FilmRoll(id = "loaded", name = "Loaded legacy roll", filmStockId = stock.id,
      filmStockName = stock.name, iso = 1600, frameCapacity = 24, lastFrameNumber = 3, createdAt = 123L)
    val expectedFrame = entry.copy(cameraName = "My camera", exposureCompensation = 1.0,
      filterStops = 2.0, calibrationEv = 0.5f)
    assertEquals(3, database.openHelper.readableDatabase.version)
    assertEquals(expectedRoll, repository.getRoll("loaded"))
    assertEquals(listOf(expectedFrame), repository.getEntriesForRoll("loaded").first())
    assertEquals(stock, repository.getFilmStock(stock.id))
    database.close()

    val reopened = open().repository()
    assertEquals(expectedRoll, reopened.getRoll("loaded"))
    assertEquals(listOf(expectedFrame), reopened.getEntriesForRoll("loaded").first())
  }

  @Test fun migratedCustomProfileStaysEnabledAndUnknownNewProfileStaysUnknown() = runBlocking {
    val known = FilmStock(87, "Tested emulsion", "My lab", 200, "Black & White", 1.6,
      description = "My tested correction", badgeStyle = "CUSTOM", isCustom = true)
    createVersionOne(listOf(known), emptyList())
    val database = open()
    val repository = database.repository()
    assertTrue(repository.getFilmStock(known.id)!!.hasReciprocityProfile)
    val unknown = known.copy(id = 0, name = "Unprofiled emulsion", hasReciprocityProfile = false)
    val unknownId = repository.insertStock(unknown).toInt()
    database.close()

    val reopened = open().repository()
    assertEquals(known, reopened.getFilmStock(known.id))
    assertEquals(unknown.copy(id = unknownId), reopened.getFilmStock(unknownId))
    assertFalse(reopened.getFilmStock(unknownId)!!.hasReciprocityProfile)
  }

  @Test fun cameraAndRollSettingsSurviveReopeningAndFrameNotesRemainEditable() = runBlocking {
    val database = open()
    val repository = database.repository()
    val camera = CameraSetup(name = "My TLR", format = CameraFormat.MEDIUM_66.name,
      focalLengthMm = 80, minAperture = 3.5, maxAperture = 22.0,
      shutterSpeeds = "0.002,0.004,0.008,0.016666666666666666,1.0", allowsBulb = false)
    val cameraId = repository.saveCameraSetup(camera).toInt()
    val roll = FilmRoll(id = "loaded", name = "Weekend", filmStockName = "Home emulsion",
      iso = 1600, format = CameraFormat.MEDIUM_66.name, cameraSetupId = cameraId,
      focalLengthMm = 80, frameCapacity = 12, lastFrameNumber = 5, createdAt = 123L,
      boxIso = 400, pushStops = 2)
    repository.saveRoll(roll)
    val entry = frame(0, roll.id, 6, roll.format, 456L).copy(
      cameraName = camera.name, focalLengthMm = 80, meteringSource = "Camera",
      exposureCompensation = 1.0, filterStops = 2.0, calibrationEv = 0.5f,
      correctionKind = "Manual", isManual = true, hasMeterReading = false, iso = 800, boxIso = 400)
    val entryId = repository.insertFrame(entry, roll.copy(lastFrameNumber = 6)).toInt()
    repository.updateLogNotes(entryId, "Develop +1")
    database.close()

    val reopened = open().repository()
    assertEquals(camera.copy(id = cameraId), reopened.getCameraSetup(cameraId))
    assertEquals(roll.copy(lastFrameNumber = 6), reopened.getRoll(roll.id))
    assertEquals(listOf(entry.copy(id = entryId, notes = "Develop +1")), reopened.getEntriesForRoll(roll.id).first())
    reopened.deleteFrameAndUpdateRoll(entryId, roll)
    assertEquals(roll, reopened.getRoll(roll.id))
    assertTrue(reopened.getEntriesForRoll(roll.id).first().isEmpty())
  }

  @Test fun failedRollUpdateRollsBackInsertedFrame() = runBlocking {
    val database = open()
    val repository = database.repository()
    val roll = FilmRoll(id = "atomic", name = "Transaction", createdAt = 123L)
    repository.saveRoll(roll)
    rejectRollFrameNumber(database, 1)

    try {
      repository.insertFrame(frame(0, roll.id, 1), roll.copy(lastFrameNumber = 1))
      fail("Expected the roll update to fail")
    } catch (_: SQLiteException) {
      assertTrue(repository.getEntriesForRoll(roll.id).first().isEmpty())
      assertEquals(roll, repository.getRoll(roll.id))
    }
  }

  @Test fun failedRollUpdateRollsBackDeletedFrame() = runBlocking {
    val database = open()
    val repository = database.repository()
    val roll = FilmRoll(id = "atomic", name = "Transaction", lastFrameNumber = 1, createdAt = 123L)
    val entry = frame(0, roll.id, 1)
    val entryId = repository.insertFrame(entry, roll).toInt()
    rejectRollFrameNumber(database, 0)

    try {
      repository.deleteFrameAndUpdateRoll(entryId, roll.copy(lastFrameNumber = 0))
      fail("Expected the roll update to fail")
    } catch (_: SQLiteException) {
      assertEquals(listOf(entry.copy(id = entryId)), repository.getEntriesForRoll(roll.id).first())
      assertEquals(roll, repository.getRoll(roll.id))
    }
  }

  private fun rejectRollFrameNumber(database: AppDatabase, number: Int) {
    for (operation in listOf("INSERT", "UPDATE")) {
      database.openHelper.writableDatabase.execSQL("""
        CREATE TRIGGER fail_roll_${operation.lowercase()} BEFORE $operation ON film_rolls
        WHEN NEW.id = 'atomic' AND NEW.lastFrameNumber = $number
        BEGIN SELECT RAISE(ABORT, 'roll update failed'); END
      """.trimIndent())
    }
  }

  private fun frame(id: Int, rollId: String, number: Int, format: String = "FORMAT_35MM", timestamp: Long = 123L) =
    FilmLogEntry(id, rollId, "Old roll", number, "Unlisted film", 400, format, "f/8", "4s",
      4.0, 5.426, 4.0, 40f, "Original note", timestamp)

  private fun createVersionOne(stocks: List<FilmStock>, frames: List<FilmLogEntry>) {
    context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { database ->
      // Exact version 1 tables: constructor defaults did not create SQL DEFAULT clauses.
      database.execSQL("""
        CREATE TABLE film_stocks (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, brand TEXT NOT NULL,
          iso INTEGER NOT NULL, type TEXT NOT NULL, reciprocityFactor REAL NOT NULL,
          reciprocityThresholdSec REAL NOT NULL, description TEXT NOT NULL, badgeStyle TEXT NOT NULL,
          isCustom INTEGER NOT NULL)
      """.trimIndent())
      database.execSQL("""
        CREATE TABLE film_log_entries (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, rollId TEXT NOT NULL, rollName TEXT NOT NULL,
          frameNumber INTEGER NOT NULL, filmStockName TEXT NOT NULL, iso INTEGER NOT NULL,
          format TEXT NOT NULL, apertureText TEXT NOT NULL, shutterText TEXT NOT NULL,
          rawSeconds REAL NOT NULL, correctedSeconds REAL NOT NULL, evValue REAL NOT NULL,
          luxValue REAL NOT NULL, notes TEXT NOT NULL, timestamp INTEGER NOT NULL)
      """.trimIndent())
      for (stock in stocks) {
        database.execSQL("INSERT INTO film_stocks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          arrayOf(stock.id, stock.name, stock.brand, stock.iso, stock.type, stock.reciprocityFactor,
            stock.reciprocityThresholdSec, stock.description, stock.badgeStyle, if (stock.isCustom) 1 else 0))
      }
      for (entry in frames) {
        database.execSQL("INSERT INTO film_log_entries VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          arrayOf(entry.id, entry.rollId, entry.rollName, entry.frameNumber, entry.filmStockName,
            entry.iso, entry.format, entry.apertureText, entry.shutterText, entry.rawSeconds,
            entry.correctedSeconds, entry.evValue, entry.luxValue, entry.notes, entry.timestamp))
      }
      database.version = 1
    }
  }
}
