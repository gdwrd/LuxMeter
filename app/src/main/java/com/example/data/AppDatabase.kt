package com.example.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.CameraSetupDao
import com.example.data.dao.FilmLogDao
import com.example.data.dao.FilmRollDao
import com.example.data.dao.FilmStockDao
import com.example.data.model.CameraFormat
import com.example.data.model.CameraSetup
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmRoll
import com.example.data.model.FilmStock
import com.example.data.repository.FilmRepository

@Database(
  entities = [FilmStock::class, FilmLogEntry::class, CameraSetup::class, FilmRoll::class],
  version = 3,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun filmStockDao(): FilmStockDao
  abstract fun filmLogDao(): FilmLogDao
  abstract fun cameraSetupDao(): CameraSetupDao
  abstract fun filmRollDao(): FilmRollDao

  fun repository(): FilmRepository = FilmRepository(filmStockDao(), filmLogDao(), cameraSetupDao(), filmRollDao(), this)

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE film_stocks ADD COLUMN hasReciprocityProfile INTEGER NOT NULL DEFAULT 1")
        for (column in listOf(
          "cameraName TEXT NOT NULL DEFAULT ''", "focalLengthMm INTEGER NOT NULL DEFAULT 0",
          "meteringSource TEXT NOT NULL DEFAULT ''", "exposureCompensation REAL NOT NULL DEFAULT 0",
          "filterStops REAL NOT NULL DEFAULT 0", "calibrationEv REAL NOT NULL DEFAULT 0",
          "correctionKind TEXT NOT NULL DEFAULT ''", "isManual INTEGER NOT NULL DEFAULT 0",
          "hasMeterReading INTEGER NOT NULL DEFAULT 1"
        )) db.execSQL("ALTER TABLE film_log_entries ADD COLUMN $column")
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS camera_setups (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL, format TEXT NOT NULL, focalLengthMm INTEGER NOT NULL,
            minAperture REAL NOT NULL, maxAperture REAL NOT NULL,
            shutterSpeeds TEXT NOT NULL, allowsBulb INTEGER NOT NULL
          )
        """.trimIndent())
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS film_rolls (
            id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, filmStockId INTEGER,
            filmStockName TEXT NOT NULL, iso INTEGER NOT NULL, format TEXT NOT NULL,
            cameraSetupId INTEGER, focalLengthMm INTEGER NOT NULL, frameCapacity INTEGER NOT NULL,
            lastFrameNumber INTEGER NOT NULL, isFinished INTEGER NOT NULL, createdAt INTEGER NOT NULL
          )
        """.trimIndent())

        val stockIds = mutableMapOf<String, Int?>()
        db.query("SELECT name, id FROM film_stocks").use { cursor ->
          while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            stockIds[name] = if (stockIds.containsKey(name)) null else cursor.getInt(1)
          }
        }
        val rolls = linkedMapOf<String, FilmRoll>()
        db.query("SELECT rollId, rollName, filmStockName, iso, format, timestamp, frameNumber FROM film_log_entries ORDER BY timestamp ASC, id ASC").use { cursor ->
          while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val frame = cursor.getInt(6).coerceAtLeast(0)
            val existing = rolls[id]
            if (existing != null) {
              rolls[id] = existing.copy(
                lastFrameNumber = maxOf(existing.lastFrameNumber, frame),
                frameCapacity = maxOf(existing.frameCapacity, frame)
              )
            } else {
              val oldFormat = cursor.getString(4)
              val format = CameraFormat.entries.firstOrNull {
                it.name == oldFormat || it.displayName == oldFormat || it.displayName.replace('×', 'x') == oldFormat
              } ?: CameraFormat.FORMAT_35MM
              val stockName = cursor.getString(2)
              rolls[id] = FilmRoll(
                id = id, name = cursor.getString(1), filmStockId = stockIds[stockName], filmStockName = stockName,
                iso = cursor.getInt(3), format = format.name, frameCapacity = maxOf(format.standardFramesPerRoll, frame),
                lastFrameNumber = frame, isFinished = true, createdAt = cursor.getLong(5)
              )
            }
          }
        }
        for (roll in rolls.values) {
          db.execSQL("""
            INSERT INTO film_rolls (id, name, filmStockId, filmStockName, iso, format,
              cameraSetupId, focalLengthMm, frameCapacity, lastFrameNumber, isFinished, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """.trimIndent(), arrayOf<Any?>(
            roll.id, roll.name, roll.filmStockId, roll.filmStockName, roll.iso, roll.format,
            roll.cameraSetupId, roll.focalLengthMm, roll.frameCapacity, roll.lastFrameNumber, 1, roll.createdAt
          ))
        }
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE film_rolls ADD COLUMN boxIso INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE film_rolls ADD COLUMN pushStops INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE film_log_entries ADD COLUMN boxIso INTEGER DEFAULT NULL")
      }
    }

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE?.let { return@synchronized it }
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "luxmeter_analog.db"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .addCallback(DatabaseCallback())
        .build()
        INSTANCE = instance
        instance
      }
    }

    internal class DatabaseCallback : RoomDatabase.Callback() {
      override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        val initialStocks = listOf(
          FilmStock(
            name = "Portra 400",
            brand = "Kodak",
            iso = 400,
            type = "Color Negative",
            reciprocityFactor = 1.0,
            description = "Iconic warm skin tones, fine grain, and exceptional exposure latitude.",
            badgeStyle = "PORTRA"
          ),
          FilmStock(
            name = "Tri-X 400",
            brand = "Kodak",
            iso = 400,
            type = "Black & White",
            reciprocityFactor = 1.0,
            reciprocityThresholdSec = 0.1,
            description = "Legendary photojournalism film, classic gritty grain and deep blacks.",
            badgeStyle = "TRI_X"
          ),
          FilmStock(
            name = "HP5 Plus 400",
            brand = "Ilford",
            iso = 400,
            type = "Black & White",
            reciprocityFactor = 1.31,
            description = "Versatile medium contrast B&W film, excellent for pushing up to 3200.",
            badgeStyle = "HP5"
          ),
          FilmStock(
            name = "Gold 200",
            brand = "Kodak",
            iso = 200,
            type = "Color Negative",
            reciprocityFactor = 1.0,
            description = "Warm golden nostalgic aesthetic, saturated colors, great daylight stock.",
            badgeStyle = "GOLD"
          ),
          FilmStock(
            name = "CineStill 800T",
            brand = "CineStill",
            iso = 800,
            type = "Tungsten Cine",
            reciprocityFactor = 1.30,
            description = "Motion picture cinema emulsion, unique red halation glow around point lights.",
            badgeStyle = "CINESTILL"
          ),
          FilmStock(
            name = "Velvia 50",
            brand = "Fujifilm",
            iso = 50,
            type = "Color Slide",
            reciprocityFactor = 1.0,
            description = "High saturation, ultra-fine grain reversal slide film for landscapes.",
            badgeStyle = "FUJI_VELVIA"
          ),
          FilmStock(
            name = "Superia X-TRA 400",
            brand = "Fujifilm",
            iso = 400,
            type = "Color Negative",
            reciprocityFactor = 1.0,
            reciprocityThresholdSec = 2.0,
            description = "Punchy greens and accurate whites, great all-around snapshot film.",
            badgeStyle = "FUJI_SUPERIA"
          ),
          FilmStock(
            name = "Ektar 100",
            brand = "Kodak",
            iso = 100,
            type = "Color Negative",
            reciprocityFactor = 1.0,
            description = "Finest grain color negative in the world, vivid saturation for travel & nature.",
            badgeStyle = "EKTAR"
          ),
          FilmStock(
            name = "Delta 3200",
            brand = "Ilford",
            iso = 3200,
            type = "Black & White",
            reciprocityFactor = 1.33,
            description = "Ultra high-speed B&W film for low available light and moody street scenes.",
            badgeStyle = "DELTA"
          )
        )
        // Repair old/empty installs before Room serves queries, without a ViewModel lifetime race.
        db.beginTransaction()
        try {
          for (stock in initialStocks) {
            val profile = ContentValues().apply {
              put("reciprocityFactor", stock.reciprocityFactor)
              put("reciprocityThresholdSec", stock.reciprocityThresholdSec)
            }
            val updated = db.update(
              "film_stocks", SQLiteDatabase.CONFLICT_ABORT, profile,
              "name = ? AND brand = ? AND isCustom = 0", arrayOf(stock.name, stock.brand)
            )
            if (updated == 0) {
              val values = ContentValues(profile).apply {
                put("name", stock.name)
                put("brand", stock.brand)
                put("iso", stock.iso)
                put("type", stock.type)
                put("description", stock.description)
                put("badgeStyle", stock.badgeStyle)
                put("isCustom", false)
              }
              db.insert("film_stocks", SQLiteDatabase.CONFLICT_ABORT, values)
            }
          }
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }
      }
    }
  }
}
