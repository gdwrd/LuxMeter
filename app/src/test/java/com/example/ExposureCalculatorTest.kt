package com.example

import com.example.data.model.CameraFormat
import com.example.data.model.FilmStock
import com.example.sensor.ExposureCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExposureCalculatorTest {
  @Test fun cameraDialInputSupportsOlderSpeedsAndRejectsInvalidValues() {
    assertEquals(listOf(0.01, 0.02, 0.04, 1.0), ExposureCalculator.parseShutterSpeeds("1/25 1/50,1/100 1s"))
    assertEquals("1/25s", ExposureCalculator.formatSeconds(0.04))
    assertEquals("f/3.5", ExposureCalculator.formatAperture(3.5))
    for (input in listOf("", "0", "1/0", "1/2/3", "NaN", "Infinity", "61", "1/125 invalid"))
      assertNull(input, ExposureCalculator.parseShutterSpeeds(input))
    val apertures = listOf(3.5, 5.6, 8.0)
    val shutters = listOf(0.01, 0.02, 0.04, 1.0)
    val index = ExposureCalculator.calculateShutterIndexForAperture(0, kotlin.math.log2(3.5 * 3.5 / 0.04), apertures, shutters)
    assertEquals(2, index)
  }

  @Test
  fun testSunny16Rule() {
    // Sunny 16: In bright sunlight (~80,000 lux), EV100 ≈ 15.0
    // At f/16 and ISO 100, shutter speed should be 1/100s (closest standard is 1/125s)
    val ev100 = ExposureCalculator.luxToEv100(81920f)
    assertEquals(15.0, ev100, 0.2)

    val evAtIso100 = ExposureCalculator.ev100ToEvForIso(ev100, 100)
    assertEquals(15.0, evAtIso100, 0.2)

    // Index 9 is f/16
    val f16Index = ExposureCalculator.APERTURE_LABELS.indexOf("f/16")
    assertTrue(f16Index >= 0)

    val shutterIdx = ExposureCalculator.calculateShutterIndexForAperture(f16Index, evAtIso100)
    val shutterLabel = ExposureCalculator.SHUTTER_LABELS[shutterIdx]
    assertEquals("1/125", shutterLabel)
  }

  @Test
  fun testIsoCompensation() {
    // When ISO increases from 100 to 400 (x4 speed), EV_s increases by log2(4) = +2 EV
    val ev100 = 10.0
    val evAtIso400 = ExposureCalculator.ev100ToEvForIso(ev100, 400)
    assertEquals(12.0, evAtIso400, 0.001)

    val evAtIso50 = ExposureCalculator.ev100ToEvForIso(ev100, 50)
    assertEquals(9.0, evAtIso50, 0.001)
  }

  @Test
  fun testReciprocityFailureCalculation() {
    val portra = FilmStock(
      name = "Portra 400",
      brand = "Kodak",
      iso = 400,
      type = "Color Negative",
      reciprocityFactor = 1.22,
      description = "Test",
      badgeStyle = "PORTRA"
    )

    // For exposure <= 1s, no reciprocity failure
    assertEquals(0.5, portra.calculateCorrectedExposure(0.5)!!, 0.001)
    assertEquals(1.0, portra.calculateCorrectedExposure(1.0)!!, 0.001)

    assertNull("Kodak provides no timed correction for this range", portra.calculateCorrectedExposure(4.0))
  }

  @Test
  fun testCameraFormats() {
    assertEquals(36, CameraFormat.FORMAT_35MM.standardFramesPerRoll)
    assertEquals("3:2", CameraFormat.FORMAT_35MM.aspectRatioLabel)

    assertEquals(16, CameraFormat.MEDIUM_645.standardFramesPerRoll)
    assertEquals("4:3", CameraFormat.MEDIUM_645.aspectRatioLabel)

    assertEquals(12, CameraFormat.MEDIUM_66.standardFramesPerRoll)
    assertEquals("1:1", CameraFormat.MEDIUM_66.aspectRatioLabel)

    assertEquals(10, CameraFormat.MEDIUM_67.standardFramesPerRoll)
    assertEquals("5:4", CameraFormat.MEDIUM_67.aspectRatioLabel)
  }

  @Test
  fun testShutterPriorityAperture() {
    // At EV 15 and 1/125s shutter, aperture should calculate to f/16
    val shutter125Idx = ExposureCalculator.SHUTTER_LABELS.indexOf("1/125")
    val apIdx = ExposureCalculator.calculateApertureIndexForShutterSeconds(ExposureCalculator.SHUTTER_SECONDS[shutter125Idx], 15.0)
    val apertureLabel = ExposureCalculator.APERTURE_LABELS[apIdx]
    assertEquals("f/16", apertureLabel)
  }

  @Test
  fun testDepthOfFieldCalculation() {
    // 50mm lens on 35mm film, f/8 at 3.0m
    val dof = com.example.sensor.DepthOfFieldCalculator.calculate(
      focalLengthMm = 50.0,
      apertureValue = 8.0,
      focusDistanceMeters = 3.0,
      format = CameraFormat.FORMAT_35MM
    )

    // Near limit should be ~2.3m - 2.4m
    assertTrue("Near limit ${dof.nearMeters} should be less than focus 3.0m", dof.nearMeters < 3.0)
    assertTrue("Near limit ${dof.nearMeters} should be > 2.0m", dof.nearMeters > 2.0)

    // Far limit should be ~4.1m - 4.3m
    assertTrue("Far limit ${dof.farMeters} should be greater than focus 3.0m", dof.farMeters > 3.0)
    assertTrue("Far limit ${dof.farMeters} should be < 5.0m", dof.farMeters < 5.0)

    // Total depth = far - near
    assertTrue("Total depth should be positive", dof.totalDepthMeters > 0)
    assertEquals(dof.farMeters - dof.nearMeters, dof.totalDepthMeters, 0.001)

    // Hyperfocal test: when focused at or beyond hyperfocal, far limit is Infinity
    val hyperDof = com.example.sensor.DepthOfFieldCalculator.calculate(
      focalLengthMm = 50.0,
      apertureValue = 8.0,
      focusDistanceMeters = dof.hyperfocalMeters,
      format = CameraFormat.FORMAT_35MM
    )
    assertTrue("Far limit at hyperfocal should be Infinity", hyperDof.isFarInfinity)
  }

  @Test
  fun testMediumFormatDof() {
    // 80mm lens on 6x6 medium format at f/2.8, focus 2.0m
    val dof = com.example.sensor.DepthOfFieldCalculator.calculate(
      focalLengthMm = 80.0,
      apertureValue = 2.8,
      focusDistanceMeters = 2.0,
      format = CameraFormat.MEDIUM_66
    )

    assertTrue("Near limit should be < 2.0m", dof.nearMeters < 2.0)
    assertTrue("Far limit should be > 2.0m", dof.farMeters > 2.0)
    assertTrue("Depth of field at f/2.8 is shallow (< 0.5m)", dof.totalDepthMeters < 0.5)
  }

  @Test
  fun testMediumFormatApertureEquivalence() {
    // 6x6 medium format has 0.55x crop factor
    val eq66 = com.example.sensor.DepthOfFieldCalculator.getEquivalent35mmAperture(
      apertureValue = 2.8,
      format = CameraFormat.MEDIUM_66
    )
    assertEquals(1.54, eq66, 0.05)

    // 6x7 medium format has 0.50x crop factor (f/2.8 behaves like f/1.4 on 35mm!)
    val eq67 = com.example.sensor.DepthOfFieldCalculator.getEquivalent35mmAperture(
      apertureValue = 2.8,
      format = CameraFormat.MEDIUM_67
    )
    assertEquals(1.40, eq67, 0.05)

    // 6x4.5 medium format has 0.62x crop factor (f/4 behaves like f/2.5 on 35mm)
    val eq645 = com.example.sensor.DepthOfFieldCalculator.getEquivalent35mmAperture(
      apertureValue = 4.0,
      format = CameraFormat.MEDIUM_645
    )
    assertEquals(2.48, eq645, 0.05)

    // 35mm format equivalence is 1:1
    val eq35 = com.example.sensor.DepthOfFieldCalculator.getEquivalent35mmAperture(
      apertureValue = 2.8,
      format = CameraFormat.FORMAT_35MM
    )
    assertEquals(2.8, eq35, 0.001)
  }

  @Test fun lowLightConversionAndCalibrationDoNotClipMeasuredEv() {
    assertEquals(-6.0, ExposureCalculator.luxToEv100(0.0390625f), 0.000001)
    assertEquals(-6.0, ExposureCalculator.luxToEv100(0.3125f, -3f), 0.000001)
    assertEquals(23.0, ExposureCalculator.luxToEv100(2.5f * (1 shl 20), 3f), 0.000001)
  }

  @Test(expected = IllegalArgumentException::class)
  fun zeroLuxDoesNotFabricateAReading() { ExposureCalculator.luxToEv100(0f) }

  @Test fun unsupportedDurationsAreNotFormattedAsPlausibleValues() {
    for (seconds in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 60.0 * 60 * 60 * 60 * 60 * 60)) {
      assertEquals("—", ExposureCalculator.formatSeconds(seconds))
    }
    assertEquals("86400s", ExposureCalculator.formatSeconds(ExposureCalculator.MAX_EXPOSURE_SECONDS))
    assertEquals("1/2s", ExposureCalculator.formatSeconds(0.5))
    assertEquals("0.810s", ExposureCalculator.formatSeconds(0.81))
  }
}
