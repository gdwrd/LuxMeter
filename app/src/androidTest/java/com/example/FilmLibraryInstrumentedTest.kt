package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.FilmStock
import com.example.sensor.HapticManager
import com.example.ui.components.FilmStockPickerSheet
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilmLibraryInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val showing = mutableStateOf(true)
  private val applied = mutableListOf<Applied>()
  private val hp5 = film(1, "HP5 Plus 400", "Ilford", 400, "Black & White")
  private val portra = film(2, "Portra 160", "Kodak", 160, "Color Negative")
  private val cineStill = film(3, "CineStill 800T", "CineStill", 800, "Tungsten Cine")
  private val velvia = film(4, "Velvia 50", "Fujifilm", 50, "Color Slide")
  private val stocks = listOf(hp5, portra, cineStill, velvia)

  private data class Applied(val stockId: Int?, val iso: Int, val pushStops: Int?, val confirmed: Boolean)

  private fun film(id: Int, name: String, brand: String, iso: Int, type: String) = FilmStock(
    id, name, brand, iso, type, 1.0,
    description = "", badgeStyle = "CUSTOM", isCustom = true, hasReciprocityProfile = false
  )

  private fun showSheet(
    library: List<FilmStock> = stocks,
    confirmationRequired: Boolean = false,
    dark: Boolean = false,
    fontScale: Float? = null
  ) {
    compose.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
        MyApplicationTheme(darkTheme = dark) {
          if (showing.value) {
            FilmStockPickerSheet(
              stocks = library, selectedStock = hp5, customIso = 400, pushStops = 0,
              hapticManager = HapticManager(compose.activity),
              onApply = { stock, iso, processing, confirmed ->
                applied += Applied(stock?.id, iso, processing, confirmed)
                true
              },
              needsConfirmation = { _, _, _ -> confirmationRequired },
              onDismiss = { showing.value = false }
            )
          }
        }
      }
    }
  }

  private fun scrollTo(text: String) {
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
  }

  private fun clickInSheet(text: String) {
    scrollTo(text)
    compose.onNodeWithText(text).assertIsDisplayed().performClick()
  }

  private fun search(query: String) {
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Search films"))
    compose.onNodeWithContentDescription("Search films").assertIsDisplayed().performTextReplacement(query)
  }

  @Test
  fun nativeSearchFiltersAndCancellationKeepSettingsUntouched() {
    showSheet()
    search(" kOdAk ")
    compose.onNodeWithText(portra.name).assertExists()
    compose.onNodeWithText(velvia.name).assertDoesNotExist()
    search("velvia")
    compose.onNodeWithText(velvia.name).assertExists()
    compose.onNodeWithText(portra.name).assertDoesNotExist()
    search("800")
    compose.onNodeWithText(cineStill.name).assertExists()
    compose.onNodeWithText(velvia.name).assertDoesNotExist()
    compose.onNodeWithContentDescription("Clear film search").performClick()

    clickInSheet("Color")
    compose.onNodeWithText(portra.name).assertExists()
    compose.onNodeWithText(cineStill.name).assertExists()
    compose.onNodeWithText(hp5.name).assertDoesNotExist()
    compose.onNodeWithText(velvia.name).assertDoesNotExist()
    clickInSheet("B&W")
    compose.onNodeWithText(hp5.name).assertExists()
    compose.onNodeWithText(portra.name).assertDoesNotExist()
    compose.onNodeWithText(cineStill.name).assertDoesNotExist()
    clickInSheet("Slide")
    compose.onNodeWithText(velvia.name).assertExists()
    compose.onNodeWithText(hp5.name).assertDoesNotExist()
    search("missing film")
    compose.onNodeWithText("No matching films").assertIsDisplayed()
    scrollTo("Meter at EI 400")
    compose.onNodeWithText("Ask lab: Normal processing").assertExists()
    compose.onNodeWithContentDescription("Close film picker").performClick()
    compose.onNodeWithText("Film & speed").assertDoesNotExist()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
  }

  @Test
  fun expandedCatalogueDetailsAndPushRequireExplicitConfirmation() {
    val library = stocks + (5..27).map { film(it, "Library film $it", "Test", 100, "Color Negative") }
    showSheet(library, confirmationRequired = true)
    compose.onNodeWithText("27 film presets").assertIsDisplayed()
    compose.onNodeWithText(library.last().name).assertDoesNotExist()
    clickInSheet("View all 27 films")
    scrollTo(library.last().name)
    compose.onNodeWithText("Use for this roll").assertIsDisplayed()
    compose.onNodeWithContentDescription("Details for ${library.last().name}").assertIsDisplayed().performClick()
    compose.onNodeWithText("No timed correction supplied. You can record the settings you choose.").assertIsDisplayed()
    compose.onNodeWithText("Done").performClick()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
    clickInSheet("Show fewer films")
    compose.onNodeWithText(library.last().name).assertDoesNotExist()

    clickInSheet("+1")
    compose.onNodeWithText("Meter at EI 800").assertExists()
    compose.onNodeWithText("Ask lab: Push +1 stop").assertExists()
    compose.onNodeWithText("Use for this roll").assertIsDisplayed().performClick()
    compose.onNodeWithText("Change this roll?").assertIsDisplayed()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
    compose.onNodeWithText("Keep current settings").assertIsDisplayed().performClick()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
    compose.onNodeWithText("Use for this roll").performClick()
    compose.onNodeWithText("Apply to whole roll").assertIsDisplayed().performClick()
    compose.onNodeWithText("Film & speed").assertDoesNotExist()
    compose.runOnIdle { assertEquals(listOf(Applied(hp5.id, 800, 1, true)), applied) }
  }

  @Test
  fun darkLargeTextKeepsSearchCardsPushIsoAndPinnedApplyReachable() {
    showSheet(dark = true, fontScale = 1.5f)
    search("Kodak")
    scrollTo(portra.name)
    compose.onNodeWithText(portra.name).assertIsDisplayed()
    compose.onNodeWithContentDescription("Details for ${portra.name}").assertIsDisplayed()
    compose.onNodeWithText("Use for this roll").assertIsDisplayed()
    search("")
    clickInSheet("+2")
    compose.onNodeWithText("Meter at EI 1600").assertExists()
    clickInSheet("ISO 1600")
    compose.onNodeWithText("ISO 800").performScrollTo().assertIsDisplayed().performClick()
    scrollTo("Meter at EI 800")
    compose.onNodeWithText("Meter at EI 800").assertIsDisplayed()
    compose.onNodeWithText("Ask lab: Push +2 stops").assertExists()
    compose.onNodeWithText("Use for this roll").assertIsDisplayed().performClick()
    compose.onNodeWithText("Film & speed").assertDoesNotExist()
    compose.runOnIdle { assertEquals(listOf(Applied(hp5.id, 800, 2, false)), applied) }
  }
}
