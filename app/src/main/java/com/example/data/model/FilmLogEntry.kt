package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

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
  val timestamp: Long = System.currentTimeMillis(),
  @ColumnInfo(defaultValue = "''") val cameraName: String = "",
  @ColumnInfo(defaultValue = "0") val focalLengthMm: Int = 0,
  @ColumnInfo(defaultValue = "''") val meteringSource: String = "",
  @ColumnInfo(defaultValue = "0") val exposureCompensation: Double = 0.0,
  @ColumnInfo(defaultValue = "0") val filterStops: Double = 0.0,
  @ColumnInfo(defaultValue = "0") val calibrationEv: Float = 0f,
  @ColumnInfo(defaultValue = "''") val correctionKind: String = "",
  @ColumnInfo(defaultValue = "0") val isManual: Boolean = false,
  @ColumnInfo(defaultValue = "1") val hasMeterReading: Boolean = true,
  @ColumnInfo(defaultValue = "NULL") val boxIso: Int? = null,
  @ColumnInfo(defaultValue = "NULL") val photoFileName: String? = null
)
