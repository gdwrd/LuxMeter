package com.example

import com.example.sensor.ExposureCalculator
import com.example.ui.components.parseLocalizedDecimal
import com.example.ui.components.parseReferenceSeconds
import com.example.ui.components.parseSetupShutters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SetupDialogInputTest {
  @Test fun `apertures accept local decimals but not grouping or trailing text`() {
    assertEquals(1.8, parseLocalizedDecimal(" 1,8 ", Locale.GERMANY)!!, 0.0)
    assertEquals(1.8, parseLocalizedDecimal("1.8", Locale.UK)!!, 0.0)
    assertNull(parseLocalizedDecimal("1.000", Locale.GERMANY))
    assertNull(parseLocalizedDecimal("1,000", Locale.UK))
    assertNull(parseLocalizedDecimal("1,8abc", Locale.GERMANY))
    assertNull(parseLocalizedDecimal("NaN", Locale.UK))
    assertNull(parseLocalizedDecimal("", Locale.UK))
  }

  @Test fun `camera shutter list accepts marked fractions and localized seconds`() {
    assertEquals(listOf(1.0 / 125, 0.5, 1.0), parseSetupShutters("1/125 0,5 1", Locale.GERMANY))
    assertEquals(listOf(1.0 / 125, 0.5, 1.0), parseSetupShutters("1/125, 0.5, 1", Locale.UK))
    assertNull(parseSetupShutters("1/0", Locale.UK))
    assertNull(parseSetupShutters("61", Locale.UK))
    assertNull(parseSetupShutters("1/125 bad", Locale.UK))
    assertNull(parseSetupShutters("1.000", Locale.GERMANY))
  }

  @Test fun `reference exposure accepts one local number or fraction within the timing limit`() {
    assertEquals(1.0 / 125, parseReferenceSeconds("1/125", Locale.UK)!!, 0.0)
    assertEquals(0.5, parseReferenceSeconds("0,5s", Locale.GERMANY)!!, 0.0)
    assertEquals(120.0, parseReferenceSeconds("120", Locale.UK)!!, 0.0)
    assertEquals(ExposureCalculator.MAX_EXPOSURE_SECONDS,
      parseReferenceSeconds(ExposureCalculator.MAX_EXPOSURE_SECONDS.toInt().toString(), Locale.UK)!!, 0.0)
    listOf("0", "-1", "1/0", "1/-2", "1/2/3", "1/125 1/60", "86401", "NaN", "", "1,000").forEach {
      assertNull(it, parseReferenceSeconds(it, Locale.UK))
    }
  }
}
