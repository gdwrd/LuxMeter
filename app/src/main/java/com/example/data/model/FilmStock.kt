package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Ignore
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
  val badgeStyle: String, // Film family used by the canister artwork; custom stocks use "CUSTOM".
  val isCustom: Boolean = false,
  @ColumnInfo(defaultValue = "1") val hasReciprocityProfile: Boolean = true
) {
  fun isoForPush(stops: Int): Int? {
    if (iso <= 0 || stops !in 0..4) return null
    return (iso.toLong() * (1L shl stops)).takeIf { it <= 102400L }?.toInt()
  }

  private fun profile(): String = if (isCustom) "custom" else "$brand/$name"

  @get:Ignore
  val correctionLabel: String
    get() = when {
      !hasReciprocityProfile -> "Unknown correction"
      isCustom -> "Your profile"
      profile() == "Kodak/ColorPlus 200" -> "Short-exposure estimate"
      profile() == "CineStill/50D" || profile() == "CineStill/400D" -> "Short-exposure guidance"
      profile() == "CineStill/CineStill 800T" -> "Estimate"
      profile() == "Kodak/Tri-X 400" -> "Manufacturer table · interpolation estimated"
      sourceUrl != null -> "Manufacturer guidance"
      else -> "Unknown correction"
    }

  @get:Ignore
  val sourceUrl: String?
    get() = when (profile()) {
      "Ilford/HP5 Plus 400", "Ilford/Delta 3200", "Ilford/FP4 Plus", "Ilford/Delta 100",
      "Ilford/Delta 400", "Ilford/Pan F Plus", "Ilford/XP2 Super", "Kentmere/Pan 100", "Kentmere/Pan 400" ->
        "https://www.ilfordphoto.com/wp/wp-content/uploads/2024/05/Reciprocity-Failure-Compensation-v2.pdf"
      "Kodak/Tri-X 400" -> "https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4017_TriX.pdf"
      "CineStill/CineStill 800T", "CineStill/50D", "CineStill/400D" -> "https://help.cinestillfilm.com/hc/en-us/articles/4407453578893-What-are-the-reciprocity-failure-details-for-CineStill-films"
      "Kodak/Portra 400" -> "https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4050_portra_400.pdf"
      "Kodak/Portra 160" -> "https://www.kodakprofessional.com/sites/default/files/2025-07/e4051.pdf"
      "Kodak/Portra 800" -> "https://www.kodakprofessional.com/sites/default/files/2025-07/e4040.pdf"
      "Kodak/UltraMax 400" -> "https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/E7023_max_400.pdf"
      "Kodak/ColorPlus 200" -> "https://kodakprofessional.com/sites/default/files/files/resources/Kodak%20Catalogue%20-%20Final%20-%20High%20Res%20%28Print%29.pdf"
      "Kodak/Ektachrome" -> "https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4000_ektachrome_100.pdf"
      "Kodak/T-MAX 100" -> "https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4016_TMax_100.pdf"
      "Kodak/T-MAX 400" -> "https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4043_TMax_400.pdf"
      "Kodak/Gold 200" -> "https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/E7022_Gold_200.pdf"
      "Kodak/Ektar 100" -> "https://www.kodakprofessional.com/sites/default/files/2025-07/e4046.pdf"
      "Fujifilm/Superia X-TRA 400" -> "https://asset.fujifilm.com/www/us/files/2025-06/8abba3dd9d004f44d1e9c7fdbdf5c520/films_superia-xtra400_datasheet_01.pdf"
      "Fujifilm/Velvia 50" -> "https://www.fujifilm.com.hk/products/professional_films/pdf/velvia_50_datasheet.pdf"
      "Fujifilm/Acros II" -> "https://asset.fujifilm.com/www/us/files/2020-08/73080443d0289b1a7b116add14a9bdfc/films_neopan100acros2_135_01_0.pdf"
      "Foma/Fomapan 100" -> "https://www.foma.cz/en/fomapan-100"
      else -> null
    }

  @get:Ignore
  val rangeText: String
    get() = when {
      !hasReciprocityProfile -> "No timed correction supplied. You can record the settings you choose."
      isCustom -> "Your t^$reciprocityFactor correction above ${reciprocityThresholdSec}s; the app limits timing to one day."
      else -> when (profile()) {
        "Ilford/HP5 Plus 400", "Ilford/Delta 3200" -> "No correction through 1s; manufacturer's power correction above 1s. App limit: one day."
        "Kodak/Tri-X 400" -> "Timed correction for 1/1000s–100s metered. Development changes may also be needed."
        "CineStill/CineStill 800T" -> "Approximate t^1.3 above 1s. Bracket exposures; this is not a validated time table."
        "Kodak/Portra 400", "Kodak/Gold 200", "Kodak/Ektar 100", "Kodak/Portra 160", "Kodak/Portra 800", "Kodak/UltraMax 400" ->
          "No correction from 1/10,000s to 1s. Test longer exposures; no timed correction is published."
        "Kodak/ColorPlus 200" -> "The app assumes ordinary exposure through 1s. Kodak supplies no reciprocity range; the source confirms ISO only. Longer timed correction is unavailable."
        "Kodak/Ektachrome" -> "No correction from 1/10,000s to 10s in daylight with normal processing. Longer exposures need color-filter guidance."
        "Kodak/T-MAX 100" -> "No correction from 1/1000s to 1/10s, between Kodak's published table points. Other exposures need its exposure and development table; longer timed correction is not included."
        "Kodak/T-MAX 400" -> "No correction from 1/10,000s to 1s, between Kodak's published table points. Longer timed correction is not included; check its exposure and development table."
        "Ilford/FP4 Plus", "Ilford/Delta 100", "Ilford/Delta 400", "Ilford/Pan F Plus", "Ilford/XP2 Super",
        "Kentmere/Pan 100", "Kentmere/Pan 400" -> "No correction through 1s. Longer timed correction is not included; consult the manufacturer's guidance."
        "CineStill/50D", "CineStill/400D" -> "Ordinary exposure through 1s, following CineStill's short-exposure guidance. Longer timed correction is not included; bracket and test exposures."
        "Foma/Fomapan 100" -> "No correction from 1/1000s to 1/2s. Longer timed correction is not included; check Foma's exposure table."
        "Fujifilm/Acros II" -> "No correction below 120s. At 120s and longer, consult Fujifilm's aperture-correction guidance; no timed correction is included."
        "Fujifilm/Superia X-TRA 400" -> "No correction from 1/4000s to 2s. Longer exposures need Fujifilm's aperture guidance."
        "Fujifilm/Velvia 50" -> "No correction from 1/4000s to 1s. Longer exposures need aperture and magenta-filter guidance."
        else -> "No published correction is linked to this film."
      }
    }

  /** Null means the exposure is outside the film's supported or explicitly assumed range. */
  fun calculateCorrectedExposure(rawSeconds: Double): Double? = exposure(rawSeconds, inverse = false)

  /** Converts an actual shutter duration back to the equivalent metered exposure. */
  fun calculateMeteredExposure(actualSeconds: Double): Double? = exposure(actualSeconds, inverse = true)

  private fun exposure(seconds: Double, inverse: Boolean): Double? {
    if (!hasReciprocityProfile) return null
    if (!seconds.isFinite() || seconds <= 0.0 || seconds > ExposureCalculator.MAX_EXPOSURE_SECONDS) return null
    val result = when (profile()) {
      "custom" -> powerExposure(seconds, inverse, reciprocityFactor, reciprocityThresholdSec)
      // Ilford's published Tc = Tm^P factors apply above one second.
      "Ilford/HP5 Plus 400" -> powerExposure(seconds, inverse, 1.31)
      "Ilford/Delta 3200" -> powerExposure(seconds, inverse, 1.33)
      "CineStill/CineStill 800T" -> powerExposure(seconds, inverse, 1.3)
      "Kodak/Tri-X 400" -> triXExposure(seconds, inverse)
      "Kodak/Portra 400", "Kodak/Gold 200", "Kodak/Ektar 100", "Kodak/Portra 160", "Kodak/Portra 800",
      "Kodak/UltraMax 400", "Kodak/T-MAX 400" -> seconds.takeIf { it in 0.0001..1.0 }
      // shortcut: ColorPlus has no published range, replace this labeled short-exposure assumption when Kodak supplies one.
      "Kodak/ColorPlus 200" -> seconds.takeIf { it <= 1.0 }
      "Kodak/Ektachrome" -> seconds.takeIf { it in 0.0001..10.0 }
      "Kodak/T-MAX 100" -> seconds.takeIf { it in 0.001..0.1 }
      "Ilford/FP4 Plus", "Ilford/Delta 100", "Ilford/Delta 400", "Ilford/Pan F Plus", "Ilford/XP2 Super",
      "Kentmere/Pan 100", "Kentmere/Pan 400", "CineStill/50D", "CineStill/400D" -> seconds.takeIf { it <= 1.0 }
      "Foma/Fomapan 100" -> seconds.takeIf { it in 0.001..0.5 }
      "Fujifilm/Acros II" -> seconds.takeIf { it < 120.0 }
      "Fujifilm/Superia X-TRA 400" -> seconds.takeIf { it in 0.00025..2.0 }
      "Fujifilm/Velvia 50" -> seconds.takeIf { it in 0.00025..1.0 }
      else -> null
    }
    return result?.takeIf { it.isFinite() && it > 0.0 && it <= ExposureCalculator.MAX_EXPOSURE_SECONDS }
  }

  fun reciprocityGuidance(rawSeconds: Double): String? {
    if (!hasReciprocityProfile) return "Reciprocity correction is unknown for this film. Record your chosen settings manually; no timed recommendation is available."
    if (!rawSeconds.isFinite() || rawSeconds <= 0.0 || rawSeconds > ExposureCalculator.MAX_EXPOSURE_SECONDS) {
      return "Exposure is outside the supported range."
    }
    return when (profile()) {
      "Kodak/Portra 400", "Kodak/Gold 200", "Kodak/Ektar 100", "Kodak/Portra 160", "Kodak/Portra 800", "Kodak/UltraMax 400" ->
        if (calculateCorrectedExposure(rawSeconds) == null) "Kodak provides no timed correction outside 1/10,000s–1s. Test and bracket exposures." else null
      "Kodak/ColorPlus 200" ->
        if (rawSeconds <= 1.0) "Short-exposure estimate: ordinary exposure is assumed through 1s; Kodak supplies no reciprocity range. The source confirms ISO only."
        else "Kodak supplies no reciprocity range for ColorPlus 200. No timed recommendation is available above the app's assumed 1s limit."
      "Kodak/Ektachrome", "Kodak/T-MAX 100", "Kodak/T-MAX 400", "Ilford/FP4 Plus", "Ilford/Delta 100",
      "Ilford/Delta 400", "Ilford/Pan F Plus", "Ilford/XP2 Super", "Kentmere/Pan 100", "Kentmere/Pan 400",
      "CineStill/50D", "CineStill/400D", "Foma/Fomapan 100", "Fujifilm/Acros II" ->
        if (calculateCorrectedExposure(rawSeconds) == null) rangeText else null
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
