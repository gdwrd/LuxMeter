package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.FilmStock
import com.example.sensor.HapticManager
import com.example.ui.components.FilmStockPickerSheet
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilmLibraryPickerTest {
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

  private fun showSheet(library: List<FilmStock> = stocks, iso: Int = 400, pushStops: Int? = 0) {
    compose.setContent {
      MyApplicationTheme {
        if (showing.value) {
          FilmStockPickerSheet(
            stocks = library, selectedStock = hp5, customIso = iso, pushStops = pushStops,
            hapticManager = HapticManager(compose.activity),
            onApply = { stock, rating, processing, confirmed ->
              applied += Applied(stock?.id, rating, processing, confirmed)
              true
            },
            needsConfirmation = { _, _, _ -> false },
            onDismiss = { showing.value = false }
          )
        }
      }
    }
  }

  private fun scrollTo(text: String) {
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
  }

  private fun clickInSheet(text: String) {
    scrollTo(text)
    compose.onNodeWithText(text).performClick()
  }

  private fun search(query: String) {
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Search films"))
    compose.onNodeWithContentDescription("Search films").performTextReplacement(query)
  }

  @Test
  fun customFilmActionAndSavedFilmSelectionDoNotApplyUntilConfirmed() {
    var requested = false
    val addedId = mutableStateOf<Int?>(null)
    compose.setContent {
      MyApplicationTheme {
        FilmStockPickerSheet(
          stocks = stocks, selectedStock = hp5, customIso = 800, pushStops = 1,
          hapticManager = HapticManager(compose.activity),
          onApply = { stock, rating, processing, confirmed ->
            applied += Applied(stock?.id, rating, processing, confirmed); true
          },
          needsConfirmation = { _, _, _ -> false }, onDismiss = {},
          onAddCustomFilm = { requested = true }, newFilmId = addedId.value
        )
      }
    }
    clickInSheet("Add custom film")
    compose.runOnIdle { assertTrue(requested); assertTrue(applied.isEmpty()); addedId.value = portra.id }
    scrollTo("Meter at EI 160")
    compose.onNodeWithText("Ask lab: Normal processing").assertExists()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
    compose.onNodeWithText("Use for this roll").performClick()
    compose.runOnIdle { assertEquals(listOf(Applied(portra.id, 160, 0, false)), applied) }
  }

  @Test
  fun searchFindsFilmsByBrandNameAndIsoIgnoringCase() {
    showSheet()
    search("kOdAk")
    compose.onNodeWithText(portra.name).assertExists()
    compose.onNodeWithText(hp5.name).assertDoesNotExist()

    search("velvia")
    compose.onNodeWithText(velvia.name).assertExists()
    compose.onNodeWithText(portra.name).assertDoesNotExist()

    search("800")
    compose.onNodeWithText(cineStill.name).assertExists()
    compose.onNodeWithText(velvia.name).assertDoesNotExist()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
  }

  @Test
  fun typeFiltersIncludeTungstenFilmUnderColorAndKeepSlidesSeparate() {
    showSheet()
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

    clickInSheet("All")
    scrollTo(cineStill.name)
    compose.onNodeWithText(cineStill.name).assertIsDisplayed()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
  }

  @Test
  fun emptySearchKeepsTheDraftFilmSpeedAndProcessingUntilApplied() {
    showSheet(iso = 800, pushStops = 1)
    clickInSheet("Slide")
    search("missing film")
    compose.onNodeWithText("No matching films").assertIsDisplayed()
    scrollTo("Meter at EI 800")
    compose.onNodeWithText("Ask lab: Push +1 stop").assertExists()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
    compose.onNodeWithText("Use for this roll").assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals(listOf(Applied(1, 800, 1, false)), applied) }
  }

  @Test
  fun filteredReselectionKeepsTheDraftButChoosingAnotherFilmResetsIt() {
    showSheet(iso = 800, pushStops = 1)
    clickInSheet("B&W")
    search("Ilford")
    clickInSheet(hp5.name)
    scrollTo("Meter at EI 800")
    compose.onNodeWithText("Ask lab: Push +1 stop").assertExists()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }

    clickInSheet("All")
    search("Kodak")
    clickInSheet(portra.name)
    scrollTo("Meter at EI 160")
    compose.onNodeWithText("Ask lab: Normal processing").assertExists()
    compose.onNodeWithText("Use for this roll").performClick()
    compose.runOnIdle { assertEquals(listOf(Applied(2, 160, 0, false)), applied) }
  }

  @Test
  fun expandedCatalogueKeepsDetailsAndApplyReachableAndCanCollapseAgain() {
    val library = stocks + (5..12).map { film(it, "Library film $it", "Test", 100, "Color Negative") }
    showSheet(library)
    compose.onNodeWithText(library.last().name).assertDoesNotExist()
    clickInSheet("View all 12 films")
    scrollTo(library.last().name)
    compose.onNodeWithText("Use for this roll").assertIsDisplayed()
    compose.onNodeWithContentDescription("Details for ${library.last().name}").assertIsDisplayed().performClick()
    compose.onNodeWithText("No timed correction supplied. You can record the settings you choose.").assertIsDisplayed()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
    compose.onNodeWithText("Done").performClick()
    clickInSheet("Show fewer films")
    compose.onNodeWithText(library.last().name).assertDoesNotExist()
    compose.onNodeWithContentDescription("Close film picker").performClick()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
  }
}
