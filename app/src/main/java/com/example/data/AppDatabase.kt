package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FilmLogDao
import com.example.data.dao.FilmStockDao
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmStock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "luxmeter_analog.db"
        )
        .addCallback(DatabaseCallback(scope))
        .build()
        INSTANCE = instance
        instance
      }
    }

    private class DatabaseCallback(
      private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        INSTANCE?.let { database ->
          scope.launch(Dispatchers.IO) {
            populateInitialStocks(database.filmStockDao())
          }
        }
      }

      suspend fun populateInitialStocks(dao: FilmStockDao) {
        val initialStocks = listOf(
          FilmStock(
            name = "Portra 400",
            brand = "Kodak",
            iso = 400,
            type = "Color Negative",
            reciprocityFactor = 1.22,
            description = "Iconic warm skin tones, fine grain, and exceptional exposure latitude.",
            badgeStyle = "PORTRA"
          ),
          FilmStock(
            name = "Tri-X 400",
            brand = "Kodak",
            iso = 400,
            type = "Black & White",
            reciprocityFactor = 1.30,
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
            reciprocityFactor = 1.24,
            description = "Warm golden nostalgic aesthetic, saturated colors, great daylight stock.",
            badgeStyle = "GOLD"
          ),
          FilmStock(
            name = "CineStill 800T",
            brand = "CineStill",
            iso = 800,
            type = "Tungsten Cine",
            reciprocityFactor = 1.20,
            description = "Motion picture cinema emulsion, unique red halation glow around point lights.",
            badgeStyle = "CINESTILL"
          ),
          FilmStock(
            name = "Velvia 50",
            brand = "Fujifilm",
            iso = 50,
            type = "Color Slide",
            reciprocityFactor = 1.15,
            description = "High saturation, ultra-fine grain reversal slide film for landscapes.",
            badgeStyle = "FUJI_VELVIA"
          ),
          FilmStock(
            name = "Superia X-TRA 400",
            brand = "Fujifilm",
            iso = 400,
            type = "Color Negative",
            reciprocityFactor = 1.25,
            description = "Punchy greens and accurate whites, great all-around snapshot film.",
            badgeStyle = "FUJI_SUPERIA"
          ),
          FilmStock(
            name = "Ektar 100",
            brand = "Kodak",
            iso = 100,
            type = "Color Negative",
            reciprocityFactor = 1.22,
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
        dao.insertAllStocks(initialStocks)
      }
    }
  }
}
