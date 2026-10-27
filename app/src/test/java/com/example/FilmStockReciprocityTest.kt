package com.example

import com.example.data.model.FilmStock
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class FilmStockReciprocityTest {
  private fun film(name: String, brand: String, factor: Double = 1.22, custom: Boolean = false) = FilmStock(
    name = name, brand = brand, iso = 400, type = "Test", reciprocityFactor = factor,
    description = "Test", badgeStyle = "CUSTOM", isCustom = custom
  )

  @Test fun triXUsesKodakPublishedTimesAndInvertibleInterpolation() {
    val film = film("Tri-X 400", "Kodak")
    for ((metered, actual) in listOf(0.1 to 0.1, 1.0 to 2.0, 10.0 to 50.0, 100.0 to 1200.0)) {
      assertEquals(actual, film.calculateCorrectedExposure(metered)!!, 0.000001)
      assertEquals(metered, film.calculateMeteredExposure(actual)!!, 0.000001)
    }
    for (metered in listOf(0.001, 0.5, 4.0, 30.0, 60.0)) {
      assertEquals(metered, film.calculateMeteredExposure(film.calculateCorrectedExposure(metered)!!)!!, 0.000001)
    }
    assertNull(film.calculateCorrectedExposure(0.000125))
    assertNull(film.calculateCorrectedExposure(100.01))
    assertNull(film.calculateMeteredExposure(1200.01))
    assertTrue(film.reciprocityGuidance(4.0)!!.contains("estimates"))
  }

  @Test fun ilfordFactorsAndCineStillEstimateHaveInverseCorrections() {
    val hp5 = film("HP5 Plus 400", "Ilford")
    val delta = film("Delta 3200", "Ilford")
    assertEquals(20.4, hp5.calculateCorrectedExposure(10.0)!!, 0.1)
    assertEquals(21.4, delta.calculateCorrectedExposure(10.0)!!, 0.1)
    assertEquals(13.4144687, hp5.calculateMeteredExposure(30.0)!!, 0.000001)
    val cineStill = film("CineStill 800T", "CineStill")
    assertEquals(83.225733, cineStill.calculateCorrectedExposure(30.0)!!, 0.00001)
    assertTrue(cineStill.reciprocityGuidance(30.0)!!.contains("Estimate"))
  }

  @Test fun colorFilmsDoNotInventTimedCorrectionsBeyondPublishedRanges() {
    for (name in listOf("Portra 400", "Gold 200", "Ektar 100")) {
      val film = film(name, "Kodak")
      assertEquals(1.0, film.calculateCorrectedExposure(1.0)!!, 0.0)
      assertNull(film.calculateCorrectedExposure(2.0))
      assertNull(film.calculateMeteredExposure(2.0))
      assertNotNull(film.reciprocityGuidance(2.0))
    }
    val superia = film("Superia X-TRA 400", "Fujifilm")
    assertEquals(2.0, superia.calculateCorrectedExposure(2.0)!!, 0.0)
    assertNull(superia.calculateCorrectedExposure(4.0))
    assertNull(superia.calculateCorrectedExposure(1.0 / 8000))
    val velvia = film("Velvia 50", "Fujifilm")
    assertEquals(1.0, velvia.calculateCorrectedExposure(1.0)!!, 0.0)
    assertNull(velvia.calculateCorrectedExposure(2.0))
    assertTrue(velvia.reciprocityGuidance(4.0)!!.contains("magenta"))
  }

  @Test fun customProfilesKeepTheirOwnModelAndRejectUnrepresentableResults() {
    val custom = film("Portra 400", "Kodak", factor = 1.5, custom = true)
    assertEquals(8.0, custom.calculateCorrectedExposure(4.0)!!, 0.000001)
    assertEquals(4.0, custom.calculateMeteredExposure(8.0)!!, 0.000001)
    assertNull(custom.calculateCorrectedExposure(Double.NaN))
    assertNull(custom.calculateCorrectedExposure(0.0))
    assertNull(custom.calculateCorrectedExposure(-1.0))
    assertNull(custom.calculateCorrectedExposure(86_401.0))
    assertNull(custom.copy(reciprocityFactor = 100.0).calculateCorrectedExposure(60.0))
    assertNull(custom.copy(reciprocityFactor = Double.NaN).calculateCorrectedExposure(1.0))
    assertNull(custom.copy(reciprocityFactor = 0.5).calculateMeteredExposure(4.0))
    val delayed = custom.copy(reciprocityFactor = 2.0, reciprocityThresholdSec = 2.0)
    // The correction starts from the threshold, so it has no jump there and every actual time maps back.
    assertEquals(2.0, delayed.calculateCorrectedExposure(2.0)!!, 0.000001)
    assertEquals(2.0, delayed.calculateCorrectedExposure(2.000001)!!, 0.0001)
    assertEquals(4.5, delayed.calculateCorrectedExposure(3.0)!!, 0.000001)
    assertEquals(2.0 * kotlin.math.sqrt(2.0), delayed.calculateMeteredExposure(4.0)!!, 0.000001)
    assertEquals(5.0, delayed.calculateCorrectedExposure(delayed.calculateMeteredExposure(5.0)!!)!!, 0.000001)
  }

  @Test fun unknownCustomFilmsDoNotInventCorrectionsOrManufacturerSources() {
    val unknown = film("My film", "My lab", custom = true).copy(hasReciprocityProfile = false)
    assertNull(unknown.calculateCorrectedExposure(0.5))
    assertNull(unknown.calculateMeteredExposure(30.0))
    assertEquals("Unknown correction", unknown.correctionLabel)
    assertNull(unknown.sourceUrl)
    assertTrue(unknown.reciprocityGuidance(1.0)!!.contains("unknown"))
    val tested = unknown.copy(hasReciprocityProfile = true, reciprocityFactor = 1.5)
    assertEquals("Your profile", tested.correctionLabel)
    assertEquals(8.0, tested.calculateCorrectedExposure(4.0)!!, 0.000001)
  }

  @Test fun publishedProfilesExposeTheirSourcesAndLimitsWithoutCallingEstimatesVerified() {
    val hp5 = film("HP5 Plus 400", "Ilford")
    assertEquals("Manufacturer guidance", hp5.correctionLabel)
    assertTrue(hp5.sourceUrl!!.startsWith("https://www.ilfordphoto.com/"))
    val cineStill = film("CineStill 800T", "CineStill")
    assertEquals("Estimate", cineStill.correctionLabel)
    assertTrue(cineStill.rangeText.contains("not a validated"))
    val velvia = film("Velvia 50", "Fujifilm")
    assertTrue(velvia.rangeText.contains("magenta"))
    assertNotNull(velvia.sourceUrl)
  }

  @Test fun ilfordFilmsUseTheFactorsFromTheLinkedSheet() {
    val factors = listOf(
      Triple("Ilford", "Pan F Plus", 1.33), Triple("Ilford", "FP4 Plus", 1.26),
      Triple("Ilford", "Delta 100", 1.26), Triple("Ilford", "Delta 400", 1.41),
      Triple("Ilford", "XP2 Super", 1.31), Triple("Kentmere", "Pan 100", 1.26),
      Triple("Kentmere", "Pan 400", 1.30)
    )
    for ((brand, name, factor) in factors) {
      val stock = film(name, brand)
      assertEquals("$brand $name", 1.0 / 125.0, stock.calculateCorrectedExposure(1.0 / 125.0)!!, 0.0)
      assertEquals("$brand $name", 1.0, stock.calculateCorrectedExposure(1.0)!!, 0.0)
      assertEquals("$brand $name", Math.pow(10.0, factor), stock.calculateCorrectedExposure(10.0)!!, 0.000001)
      assertEquals("$brand $name", 10.0, stock.calculateMeteredExposure(Math.pow(10.0, factor))!!, 0.000001)
      assertNull("$brand $name", stock.reciprocityGuidance(10.0))
      assertEquals("$brand $name", "Manufacturer guidance", stock.correctionLabel)
      assertTrue("$brand $name", stock.rangeText.contains("power correction above 1s"))
    }
  }

  @Test fun newPresetsSupportOrdinaryMeteringWithoutInventingLongExposureCurves() {
    val additions = listOf(
      Triple("Kodak", "Portra 160", 1.0), Triple("Kodak", "Portra 800", 1.0),
      Triple("Kodak", "UltraMax 400", 1.0), Triple("Kodak", "ColorPlus 200", 1.0),
      Triple("Kodak", "Ektachrome", 10.0), Triple("Kodak", "T-MAX 100", 0.1),
      Triple("Kodak", "T-MAX 400", 1.0), Triple("CineStill", "50D", 1.0), Triple("CineStill", "400D", 1.0),
      Triple("Foma", "Fomapan 100", 0.5), Triple("Fujifilm", "Acros II", 119.0)
    )
    for ((brand, name, longestSupported) in additions) {
      val stock = film(name, brand)
      val normal = 1.0 / 125.0
      assertEquals("$brand $name", normal, stock.calculateCorrectedExposure(normal)!!, 0.0)
      assertEquals("$brand $name", normal, stock.calculateMeteredExposure(normal)!!, 0.0)
      assertEquals(longestSupported, stock.calculateCorrectedExposure(longestSupported)!!, 0.0)
      val unsupported = if (name == "Acros II") 120.0 else longestSupported + 0.01
      assertNull("$brand $name", stock.calculateCorrectedExposure(unsupported))
      assertNull("$brand $name", stock.calculateMeteredExposure(unsupported))
      assertNotNull("$brand $name", stock.reciprocityGuidance(unsupported))
      assertNotNull("$brand $name", stock.sourceUrl)
      assertNull(stock.calculateCorrectedExposure(0.0))
      assertNull(stock.calculateCorrectedExposure(Double.NaN))
    }
    assertNull(film("T-MAX 100", "Kodak").calculateCorrectedExposure(1.0 / 2000.0))
    assertNull(film("Fomapan 100", "Foma").calculateCorrectedExposure(1.0 / 2000.0))
    assertEquals(119.999, film("Acros II", "Fujifilm").calculateCorrectedExposure(119.999)!!, 0.0)
    assertTrue(film("Ektachrome", "Kodak").rangeText.contains("daylight"))
  }

  @Test fun colorPlusLabelsShortExposureAssumptionAndLinksOnlyIsoEvidence() {
    val stock = film("ColorPlus 200", "Kodak")
    assertEquals("Short-exposure estimate", stock.correctionLabel)
    assertTrue(stock.rangeText.contains("assumes"))
    assertTrue(stock.rangeText.contains("no reciprocity range"))
    assertTrue(stock.rangeText.contains("ISO only"))
    assertTrue(stock.sourceUrl!!.contains("Catalogue"))
    assertNotNull(stock.reciprocityGuidance(1.0 / 125.0))
    assertNull(stock.calculateCorrectedExposure(1.01))
    assertTrue(stock.reciprocityGuidance(1.01)!!.contains("No timed recommendation"))
  }

  @Test fun addedManufacturerPowersRespectRangesAndRemainInvertible() {
    val powers = listOf(
      Triple("Ilford", "SFX 200", 1.43), Triple("Ilford", "Ortho Plus", 1.25),
      Triple("Kentmere", "Pan 200", 1.26), Triple("Harman", "Phoenix II 200", 1.31),
      Triple("Harman", "Switch Azure 125", 1.31)
    )
    for ((brand, name, exponent) in powers) {
      val stock = film(name, brand)
      assertEquals("Manufacturer guidance", stock.correctionLabel)
      assertNotNull(stock.sourceUrl)
      for (metered in listOf(0.0001, 0.5, 1.0, 10.0, 100.0)) {
        val expected = if (metered <= 1.0) metered else metered.pow(exponent)
        assertEquals("$brand $name", expected, stock.calculateCorrectedExposure(metered)!!, 0.000001)
        assertEquals(metered, stock.calculateMeteredExposure(expected)!!, 0.000001)
      }
      if (brand != "Ilford") {
        assertNull(stock.calculateCorrectedExposure(0.00001))
        assertNull(stock.calculateMeteredExposure(0.00001))
        assertNotNull(stock.reciprocityGuidance(0.00001))
      }
      assertNull(stock.calculateCorrectedExposure(86_400.0))
    }
  }

  @Test fun addedFomaProfilesKeepPublishedOrdinaryExposureBoundaries() {
    for (name in listOf("Fomapan 200", "Fomapan 400", "Retropan 320", "Ortho 400")) {
      val stock = film(name, "Foma")
      for (seconds in listOf(0.001, 1.0 / 125.0, 0.5)) {
        assertEquals(seconds, stock.calculateCorrectedExposure(seconds)!!, 0.0)
        assertEquals(seconds, stock.calculateMeteredExposure(seconds)!!, 0.0)
      }
      for (seconds in listOf(0.0009, 0.5001, 1.0, 100.0)) {
        assertNull(stock.calculateCorrectedExposure(seconds))
        assertNull(stock.calculateMeteredExposure(seconds))
        assertNotNull(stock.reciprocityGuidance(seconds))
      }
    }
  }

  @Test fun monochromeUsesFilmTypeForBuiltinAndCustomStocks() {
    assertTrue(film("BwXX", "CineStill").copy(type = "Black & White").isBlackAndWhite)
    assertTrue(film("My film", "My lab", custom = true).copy(type = "Black & White").isBlackAndWhite)
    assertTrue(film("Fomapan R 100", "Foma").copy(type = "Black & White").isBlackAndWhite)
    assertFalse(film("Black and white name", "My lab").copy(type = "Color Negative").isBlackAndWhite)
    assertFalse(film("Unspecified", "My lab").isBlackAndWhite)
  }
}
