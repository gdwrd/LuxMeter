package com.example.sensor

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ExposureCalculator {

  val APERTURE_VALUES = listOf(
    1.0, 1.2, 1.4, 1.8, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0, 32.0, 45.0
  )

  val APERTURE_LABELS = listOf(
    "f/1.0", "f/1.2", "f/1.4", "f/1.8", "f/2", "f/2.8", "f/4", "f/5.6",
    "f/8", "f/11", "f/16", "f/22", "f/32", "f/45"
  )

  val SHUTTER_SECONDS = listOf(
    1.0 / 8000, 1.0 / 4000, 1.0 / 2000, 1.0 / 1000, 1.0 / 500, 1.0 / 250,
    1.0 / 125, 1.0 / 60, 1.0 / 30, 1.0 / 15, 1.0 / 8, 1.0 / 4, 1.0 / 2,
    1.0, 2.0, 4.0, 8.0, 15.0, 30.0, 60.0
  )

  val SHUTTER_LABELS = listOf(
    "1/8000", "1/4000", "1/2000", "1/1000", "1/500", "1/250",
    "1/125", "1/60", "1/30", "1/15", "1/8", "1/4", "1/2",
    "1s", "2s", "4s", "8s", "15s", "30s", "60s"
  )

  val STANDARD_ISOS = listOf(
    25, 50, 64, 100, 160, 200, 400, 800, 1600, 3200, 6400
  )

  /**
   * Converts ambient illuminance in Lux to EV at ISO 100 using incident constant C = 250
   * EV100 = log2(Lux / 2.5)
   */
  fun luxToEv100(lux: Float, calibrationOffsetEv: Float = 0f): Double {
    val safeLux = lux.coerceAtLeast(0.01f)
    val rawEv = log2(safeLux / 2.5)
    return (rawEv + calibrationOffsetEv).coerceIn(-4.0, 20.0)
  }

  /**
   * Converts EV100 to EV for a specific ISO
   */
  fun ev100ToEvForIso(ev100: Double, iso: Int): Double {
    val isoOffset = log2(iso.toDouble() / 100.0)
    return ev100 + isoOffset
  }

  /**
   * Given an aperture and target EV at current ISO, computes closest shutter speed index.
   * t = N^2 / (2^EV_s)
   */
  fun calculateShutterIndexForAperture(apertureIndex: Int, evForIso: Double): Int {
    val n = APERTURE_VALUES[apertureIndex.coerceIn(0, APERTURE_VALUES.lastIndex)]
    val targetSeconds = (n * n) / 2.0.pow(evForIso)

    var closestIdx = 0
    var minDiff = Double.MAX_VALUE
    for (i in SHUTTER_SECONDS.indices) {
      val diff = abs(log2(SHUTTER_SECONDS[i]) - log2(targetSeconds))
      if (diff < minDiff) {
        minDiff = diff
        closestIdx = i
      }
    }
    return closestIdx
  }

  /**
   * Given a shutter speed and target EV at current ISO, computes closest aperture index.
   * N = sqrt(t * 2^EV_s)
   */
  fun calculateApertureIndexForShutter(shutterIndex: Int, evForIso: Double): Int {
    val t = SHUTTER_SECONDS[shutterIndex.coerceIn(0, SHUTTER_SECONDS.lastIndex)]
    val targetN = sqrt(t * 2.0.pow(evForIso))

    var closestIdx = 0
    var minDiff = Double.MAX_VALUE
    for (i in APERTURE_VALUES.indices) {
      val diff = abs(log2(APERTURE_VALUES[i]) - log2(targetN.coerceAtLeast(0.5)))
      if (diff < minDiff) {
        minDiff = diff
        closestIdx = i
      }
    }
    return closestIdx
  }

  /**
   * Computes exposure error / EV difference for Manual match mode:
   * Current EV dial = log2(N^2 / t)
   * EV delta = Current EV dial - target EV
   */
  fun calculateEvDelta(apertureIndex: Int, shutterIndex: Int, targetEvForIso: Double): Double {
    val n = APERTURE_VALUES[apertureIndex]
    val t = SHUTTER_SECONDS[shutterIndex]
    val currentSettingEv = log2((n * n) / t)
    return currentSettingEv - targetEvForIso
  }

  /**
   * Returns human-readable scene classification based on EV100
   */
  fun getSceneDescription(ev100: Double): String {
    return when {
      ev100 >= 15.5 -> "Bright Sun / Snow / Sand"
      ev100 >= 14.5 -> "Direct Full Sunlight (Sunny 16)"
      ev100 >= 13.5 -> "Hazy Sun / Slight Clouds"
      ev100 >= 11.5 -> "Bright Overcast / Cloudy Daylight"
      ev100 >= 9.5 -> "Open Shade / Heavy Overcast"
      ev100 >= 7.5 -> "Golden Hour / Sunset / Stage"
      ev100 >= 5.5 -> "Bright Office / Studio Interior"
      ev100 >= 3.5 -> "Cozy Indoor / Home Night"
      ev100 >= 1.5 -> "Night Street / Neon Lights"
      ev100 >= -1.0 -> "Night Cityscape / Dim Ambient"
      else -> "Deep Night / Starlight"
    }
  }

  /**
   * Formats exposure seconds nicely, e.g. "1/250s", "2s", "15.4s"
   */
  fun formatSeconds(sec: Double): String {
    return when {
      sec < 0.9 -> {
        val denom = (1.0 / sec).roundToInt()
        "1/${denom}s"
      }
      sec < 10.0 -> {
        String.format(java.util.Locale.US, "%.1fs", sec)
      }
      else -> {
        "${sec.roundToInt()}s"
      }
    }
  }
}
