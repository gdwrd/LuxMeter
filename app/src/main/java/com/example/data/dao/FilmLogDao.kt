package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FilmLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmLogDao {
  @Query("SELECT * FROM film_log_entries ORDER BY timestamp DESC")
  fun getAllLogEntries(): Flow<List<FilmLogEntry>>

  @Query("SELECT * FROM film_log_entries WHERE rollId = :rollId ORDER BY frameNumber ASC")
  fun getEntriesForRoll(rollId: String): Flow<List<FilmLogEntry>>

  @Query("SELECT * FROM film_log_entries WHERE rollId = :rollId ORDER BY frameNumber DESC LIMIT 1")
  suspend fun getLatestEntryForRoll(rollId: String): FilmLogEntry?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLogEntry(entry: FilmLogEntry): Long

  @Query("DELETE FROM film_log_entries WHERE id = :id")
  suspend fun deleteEntryById(id: Int)

  @Query("DELETE FROM film_log_entries WHERE rollId = :rollId")
  suspend fun deleteRollEntries(rollId: String)
}
