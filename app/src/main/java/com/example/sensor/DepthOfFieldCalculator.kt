package com.example.sensor

import com.example.data.model.CameraFormat
import kotlin.math.roundToInt

data class DofResult(
  val nearMeters: Double,
  val farMeters: Double, // Double.POSITIVE_INFINITY if infinity
  val totalDepthMeters: Double,
  val hyperfocalMeters: Double,
  val isFarInfinity: Boolean
)

object DepthOfFieldCalculator {

  /**
   * Circle of Confusion (CoC) in millimeters based on sensor/film gate size:
   * 35mm: ~0.029mm
   * 6x4.5: ~0.045mm
   * 6x6: ~0.053mm
   * 6x7: ~0.060mm
   */
  fun getCircleOfConfusionMm(format: CameraFormat): Double {
    return when (format) {
      CameraFormat.FORMAT_35MM -> 0.029
      CameraFormat.MEDIUM_645 -> 0.045
      CameraFormat.MEDIUM_66 -> 0.053
      CameraFormat.MEDIUM_67 -> 0.060
    }
  }

  /**
   * Standard typical lens focal lengths for each format
   */
  fun getCommonFocalLengths(format: CameraFormat): List<Int> {
    return when (format) {
      CameraFormat.FORMAT_35MM -> listOf(24, 28, 35, 50, 85, 105, 135)
      CameraFormat.MEDIUM_645 -> listOf(35, 45, 55, 75, 80, 110, 150)
      CameraFormat.MEDIUM_66 -> listOf(40, 50, 60, 80, 100, 120, 150) // Hasselblad / TLR standard
      CameraFormat.MEDIUM_67 -> listOf(45, 55, 75, 90, 105, 150, 200) // Pentax 67 / RB67
    }
  }

  fun getDefaultFocalLength(format: CameraFormat): Int {
    return when (format) {
      CameraFormat.FORMAT_35MM -> 50
      CameraFormat.MEDIUM_645 -> 75
      CameraFormat.MEDIUM_66 -> 80
      CameraFormat.MEDIUM_67 -> 90
    }
  }

  /**
   * Crop factor relative to 35mm diagonal (43.3mm)
   */
  fun getCropFactor(format: CameraFormat): Double {
    return when (format) {
      CameraFormat.FORMAT_35MM -> 1.0
      CameraFormat.MEDIUM_645 -> 0.62
      CameraFormat.MEDIUM_66 -> 0.55
      CameraFormat.MEDIUM_67 -> 0.50
    }
  }

  /**
   * Equivalent aperture for depth of field and background separation relative to 35mm.
   * Medium format lenses provide much shallower depth of field at the same angle of view.
   * e.g., f/2.8 on 6x6 provides the depth of field of f/1.5 on 35mm!
   */
  fun getEquivalent35mmAperture(apertureValue: Double, format: CameraFormat): Double {
    return apertureValue * getCropFactor(format)
  }

  /**
   * Returns how many stops shallower the depth of field is compared to 35mm
   */
  fun getDofStopsAdvantage(format: CameraFormat): Double {
    val crop = getCropFactor(format)
    return -2.0 * kotlin.math.log2(crop)
  }

  fun getApertureEquivalenceSummary(apertureValue: Double, format: CameraFormat): String {
    if (format == CameraFormat.FORMAT_35MM) {
      return "Full Frame baseline (1.0x)"
    }
    val eqAp = getEquivalent35mmAperture(apertureValue, format)
    val stops = getDofStopsAdvantage(format)
    return String.format(
      java.util.Locale.US,
      "35mm DoF Eq: f/%.1f (%.1f stops shallower blur)",
      eqAp,
      stops
    )
  }

  /**
   * Diffraction and optical characteristics for the format
   */
  fun getApertureRecommendation(apertureValue: Double, format: CameraFormat): String {
    return if (format == CameraFormat.FORMAT_35MM) {
      when {
        apertureValue <= 1.8 -> "Shallow DoF • Best for portraits & low light"
        apertureValue <= 8.0 -> "Peak sharpness zone for 35mm primes"
        apertureValue <= 11.0 -> "Great landscape depth"
        else -> "Caution: Diffraction softens fine grain past f/11 on 35mm"
      }
    } else {
      when {
        apertureValue <= 2.8 -> "Ultra-shallow medium format look (3D pop)"
        apertureValue <= 11.0 -> "Classic medium format portrait sharpness"
        apertureValue <= 22.0 -> "Safe stopping down: giant negative resists diffraction"
        else -> "Maximum landscape depth of field on 120 film"
      }
    }
  }

  /**
   * Calculates Depth of Field parameters.
   * @param focalLengthMm lens focal length in mm (e.g. 50)
   * @param apertureValue f-number (e.g. 5.6)
   * @param focusDistanceMeters distance to subject in meters (e.g. 2.5)
   * @param format camera format to obtain correct Circle of Confusion
   */
  fun calculate(
    focalLengthMm: Double,
    apertureValue: Double,
    focusDistanceMeters: Double,
    format: CameraFormat
  ): DofResult {
    val cocMm = getCircleOfConfusionMm(format)
    val fMm = focalLengthMm.coerceAtLeast(10.0)
    val n = apertureValue.coerceAtLeast(1.0)
    val dMm = (focusDistanceMeters * 1000.0).coerceAtLeast(100.0)

    // Hyperfocal distance H = (f^2 / (N * c)) + f in mm
    val hMm = ((fMm * fMm) / (n * cocMm)) + fMm
    val hMeters = hMm / 1000.0

    // Near limit: D_near = (H * d) / (H + (d - f)) in mm
    val nearMm = (hMm * dMm) / (hMm + (dMm - fMm))
    val nearMeters = (nearMm / 1000.0).coerceAtLeast(0.05)

    // Far limit: D_far = (H * d) / (H - (d - f)) in mm
    val denomFar = hMm - (dMm - fMm)
    val isInfinity = denomFar <= 0.0 || dMm >= hMm

    val farMeters = if (isInfinity) {
      Double.POSITIVE_INFINITY
    } else {
      (hMm * dMm) / denomFar / 1000.0
    }

    val totalDepth = if (isInfinity) {
      Double.POSITIVE_INFINITY
    } else {
      (farMeters - nearMeters).coerceAtLeast(0.0)
    }

    return DofResult(
      nearMeters = nearMeters,
      farMeters = farMeters,
      totalDepthMeters = totalDepth,
      hyperfocalMeters = hMeters,
      isFarInfinity = isInfinity
    )
  }

  fun formatDistance(meters: Double): String {
    return if (meters.isInfinite() || meters > 999.0) {
      "∞"
    } else if (meters < 1.0) {
      val cm = (meters * 100.0).roundToInt()
      "${cm}cm"
    } else if (meters < 10.0) {
      String.format(java.util.Locale.US, "%.2fm", meters)
    } else {
      String.format(java.util.Locale.US, "%.1fm", meters)
    }
  }

  fun metersToFeet(meters: Double): String {
    if (meters.isInfinite() || meters > 999.0) return "∞"
    val feet = meters * 3.28084
    return String.format(java.util.Locale.US, "%.1fft", feet)
  }
}
