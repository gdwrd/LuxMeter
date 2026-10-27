package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import com.example.ui.components.ExposureDial
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
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExposureDialTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val values = listOf("f/4", "f/5.6", "f/8")

  @Test fun swipesStepThroughTheCameraValuesAndStopAtBothEnds() {
    var result = 1
    var ticks = 0
    compose.setContent {
      var selected by remember { mutableIntStateOf(1) }
      MyApplicationTheme {
        ExposureDial("Aperture", values, selected, true, { selected = it; result = it }, {}, { ticks++ })
      }
    }
    val dial = compose.onNodeWithContentDescription("Aperture dial")
    dial.performTouchInput { swipeRight() }
    compose.runOnIdle { assertEquals(2, result); assertTrue(ticks > 0) }
    dial.performTouchInput { swipeRight() }
    compose.runOnIdle { assertEquals(2, result) }
    dial.performTouchInput { swipeLeft() }
    compose.runOnIdle { assertEquals(0, result) }
    dial.performTouchInput { swipeLeft() }
    compose.runOnIdle { assertEquals(0, result) }
  }

  @Test fun verticalScrollingDoesNotChangeExposureAndTapStillOpensPicker() {
    var changes = 0
    var opened = false
    compose.setContent {
      MyApplicationTheme { ExposureDial("Aperture", values, 1, true, { changes++ }, { opened = true }) }
    }
    compose.onNodeWithContentDescription("Aperture dial").performTouchInput { swipeUp() }
    compose.onNodeWithText("f/5.6").performClick()
    compose.runOnIdle { assertEquals(0, changes); assertTrue(opened) }
  }

  @Test fun accessibleActionsChangeOneValueAndDisabledDialRejectsSwipes() {
    var result = 1
    compose.setContent {
      var selected by remember { mutableIntStateOf(1) }
      MyApplicationTheme {
        ExposureDial("Aperture", values, selected, selected == 1, { selected = it; result = it }, {})
      }
    }
    val dial = compose.onNodeWithContentDescription("Aperture dial")
    val action = dial.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label.startsWith("Next") }
    compose.runOnIdle { assertTrue(action.action()); assertEquals(2, result) }
    assertEquals("f/8", dial.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    dial.performTouchInput { swipeLeft() }
    compose.runOnIdle { assertEquals(2, result) }
  }
}
