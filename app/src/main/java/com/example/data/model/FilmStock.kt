package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sensor.ExposureCalculator
import kotlin.math.ln
import kotlin.math.pow

@Entity(tableName = "film_stocks")
data class FilmStock(
  @PrimaryKey(autoGenerate = true)
  val id: Int = 0,
  val name: String,
  val brand: String,
  val iso: Int,
  val type: String, // "Color Negative", "Black & White", "Color Slide", "Tungsten Cine"
  val reciprocityFactor: Double, // Used for custom power-law profiles; built-ins use published guidance.
  val reciprocityThresholdSec: Double = 1.0,
  val description: String,
  val badgeStyle: String, // "PORTRA", "TRI_X", "GOLD", "HP5", "CINESTILL", "FUJI_VELVIA", "FUJI_SUPERIA", "DELTA", "EKTAR", "CUSTOM"
  val isCustom: Boolean = false
) {
  private fun profile(): String = if (isCustom) "custom" else "$brand/$name"

  /** Null means the film's published guidance does not support a timed recommendation. */
  fun calculateCorrectedExposure(rawSeconds: Double): Double? = exposure(rawSeconds, inverse = false)

  /** Converts an actual shutter duration back to the equivalent metered exposure. */
  fun calculateMeteredExposure(actualSeconds: Double): Double? = exposure(actualSeconds, inverse = true)

  private fun exposure(seconds: Double, inverse: Boolean): Double? {
    if (!seconds.isFinite() || seconds <= 0.0 || seconds > ExposureCalculator.MAX_EXPOSURE_SECONDS) return null
    val result = when (profile()) {
      "custom" -> powerExposure(seconds, inverse, reciprocityFactor, reciprocityThresholdSec)
      // Ilford's published Tc = Tm^P factors apply above one second.
      "Ilford/HP5 Plus 400" -> powerExposure(seconds, inverse, 1.31)
      "Ilford/Delta 3200" -> powerExposure(seconds, inverse, 1.33)
      "CineStill/CineStill 800T" -> powerExposure(seconds, inverse, 1.3)
      "Kodak/Tri-X 400" -> triXExposure(seconds, inverse)
      "Kodak/Portra 400", "Kodak/Gold 200", "Kodak/Ektar 100" -> seconds.takeIf { it in 0.0001..1.0 }
      "Fujifilm/Superia X-TRA 400" -> seconds.takeIf { it in 0.00025..2.0 }
      "Fujifilm/Velvia 50" -> seconds.takeIf { it in 0.00025..1.0 }
      else -> null
    }
    return result?.takeIf { it.isFinite() && it > 0.0 && it <= ExposureCalculator.MAX_EXPOSURE_SECONDS }
  }

  fun reciprocityGuidance(rawSeconds: Double): String? {
    if (!rawSeconds.isFinite() || rawSeconds <= 0.0 || rawSeconds > ExposureCalculator.MAX_EXPOSURE_SECONDS) {
      return "Exposure is outside the supported range."
    }
    return when (profile()) {
      "Kodak/Portra 400", "Kodak/Gold 200", "Kodak/Ektar 100" ->
        if (calculateCorrectedExposure(rawSeconds) == null) "Kodak provides no timed correction outside 1/10,000s–1s. Test and bracket exposures." else null
      "Fujifilm/Superia X-TRA 400" ->
        if (calculateCorrectedExposure(rawSeconds) == null) "Outside Fujifilm's uncorrected 1/4000s–2s range. Use its aperture correction table; no timed correction is supplied." else null
      "Fujifilm/Velvia 50" ->
        if (calculateCorrectedExposure(rawSeconds) == null) "Outside Fujifilm's uncorrected 1/4000s–1s range. Long exposures need its aperture and magenta-filter corrections; 64s is not recommended." else null
      "CineStill/CineStill 800T" ->
        if (calculateCorrectedExposure(rawSeconds) == null) "The estimated exposure exceeds the supported one-day limit."
        else if (rawSeconds > 1.0) "Estimate using CineStill's t^1.3 guideline. Bracket exposures; color shifts are possible." else null
      "Kodak/Tri-X 400" -> when {
        calculateCorrectedExposure(rawSeconds) == null -> "Outside Kodak's supported timed-correction range (1/1000s–100s metered). Check its exposure and development table."
        rawSeconds == 0.001 -> "Kodak specifies 10% more development at 1/1000s. No exposure correction is needed."
        rawSeconds > 0.1 -> "Based on Kodak's time table; values between published points are estimates. Development adjustment is also required."
        else -> null
      }
      else -> if (calculateCorrectedExposure(rawSeconds) == null) "No supported timed correction for this film and exposure." else null
    }
  }

  private fun powerExposure(seconds: Double, inverse: Boolean, factor: Double, threshold: Double = 1.0): Double? {
    if (!factor.isFinite() || factor < 1.0 || !threshold.isFinite() || threshold < 1.0) return null
    if (seconds <= threshold) return seconds
    return seconds.pow(if (inverse) 1.0 / factor else factor)
      .takeIf { !inverse || it > threshold }
  }

  private fun triXExposure(seconds: Double, inverse: Boolean): Double? {
    // Kodak F-4017: metered 0.1/1/10/100s -> actual 0.1/2/50/1200s.
    val metered = doubleArrayOf(0.1, 1.0, 10.0, 100.0)
    val corrected = doubleArrayOf(0.1, 2.0, 50.0, 1200.0)
    val input = if (inverse) corrected else metered
    val output = if (inverse) metered else corrected
    if (seconds < 0.001 || seconds > input.last()) return null
    if (seconds <= input.first()) return seconds
    for (index in 1..input.lastIndex) {
      if (seconds <= input[index]) {
        val slope = ln(output[index] / output[index - 1]) / ln(input[index] / input[index - 1])
        return output[index - 1] * (seconds / input[index - 1]).pow(slope)
      }
    }
    return null
  }
}
