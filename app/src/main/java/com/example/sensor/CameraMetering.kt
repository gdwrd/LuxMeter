package com.example.sensor

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.roundToInt

object CameraMetering {
  fun ev100(
    exposureTimeNs: Long?,
    sensitivity: Int?,
    aperture: Float?,
    postRawSensitivityBoost: Int = 100
  ): Double? {
    if (exposureTimeNs == null || exposureTimeNs <= 0L || sensitivity == null || sensitivity <= 0 ||
      aperture == null || !aperture.isFinite() || aperture <= 0f || postRawSensitivityBoost <= 0
    ) return null
    val effectiveIso = sensitivity.toDouble() * postRawSensitivityBoost / 100.0
    return log2(aperture.toDouble() * aperture / (exposureTimeNs / 1e9) * 100.0 / effectiveIso)
      .takeIf { it.isFinite() }
  }

  /**
   * True when auto-exposure has run out of both shutter time and sensitivity. Many phones still report
   * convergence there, but the scene is darker than the reported settings imply.
   */
  fun isAtLowLightLimit(exposureTimeNs: Long?, sensitivity: Int?, longestExposureNs: Long?, maxSensitivity: Int?): Boolean {
    if (exposureTimeNs == null || sensitivity == null || longestExposureNs == null || maxSensitivity == null ||
      exposureTimeNs <= 0L || sensitivity <= 0 || longestExposureNs <= 0L || maxSensitivity <= 0) return false
    return exposureTimeNs >= longestExposureNs * LIMIT_FRACTION && sensitivity >= maxSensitivity * LIMIT_FRACTION
  }

  /** The longest preview exposure: the sensor's limit, capped by the slowest frame rate auto-exposure may use. */
  fun longestPreviewExposureNs(maxExposureNs: Long?, slowestFps: Int?): Long? {
    val frameLimit = slowestFps?.takeIf { it > 0 }?.let { 1_000_000_000L / it }
    return listOfNotNull(maxExposureNs?.takeIf { it > 0L }, frameLimit).minOrNull()
  }

  /**
   * The frame-rate range that lets auto-exposure hold the shutter open longest, keeping the fastest upper bound.
   * Ranges that can reach a smooth preview rate are preferred so composing stays usable.
   */
  fun slowestFpsRange(ranges: List<Pair<Int, Int>>): Pair<Int, Int>? {
    val valid = ranges.filter { (lower, upper) -> lower > 0 && upper >= lower }
    return valid.filter { it.second >= SMOOTH_PREVIEW_FPS }.ifEmpty { valid }
      .minWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
  }

  private const val SMOOTH_PREVIEW_FPS = 24

  private const val LIMIT_FRACTION = 0.95

  fun selectedAreaAt(x: Float, y: Float): RectF? {
    if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return null
    val left = (x - 0.06f).coerceIn(0f, 0.88f)
    val top = (y - 0.06f).coerceIn(0f, 0.88f)
    return RectF(left, top, left + 0.12f, top + 0.12f)
  }

  fun frameRegion(
    viewWidth: Int,
    viewHeight: Int,
    sensorToView: Matrix,
    activeArray: Rect,
    cropRegion: Rect,
    normalizedArea: RectF? = null
  ): Rect? {
    if (viewWidth <= 0 || viewHeight <= 0 || activeArray.isEmpty || cropRegion.isEmpty) return null
    val area = normalizedArea ?: RectF(0f, 0f, 1f, 1f)
    if (area.isEmpty || !listOf(area.left, area.top, area.right, area.bottom).all { it.isFinite() } ||
      area.left < 0f || area.top < 0f || area.right > 1f || area.bottom > 1f) return null
    val viewToSensor = Matrix()
    if (!sensorToView.invert(viewToSensor)) return null
    val frame = RectF(area.left * viewWidth, area.top * viewHeight, area.right * viewWidth, area.bottom * viewHeight)
    viewToSensor.mapRect(frame)
    if (!listOf(frame.left, frame.top, frame.right, frame.bottom).all { it.isFinite() }) return null

    // CameraX's transform is unzoomed; cropRegion also covers legacy crop-based zoom.
    val left = cropRegion.left + (frame.left - activeArray.left) / activeArray.width() * cropRegion.width()
    val top = cropRegion.top + (frame.top - activeArray.top) / activeArray.height() * cropRegion.height()
    val right = cropRegion.left + (frame.right - activeArray.left) / activeArray.width() * cropRegion.width()
    val bottom = cropRegion.top + (frame.bottom - activeArray.top) / activeArray.height() * cropRegion.height()
    val region = Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
    return region.takeIf { it.intersect(cropRegion) && !it.isEmpty }
  }

  fun regionsMatch(expected: Rect, reported: Rect): Boolean {
    // Pixel lens switches plus framework clipping can shift the same frame by four sensor pixels.
    val horizontalTolerance = maxOf(4, ceil(expected.width() * 0.001).toInt())
    val verticalTolerance = maxOf(4, ceil(expected.height() * 0.001).toInt())
    return abs(expected.left - reported.left) <= horizontalTolerance &&
      abs(expected.right - reported.right) <= horizontalTolerance &&
      abs(expected.top - reported.top) <= verticalTolerance &&
      abs(expected.bottom - reported.bottom) <= verticalTolerance
  }

  fun needsRegionUpdate(requested: Rect?, calculated: Rect): Boolean =
    requested == null || !regionsMatch(requested, calculated)

  fun unsettledDurationMs(nowMs: Long, regionRequestedMs: Long, lastValidatedReadingMs: Long): Long =
    (nowMs - maxOf(regionRequestedMs, lastValidatedReadingMs)).coerceAtLeast(0L)

  fun framingZoom(
    filmEquivalentFocalMm: Double,
    availablePhoneFocalLengths: FloatArray?,
    sensorWidthMm: Float,
    sensorHeightMm: Float,
    frameAspect: Float,
    rotationDegrees: Int
  ): Float? {
    // shortcut: ambiguous lenses use phone framing until the active 1x lens can be verified.
    val phoneFocalMm = availablePhoneFocalLengths?.singleOrNull() ?: return null
    if (!filmEquivalentFocalMm.isFinite() || filmEquivalentFocalMm <= 0.0 ||
      !listOf(phoneFocalMm, sensorWidthMm, sensorHeightMm, frameAspect).all { it.isFinite() && it > 0f }
    ) return null
    val sensorAspect = if (rotationDegrees % 180 == 0) frameAspect else 1f / frameAspect
    val frameWidth = min(sensorWidthMm, sensorHeightMm * sensorAspect)
    val frameHeight = frameWidth / sensorAspect
    val phoneEquivalent = phoneFocalMm * hypot(36.0, 24.0) / hypot(frameWidth.toDouble(), frameHeight.toDouble())
    return (filmEquivalentFocalMm / phoneEquivalent).toFloat().takeIf { it.isFinite() && it > 0f }
  }
}
