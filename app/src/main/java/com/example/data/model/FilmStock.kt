package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "film_stocks")
data class FilmStock(
  @PrimaryKey(autoGenerate = true)
  val id: Int = 0,
  val name: String,
  val brand: String,
  val iso: Int,
  val type: String, // "Color Negative", "Black & White", "Color Slide", "Tungsten Cine"
  val reciprocityFactor: Double, // Exponent p in tc = t^p (standard ~ 1.20 - 1.33)
  val reciprocityThresholdSec: Double = 1.0,
  val description: String,
  val badgeStyle: String, // "PORTRA", "TRI_X", "GOLD", "HP5", "CINESTILL", "FUJI_VELVIA", "FUJI_SUPERIA", "DELTA", "EKTAR", "CUSTOM"
  val isCustom: Boolean = false
) {
  /**
   * Calculates the reciprocity failure exposure compensation time in seconds.
   * If exposure is 1s or less, film follows normal reciprocity.
   * For longer exposures, the Schwarzschild effect requires longer open shutter time.
   */
  fun calculateCorrectedExposure(rawSeconds: Double): Double {
    if (rawSeconds <= reciprocityThresholdSec) return rawSeconds
    // For Ilford / Kodak, empirical formula: t_c = t^p
    // Or for CineStill: 1.2 exponent
    return Math.pow(rawSeconds, reciprocityFactor)
  }
}
