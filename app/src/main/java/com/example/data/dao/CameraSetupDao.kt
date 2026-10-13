package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CameraSetup
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraSetupDao {
  @Query("SELECT * FROM camera_setups ORDER BY name COLLATE NOCASE ASC, id ASC")
  fun getAllCameraSetups(): Flow<List<CameraSetup>>

  @Query("SELECT * FROM camera_setups WHERE id = :id LIMIT 1")
  suspend fun getCameraSetup(id: Int): CameraSetup?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveCameraSetup(setup: CameraSetup): Long
}
