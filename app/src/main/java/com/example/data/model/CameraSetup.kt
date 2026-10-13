package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sensor.ExposureCalculator

@Entity(tableName = "camera_setups")
data class CameraSetup(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val name: String,
  val format: String = CameraFormat.FORMAT_35MM.name,
  val focalLengthMm: Int = 50,
  val minAperture: Double = 1.0,
  val maxAperture: Double = 45.0,
  val shutterSpeeds: String = ExposureCalculator.SHUTTER_SECONDS.joinToString(","),
  val allowsBulb: Boolean = true
)
