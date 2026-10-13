package com.example

import com.example.data.model.FilmStock
import org.junit.Assert.*
import org.junit.Test

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
    assertNull(delayed.calculateMeteredExposure(4.0))
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
}
