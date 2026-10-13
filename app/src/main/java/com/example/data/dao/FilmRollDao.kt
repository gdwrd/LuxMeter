package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FilmRoll
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmRollDao {
  @Query("SELECT * FROM film_rolls ORDER BY isFinished ASC, createdAt DESC, id ASC")
  fun getAllRolls(): Flow<List<FilmRoll>>

  @Query("SELECT * FROM film_rolls WHERE id = :id LIMIT 1")
  suspend fun getRoll(id: String): FilmRoll?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveRoll(roll: FilmRoll)

  @Query("DELETE FROM film_rolls WHERE id = :id")
  suspend fun deleteRoll(id: String)
}
