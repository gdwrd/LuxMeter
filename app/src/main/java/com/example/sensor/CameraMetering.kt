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

  fun frameRegion(
    viewWidth: Int,
    viewHeight: Int,
    sensorToView: Matrix,
    activeArray: Rect,
    cropRegion: Rect
  ): Rect? {
    if (viewWidth <= 0 || viewHeight <= 0 || activeArray.isEmpty || cropRegion.isEmpty) return null
    val viewToSensor = Matrix()
    if (!sensorToView.invert(viewToSensor)) return null
    val frame = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
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
    // Logical-camera lens switches can quantize the same frame to slightly different sensor pixels.
    val horizontalTolerance = maxOf(2, ceil(expected.width() * 0.001).toInt())
    val verticalTolerance = maxOf(2, ceil(expected.height() * 0.001).toInt())
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
    phoneFocalMm: Float,
    sensorWidthMm: Float,
    sensorHeightMm: Float,
    frameAspect: Float,
    rotationDegrees: Int
  ): Float? {
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
