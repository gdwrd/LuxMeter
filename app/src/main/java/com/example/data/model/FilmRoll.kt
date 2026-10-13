package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "film_rolls")
data class FilmRoll(
  @PrimaryKey val id: String,
  val name: String,
  val filmStockId: Int? = null,
  val filmStockName: String = "",
  val iso: Int = 400,
  val format: String = CameraFormat.FORMAT_35MM.name,
  val cameraSetupId: Int? = null,
  val focalLengthMm: Int = 50,
  val frameCapacity: Int = 36,
  val lastFrameNumber: Int = 0,
  val isFinished: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
)
