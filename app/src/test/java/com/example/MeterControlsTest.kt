package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.model.FilmStock
import com.example.sensor.HapticManager
import com.example.ui.components.FilmStockPickerSheet
import com.example.ui.components.parseExposureInput
import com.example.ui.meter.HeldExposureSummary
import com.example.ui.meter.ValuePicker
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MeterControlsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun valuePickerOpensAtTheSelectedValueAndItsNeighbours() {
    var chosen = -1
    compose.setContent {
      MyApplicationTheme {
        ValuePicker("Shutter speed", (1..20).map { "Choice $it" }, 17, {}, { chosen = it })
      }
    }
    compose.onNodeWithText("Choice 18").assertIsDisplayed()
    compose.onNodeWithText("Choice 19").assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals(18, chosen) }
  }

  @Test
  fun heldExposureIsOneCompleteAccessibleResultAtLargeTextSize() {
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
        MyApplicationTheme {
          Surface {
            Box(Modifier.width(320.dp)) { HeldExposureSummary(400, "f/8", 1.0 / 125) }
          }
        }
      }
    }
    val summary = "Reading held. ISO 400. Aperture f 8. Shutter 1 over 125 second."
    compose.onAllNodesWithContentDescription(summary).assertCountEquals(1)
    compose.onNodeWithContentDescription(summary).assertIsDisplayed()
  }

  @Test
  fun filmDetailsCanBeOpenedWithoutSelectingTheFilm() {
    val film = FilmStock(42, "Unlisted film", "Custom", 400, "Color Negative", 1.0,
      description = "", badgeStyle = "CUSTOM", isCustom = true, hasReciprocityProfile = false)
    var selections = 0
    compose.setContent {
      MyApplicationTheme {
        FilmStockPickerSheet(listOf(film), null, 400, HapticManager(compose.activity), { selections++ }, {}, {})
      }
    }
    compose.onNodeWithContentDescription("Details for Unlisted film").assertIsDisplayed().performClick()
    compose.onNodeWithText("No timed correction supplied. You can record the settings you choose.").assertIsDisplayed()
    compose.runOnIdle { assertEquals(0, selections) }
  }

  @Test
  fun fractionalExposureInputsRespectLocaleAndRejectPartialOrNonfiniteNumbers() {
    assertEquals(3.5, parseExposureInput("3,5", Locale.GERMANY)!!, 0.0001)
    assertEquals(0.008, parseExposureInput("0.008", Locale.US)!!, 0.0001)
    assertNull(parseExposureInput("3 stops", Locale.US))
    assertNull(parseExposureInput("NaN", Locale.US))
    assertNull(parseExposureInput("", Locale.US))
  }
}
