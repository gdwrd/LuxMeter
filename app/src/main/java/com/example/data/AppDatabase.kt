package com.example.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FilmLogDao
import com.example.data.dao.FilmStockDao
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmStock

@Database(
  entities = [FilmStock::class, FilmLogEntry::class],
  version = 1,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun filmStockDao(): FilmStockDao
  abstract fun filmLogDao(): FilmLogDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE?.let { return@synchronized it }
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "luxmeter_analog.db"
        )
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
