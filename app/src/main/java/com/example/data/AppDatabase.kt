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
  version = 4,
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

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE film_log_entries ADD COLUMN photoFileName TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE film_rolls ADD COLUMN filmType TEXT DEFAULT NULL")
        db.execSQL("UPDATE film_rolls SET filmType = (SELECT type FROM film_stocks WHERE id = film_rolls.filmStockId)")
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
          ),
          FilmStock(
            name = "Portra 160", brand = "Kodak", iso = 160, type = "Color Negative",
            reciprocityFactor = 1.0,
            description = "Fine grain and natural skin tones for portraits in generous light.",
            badgeStyle = "PORTRA"
          ),
          FilmStock(
            name = "Portra 800", brand = "Kodak", iso = 800, type = "Color Negative",
            reciprocityFactor = 1.0,
            description = "High-speed color negative film with natural skin tones for available light.",
            badgeStyle = "PORTRA"
          ),
          FilmStock(
            name = "UltraMax 400", brand = "Kodak", iso = 400, type = "Color Negative",
            reciprocityFactor = 1.0,
            description = "Versatile everyday color with vivid saturation and broad exposure latitude.",
            badgeStyle = "ULTRAMAX"
          ),
          FilmStock(
            name = "ColorPlus 200", brand = "Kodak", iso = 200, type = "Color Negative",
            reciprocityFactor = 1.0,
            description = "Everyday daylight color negative film with warm tones and classic grain.",
            badgeStyle = "COLORPLUS"
          ),
          FilmStock(
            name = "Ektachrome", brand = "Kodak", iso = 100, type = "Color Slide",
            reciprocityFactor = 1.0,
            description = "E100 daylight-balanced slide film with fine grain and natural color.",
            badgeStyle = "EKTACHROME"
          ),
          FilmStock(
            name = "T-MAX 100", brand = "Kodak", iso = 100, type = "Black & White",
            reciprocityFactor = 1.0, reciprocityThresholdSec = 0.1,
            description = "Fine-grained black-and-white film with high sharpness for detailed subjects.",
            badgeStyle = "T_MAX"
          ),
          FilmStock(
            name = "T-MAX 400", brand = "Kodak", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0,
            description = "High-speed black-and-white film with fine grain and smooth tonal separation.",
            badgeStyle = "T_MAX"
          ),
          FilmStock(
            name = "FP4 Plus", brand = "Ilford", iso = 125, type = "Black & White",
            reciprocityFactor = 1.0,
            description = "Classic medium-speed black-and-white film with fine grain and rich tones.",
            badgeStyle = "FP4"
          ),
          FilmStock(
            name = "Delta 100", brand = "Ilford", iso = 100, type = "Black & White",
            reciprocityFactor = 1.0,
            description = "Fine grain and high sharpness for detailed black-and-white photographs.",
            badgeStyle = "DELTA"
          ),
          FilmStock(
            name = "Delta 400", brand = "Ilford", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0,
            description = "Flexible high-speed black-and-white film with refined grain and detail.",
            badgeStyle = "DELTA"
          ),
          FilmStock(
            name = "Pan F Plus", brand = "Ilford", iso = 50, type = "Black & White",
            reciprocityFactor = 1.0,
            description = "Slow black-and-white film with very fine grain and crisp detail.",
            badgeStyle = "PAN_F"
          ),
          FilmStock(
            name = "XP2 Super", brand = "Ilford", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0,
            description = "Versatile black-and-white film developed in standard C-41 chemistry.",
            badgeStyle = "XP2"
          ),
          FilmStock(
            name = "Pan 100", brand = "Kentmere", iso = 100, type = "Black & White",
            reciprocityFactor = 1.0,
            description = "Accessible fine-grained black-and-white film for everyday daylight photography.",
            badgeStyle = "KENTMERE"
          ),
          FilmStock(
            name = "Pan 400", brand = "Kentmere", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0,
            description = "Versatile black-and-white film for everyday scenes and available light.",
            badgeStyle = "KENTMERE"
          ),
          FilmStock(
            name = "50D", brand = "CineStill", iso = 50, type = "Color Negative",
            reciprocityFactor = 1.0,
            description = "Daylight-balanced cinema-derived color film with fine grain and halation.",
            badgeStyle = "CINESTILL"
          ),
          FilmStock(
            name = "400D", brand = "CineStill", iso = 400, type = "Color Negative",
            reciprocityFactor = 1.0,
            description = "Daylight-balanced color film with wide latitude and warm halation.",
            badgeStyle = "CINESTILL"
          ),
          FilmStock(
            name = "Fomapan 100", brand = "Foma", iso = 100, type = "Black & White",
            reciprocityFactor = 1.0, reciprocityThresholdSec = 0.5,
            description = "Traditional black-and-white film with fine grain and a broad tonal range.",
            badgeStyle = "FOMAPAN"
          ),
          FilmStock(
            name = "Acros II", brand = "Fujifilm", iso = 100, type = "Black & White",
            reciprocityFactor = 1.0, reciprocityThresholdSec = 120.0,
            description = "Fine-grained black-and-white film with excellent reciprocity characteristics.",
            badgeStyle = "ACROS"
          ),
          FilmStock(
            name = "Pan 200", brand = "Kentmere", iso = 200, type = "Black & White",
            reciprocityFactor = 1.26,
            description = "Medium-speed black-and-white film with fine grain and medium-high contrast.",
            badgeStyle = "KENTMERE"
          ),
          FilmStock(
            name = "SFX 200", brand = "Ilford", iso = 200, type = "Black & White",
            reciprocityFactor = 1.43,
            description = "Extended-red-sensitive black-and-white film. Infrared effects need a suitable filter and exposure tests.",
            badgeStyle = "SFX"
          ),
          FilmStock(
            name = "Ortho Plus", brand = "Ilford", iso = 80, type = "Black & White",
            reciprocityFactor = 1.25,
            description = "Orthochromatic black-and-white film; rate at EI 80 in daylight or EI 40 in tungsten light.",
            badgeStyle = "ORTHO"
          ),
          FilmStock(
            name = "Fomapan 200", brand = "Foma", iso = 200, type = "Black & White",
            reciprocityFactor = 1.0, reciprocityThresholdSec = 0.5,
            description = "Medium-speed black-and-white negative film with fine grain and high resolution.",
            badgeStyle = "FOMAPAN"
          ),
          FilmStock(
            name = "Fomapan 400", brand = "Foma", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0, reciprocityThresholdSec = 0.5,
            description = "High-speed black-and-white negative film for available light and shorter exposures.",
            badgeStyle = "FOMAPAN"
          ),
          FilmStock(
            name = "Retropan 320", brand = "Foma", iso = 320, type = "Black & White",
            reciprocityFactor = 1.0, reciprocityThresholdSec = 0.5,
            description = "Black-and-white negative film with soft contrast and a broad range of mid-tones.",
            badgeStyle = "RETROPAN"
          ),
          FilmStock(
            name = "Ortho 400", brand = "Foma", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0, reciprocityThresholdSec = 0.5,
            description = "High-speed orthochromatic black-and-white film with strong green sensitivity.",
            badgeStyle = "ORTHO"
          ),
          FilmStock(
            name = "Fomapan R 100", brand = "Foma", iso = 100, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Black-and-white reversal film for slides; requires a suitable reversal process.",
            badgeStyle = "FOMAPAN"
          ),
          FilmStock(
            name = "RPX 25", brand = "Rollei", iso = 25, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Low-speed black-and-white negative film for fine grain and detailed subjects.",
            badgeStyle = "RPX"
          ),
          FilmStock(
            name = "RPX 100", brand = "Rollei", iso = 100, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Medium-speed black-and-white negative film for bright light and fine detail.",
            badgeStyle = "RPX"
          ),
          FilmStock(
            name = "RPX 400", brand = "Rollei", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "High-speed black-and-white negative film for changing light and moving subjects.",
            badgeStyle = "RPX"
          ),
          FilmStock(
            name = "Retro 80S", brand = "Rollei", iso = 80, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Fine-grained superpanchromatic black-and-white film on a clear base.",
            badgeStyle = "RETRO"
          ),
          FilmStock(
            name = "Retro 400S", brand = "Rollei", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "High-speed black-and-white negative film with extended spectral sensitivity.",
            badgeStyle = "RETRO"
          ),
          FilmStock(
            name = "Superpan 200", brand = "Rollei", iso = 200, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Black-and-white film with extended red sensitivity, suitable for negative or reversal processing.",
            badgeStyle = "SUPERPAN"
          ),
          FilmStock(
            name = "Infrared 400", brand = "Rollei", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Infrared-sensitive black-and-white film. ISO 400 is unfiltered; infrared filters require exposure tests.",
            badgeStyle = "INFRARED"
          ),
          FilmStock(
            name = "Ortho 25 Plus", brand = "Rollei", iso = 25, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Low-speed orthochromatic black-and-white film for fine detail and distinctive tones.",
            badgeStyle = "ORTHO"
          ),
          FilmStock(
            name = "BwXX", brand = "CineStill", iso = 250, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Panchromatic black-and-white cinema film; rate at EI 250 in daylight or EI 200 in tungsten light.",
            badgeStyle = "BWXX"
          ),
          FilmStock(
            name = "Phoenix II 200", brand = "Harman", iso = 200, type = "Color Negative",
            reciprocityFactor = 1.31,
            description = "Second-generation C-41 colour film. Maker recommends EI 100–200 and does not recommend push processing.",
            badgeStyle = "PHOENIX"
          ),
          FilmStock(
            name = "Phoenix 125", brand = "Harman", iso = 125, type = "Color Negative",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "High-contrast C-41 colour film with visible grain, rated at ISO 125.",
            badgeStyle = "PHOENIX"
          ),
          FilmStock(
            name = "RED 125", brand = "Harman", iso = 125, type = "Color Negative",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "C-41 redscale film with warm red and orange tones. Maker recommends starting at EI 100–200.",
            badgeStyle = "RED"
          ),
          FilmStock(
            name = "Switch Azure 125", brand = "Harman", iso = 125, type = "Color Negative",
            reciprocityFactor = 1.31,
            description = "C-41 colour-shifting film, rated at ISO 125. Maker does not recommend push processing.",
            badgeStyle = "AZURE"
          ),
          FilmStock(
            name = "Color Negative 100", brand = "Lomography", iso = 100, type = "Color Negative",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Daylight-speed C-41 colour negative film.",
            badgeStyle = "LOMOGRAPHY"
          ),
          FilmStock(
            name = "Color Negative 400", brand = "Lomography", iso = 400, type = "Color Negative",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "General-purpose C-41 colour negative film for changing light.",
            badgeStyle = "LOMOGRAPHY"
          ),
          FilmStock(
            name = "Color Negative 800", brand = "Lomography", iso = 800, type = "Color Negative",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "High-speed C-41 colour negative film for available light.",
            badgeStyle = "LOMOGRAPHY"
          ),
          FilmStock(
            name = "Earl Grey 100", brand = "Lomography", iso = 100, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Medium-speed black-and-white negative film for daylight scenes.",
            badgeStyle = "EARL GREY"
          ),
          FilmStock(
            name = "Lady Grey 400", brand = "Lomography", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "High-speed black-and-white negative film for mixed lighting.",
            badgeStyle = "LADY GREY"
          ),
          FilmStock(
            name = "Potsdam Kino 100", brand = "Lomography", iso = 100, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "Medium-speed black-and-white cinema film for daylight photography.",
            badgeStyle = "POTSDAM"
          ),
          FilmStock(
            name = "Berlin Kino 400", brand = "Lomography", iso = 400, type = "Black & White",
            reciprocityFactor = 1.0, hasReciprocityProfile = false,
            description = "High-speed black-and-white cinema film for varied lighting.",
            badgeStyle = "BERLIN"
          )
        )
        // Repair old/empty installs before Room serves queries, without a ViewModel lifetime race.
        db.beginTransaction()
        try {
          for (stock in initialStocks) {
            val profile = ContentValues().apply {
              put("reciprocityFactor", stock.reciprocityFactor)
              put("reciprocityThresholdSec", stock.reciprocityThresholdSec)
              put("hasReciprocityProfile", stock.hasReciprocityProfile)
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
