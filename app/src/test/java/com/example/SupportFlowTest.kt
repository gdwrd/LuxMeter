package com.example

import com.example.sensor.ExposureCalculator
import com.example.ui.components.remainingExposureMillis
import com.example.ui.sunny16.sunny16ShutterForIso
import com.example.ui.components.isValidReciprocityExponent
import com.example.ui.components.parseReciprocityExponent
import com.example.ui.components.createUnlistedFilm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2
import java.util.Locale

class SupportFlowTest {
  @Test
  fun customReciprocityProfileMustStayWithinOneDayAtLongestMeteredExposure() {
    assertTrue(isValidReciprocityExponent(1.0))
    assertTrue(isValidReciprocityExponent(1.33))
    assertTrue(isValidReciprocityExponent(2.5))
    assertFalse(isValidReciprocityExponent(3.0))
    assertFalse(isValidReciprocityExponent(100.0))
    assertFalse(isValidReciprocityExponent(1_000.0))
    assertFalse(isValidReciprocityExponent(Double.POSITIVE_INFINITY))
    assertFalse(isValidReciprocityExponent(Double.NaN))
    assertFalse(isValidReciprocityExponent(0.5))
    assertFalse(isValidReciprocityExponent(null))
  }

  @Test
  fun reciprocityInputAcceptsLocalDecimalsAndRejectsPartialOrGroupedNumbers() {
    assertEquals(1.31, parseReciprocityExponent("1,31", Locale.GERMANY)!!, 0.000001)
    assertEquals(1.31, parseReciprocityExponent("1.31", Locale.US)!!, 0.000001)
    assertEquals(1.31, parseReciprocityExponent("١٫٣١", Locale.forLanguageTag("ar"))!!, 0.000001)
    assertNull(parseReciprocityExponent("1.31", Locale.GERMANY))
    assertNull(parseReciprocityExponent("1,310", Locale.US))
    assertNull(parseReciprocityExponent("1,31 extra", Locale.GERMANY))
    assertNull(parseReciprocityExponent("NaN", Locale.US))
    assertNull(parseReciprocityExponent("", Locale.US))
  }

  @Test
  fun unlistedFilmDoesNotInventAReciprocityProfile() {
    val film = createUnlistedFilm("Test film", "Test brand", 125, "Black & White", false, null)!!
    assertEquals("Black & White", film.type)
    assertEquals(125, film.iso)
    assertFalse(film.hasReciprocityProfile)
    assertEquals(1.0, film.reciprocityFactor, 0.0)
    assertNull(film.calculateCorrectedExposure(10.0))
  }

  @Test
  fun testedCustomProfileRequiresAValidExponent() {
    assertNull(createUnlistedFilm("Test", "", 125, "Black & White", true, null))
    assertNull(createUnlistedFilm("Test", "", 125, "Black & White", true, 3.0))
    assertNull(createUnlistedFilm("Test", "", 0, "Black & White", false, null))
    val film = createUnlistedFilm("Test", "", 125, "Black & White", true, 1.31)!!
    assertTrue(film.hasReciprocityProfile)
    assertEquals(20.4, film.calculateCorrectedExposure(10.0)!!, 0.1)
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
