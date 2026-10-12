package com.example

import com.example.sensor.ExposureCalculator
import com.example.ui.components.remainingExposureMillis
import com.example.ui.sunny16.sunny16ShutterForIso
import com.example.ui.settings.isValidReciprocityExponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2

class SupportFlowTest {
  @Test
  fun customReciprocityProfileMustStayFiniteAtLongestMeteredExposure() {
    assertTrue(isValidReciprocityExponent(1.0))
    assertTrue(isValidReciprocityExponent(1.33))
    assertTrue(isValidReciprocityExponent(100.0))
    assertFalse(isValidReciprocityExponent(1_000.0))
    assertFalse(isValidReciprocityExponent(Double.POSITIVE_INFINITY))
    assertFalse(isValidReciprocityExponent(Double.NaN))
    assertFalse(isValidReciprocityExponent(0.5))
    assertFalse(isValidReciprocityExponent(null))
  }

  @Test
  fun sunny16UsesAvailableShutterSpeedsForEverySupportedIso() {
    for (iso in ExposureCalculator.STANDARD_ISOS) {
      val shutter = sunny16ShutterForIso(iso)
      val index = ExposureCalculator.SHUTTER_LABELS.indexOf(shutter)
      assertTrue("ISO " + iso + " must produce an available meter speed", index >= 0)
      assertTrue(abs(log2(ExposureCalculator.SHUTTER_SECONDS[index] * iso)) <= 0.5)
    }
    assertEquals("1/125", sunny16ShutterForIso(160))
  }

  @Test(expected = IllegalArgumentException::class)
  fun sunny16RejectsInvalidIso() {
    sunny16ShutterForIso(0)
  }

  @Test
  fun timerCatchesUpAfterDelayedUpdatesAndStopsAtZero() {
    assertEquals(9_000L, remainingExposureMillis(10_000L, 1_000L, 2_000L))
    assertEquals(1_000L, remainingExposureMillis(10_000L, 1_000L, 10_000L))
    assertEquals(0L, remainingExposureMillis(10_000L, 1_000L, 15_000L))
  }

  @Test
  fun timerResumesFromPausedRemainingTime() {
    val pausedRemaining = remainingExposureMillis(10_000L, 1_000L, 4_000L)
    assertEquals(5_000L, remainingExposureMillis(pausedRemaining, 50_000L, 52_000L))
  }
}
