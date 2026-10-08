package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "film_log_entries")
data class FilmLogEntry(
  @PrimaryKey(autoGenerate = true)
  val id: Int = 0,
  val rollId: String,
  val rollName: String,
  val frameNumber: Int,
  val filmStockName: String,
  val iso: Int,
  val format: String, // "35mm", "6x4.5", "6x6", "6x7"
  val apertureText: String, // e.g. "f/2.8"
  val shutterText: String, // e.g. "1/125s" or "4s"
  val rawSeconds: Double,
  val correctedSeconds: Double,
  val evValue: Double,
  val luxValue: Float,
  val notes: String = "",
  val timestamp: Long = System.currentTimeMillis()
)
