package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FilmStock
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmStockDao {
  @Query("SELECT * FROM film_stocks ORDER BY isCustom DESC, name ASC")
  fun getAllFilmStocks(): Flow<List<FilmStock>>

  @Query("SELECT * FROM film_stocks WHERE id = :id LIMIT 1")
  suspend fun getFilmStockById(id: Int): FilmStock?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFilmStock(stock: FilmStock): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAllStocks(stocks: List<FilmStock>)

  @Query("DELETE FROM film_stocks WHERE id = :id")
  suspend fun deleteFilmStockById(id: Int)
}
