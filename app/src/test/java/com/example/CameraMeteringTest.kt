package com.example

import android.graphics.Matrix
import android.graphics.Rect
import com.example.sensor.CameraMetering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraMeteringTest {
  @Test
  fun `exposure uses capture settings and includes processed image gain`() {
    assertEquals(13.0, CameraMetering.ev100(7_812_500L, 100, 8f)!!, 0.00001)
    assertEquals(11.0, CameraMetering.ev100(7_812_500L, 400, 8f)!!, 0.00001)
    assertEquals(12.0, CameraMetering.ev100(7_812_500L, 100, 8f, 200)!!, 0.00001)
    assertEquals(-3.0, CameraMetering.ev100(1_000_000_000L, 3200, 2f)!!, 0.00001)
  }

  @Test
  fun `missing invalid metadata never becomes a plausible exposure`() {
    assertNull(CameraMetering.ev100(null, 100, 2f))
    assertNull(CameraMetering.ev100(1L, null, 2f))
    assertNull(CameraMetering.ev100(1L, 100, null))
    assertNull(CameraMetering.ev100(0L, 100, 2f))
    assertNull(CameraMetering.ev100(1L, 0, 2f))
    assertNull(CameraMetering.ev100(1L, 100, Float.NaN))
    assertNull(CameraMetering.ev100(1L, 100, 2f, 0))
  }

  @Test
  fun `portrait preview maps its rotated square to the sensor and excludes sides`() {
    val sensor = Rect(0, 0, 4000, 3000)
    val rotatedPreview = Matrix().apply {
      setValues(floatArrayOf(0f, -0.1f, 300f, 0.1f, 0f, -50f, 0f, 0f, 1f))
    }
    assertEquals(Rect(500, 0, 3500, 3000), CameraMetering.frameRegion(300, 300, rotatedPreview, sensor, sensor))
  }

  @Test
  fun `legacy zoom maps the same visible frame inside the reported sensor crop`() {
    val sensor = Rect(0, 0, 4000, 3000)
    val preview = Matrix().apply {
      setValues(floatArrayOf(0.1f, 0f, -50f, 0f, 0.1f, 0f, 0f, 0f, 1f))
    }
    assertEquals(
      Rect(1250, 750, 2750, 2250),
      CameraMetering.frameRegion(300, 300, preview, sensor, Rect(1000, 750, 3000, 2250))
    )
  }

  @Test
  fun `invalid transforms and changed metering regions are rejected`() {
    val sensor = Rect(0, 0, 4000, 3000)
    val singular = Matrix().apply { setScale(0f, 0f) }
    assertNull(CameraMetering.frameRegion(300, 300, singular, sensor, sensor))
    assertNull(CameraMetering.frameRegion(0, 300, Matrix(), sensor, sensor))
    assertTrue(CameraMetering.regionsMatch(Rect(500, 0, 3500, 3000), Rect(501, 0, 3500, 3000)))
    assertFalse(CameraMetering.regionsMatch(Rect(500, 0, 3500, 3000), sensor))
  }

  @Test
  fun `one pixel crop jitter does not restart exposure but a changed frame does`() {
    val requested = Rect(500, 0, 3500, 3000)
    assertTrue(CameraMetering.needsRegionUpdate(null, requested))
    assertFalse(CameraMetering.needsRegionUpdate(requested, Rect(501, 1, 3499, 2999)))
    assertTrue(CameraMetering.needsRegionUpdate(requested, Rect(600, 0, 3400, 3000)))
    assertEquals(Rect(500, 0, 3500, 3000), requested)
  }

  @Test
  fun `Pixel logical lens quantization matches but a different metered crop does not`() {
    val requested = Rect(501, 1, 3500, 2999)
    assertTrue(CameraMetering.regionsMatch(requested, Rect(498, 0, 3497, 2996)))
    assertFalse(CameraMetering.needsRegionUpdate(requested, Rect(498, 0, 3497, 2996)))
    assertFalse(CameraMetering.regionsMatch(requested, Rect(491, 1, 3490, 2999)))
    assertFalse(CameraMetering.regionsMatch(requested, Rect(501, 11, 3500, 3009)))
  }

  @Test
  fun `brief adjustment after a long live session does not immediately time out`() {
    val regionRequestedMs = 1_000L
    val lastValidatedReadingMs = 20_000L
    assertTrue(CameraMetering.unsettledDurationMs(20_180L, regionRequestedMs, lastValidatedReadingMs) < 3000L)
    assertTrue(CameraMetering.unsettledDurationMs(25_001L, regionRequestedMs, lastValidatedReadingMs) > 5000L)
    assertEquals(100L, CameraMetering.unsettledDurationMs(25_100L, 25_000L, lastValidatedReadingMs))
  }

  @Test
  fun `lens framing accounts for the rotated film crop without an assumed phone focal length`() {
    val landscape = CameraMetering.framingZoom(50.0, 4f, 6f, 4f, 1.5f, 0)!!
    val portrait = CameraMetering.framingZoom(50.0, 4f, 6f, 4f, 1.5f, 90)!!
    assertEquals(50f / 24f, landscape, 0.0001f)
    assertTrue(portrait < landscape)
    assertNull(CameraMetering.framingZoom(50.0, 0f, 6f, 4f, 1.5f, 0))
  }
}
