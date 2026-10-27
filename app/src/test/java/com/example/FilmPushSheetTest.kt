package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
class FilmPushSheetTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val showing = mutableStateOf(true)
  private val applied = mutableListOf<Applied>()
  private val hp5 = film(7, "HP5 Plus 400", 400)

  private data class Applied(val stockId: Int?, val iso: Int, val pushStops: Int?, val confirmed: Boolean)

  private fun film(id: Int, name: String, iso: Int) = FilmStock(
    id, name, "Test", iso, "Black & White", 1.31,
    description = "", badgeStyle = "CUSTOM", isCustom = true
  )

  private fun showSheet(
    stocks: List<FilmStock> = listOf(hp5),
    selectedStock: FilmStock = stocks.first(),
    iso: Int = selectedStock.iso,
    pushStops: Int? = 0,
    confirmationRequired: Boolean = false
  ) {
    compose.setContent {
      MyApplicationTheme {
        if (showing.value) {
          FilmStockPickerSheet(
            stocks, selectedStock, iso, pushStops, HapticManager(compose.activity),
            onApply = { stock, rating, processing, confirmed ->
              applied += Applied(stock?.id, rating, processing, confirmed)
              true
            },
            needsConfirmation = { _, _, _ -> confirmationRequired },
            onDismiss = { showing.value = false }
          )
        }
      }
    }
  }

  private fun clickInSheet(text: String) {
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    compose.onNodeWithText(text).performClick()
  }

  @Test
  fun cancellingPushDraftDoesNotApplySettings() {
    showSheet(pushStops = null)
    clickInSheet("+2")
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Meter at EI 1600"))
    compose.onNodeWithText("Meter at EI 1600").assertExists()
    compose.onNodeWithText("Ask lab: Push +2 stops").assertExists()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
    compose.onNodeWithContentDescription("Close film picker").performClick()
    compose.onNodeWithText("Film & speed").assertDoesNotExist()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
  }

  @Test
  fun customRatingAndReselectingTheSameFilmPreserveProcessing() {
    showSheet()
    clickInSheet("+2")
    clickInSheet("ISO 1600")
    compose.onNodeWithText("ISO 800").performClick()
    clickInSheet("HP5 Plus 400")
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Meter at EI 800"))
    compose.onNodeWithText("Ask lab: Push +2 stops").assertExists()
    compose.onNodeWithText("Use for this roll").performClick()
    compose.runOnIdle { assertEquals(listOf(Applied(7, 800, 2, false)), applied) }
  }

  @Test
  fun changingFilmResetsToNormalAndQuickChoicesRespectTheIsoBound() {
    val highSpeed = film(8, "High speed film", 12800)
    val oddSpeed = film(9, "Test 125", 125)
    showSheet(listOf(highSpeed, oddSpeed), iso = 25600, pushStops = 2)
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+4"))
    compose.onNodeWithText("+4").assertIsNotEnabled()
    clickInSheet("Test 125")
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Meter at EI 125"))
    compose.onNodeWithText("Ask lab: Normal processing").assertExists()
    clickInSheet("+3")
    compose.onNodeWithText("Meter at EI 1000").assertExists()
    compose.onNodeWithText("Use for this roll").performClick()
    compose.runOnIdle { assertEquals(listOf(Applied(9, 1000, 3, false)), applied) }
  }

  @Test
  fun populatedRollRequiresTheExplicitWholeRollConfirmation() {
    showSheet(confirmationRequired = true)
    clickInSheet("+1")
    compose.onNodeWithText("Use for this roll").performClick()
    compose.onNodeWithText("Change this roll?").assertIsDisplayed()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
    compose.onNodeWithText("Keep current settings").performClick()
    compose.runOnIdle { assertTrue(applied.isEmpty()) }
    compose.onNodeWithText("Use for this roll").performClick()
    compose.onNodeWithText("Apply to whole roll").performClick()
    compose.runOnIdle { assertEquals(listOf(Applied(7, 800, 1, true)), applied) }
  }
}
