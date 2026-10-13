package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.components.ExposureAdjustmentsDialog
import com.example.ui.components.ManualExposureDialog
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat

@RunWith(AndroidJUnit4::class)
class MeterDialogInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun compensationUsesThirdStopsAndStopsAtTheLimitThenResetsBothAdjustments() {
    var compensation by mutableDoubleStateOf(8.0 / 3.0)
    var filter by mutableDoubleStateOf(6.0)
    compose.setContent {
      MyApplicationTheme {
        ExposureAdjustmentsDialog(compensation, filter, { compensation = it }, { filter = it }, {
          compensation = 0.0
          filter = 0.0
        }, {})
      }
    }
    compose.onNodeWithContentDescription("Increase exposure compensation").performClick()
    compose.runOnIdle { assertEquals(3.0, compensation, 0.0001) }
    compose.onNodeWithContentDescription("Increase exposure compensation").assertIsNotEnabled()
    compose.onNodeWithText("Reset adjustments").performScrollTo().performClick()
    compose.runOnIdle {
      assertEquals(0.0, compensation, 0.0001)
      assertEquals(0.0, filter, 0.0001)
    }
  }

  @Test
  fun invalidFilterStrengthDoesNotChangeTheMeter() {
    var filter by mutableDoubleStateOf(3.0)
    compose.setContent {
      MyApplicationTheme {
        ExposureAdjustmentsDialog(0.0, filter, {}, { filter = it }, {}, {})
      }
    }
    compose.onNodeWithText("Filter strength (stops)").performScrollTo().performTextReplacement("17")
    compose.onNodeWithText("Done").assertIsNotEnabled()
    compose.runOnIdle { assertEquals(3.0, filter, 0.0001) }
    compose.onNodeWithText("Filter strength (stops)").performTextReplacement(NumberFormat.getNumberInstance().format(3.5))
    compose.runOnIdle { assertEquals(3.5, filter, 0.0001) }
  }

  @Test
  fun manualRecordRequiresAnActualDurationAndPreservesEnteredSettings() {
    var saved: Triple<Double, Double, String>? = null
    compose.setContent {
      MyApplicationTheme {
        ManualExposureDialog(8.0, 0.0, false, { aperture, seconds, notes -> saved = Triple(aperture, seconds, notes) }, {})
      }
    }
    compose.onNodeWithText("Save manual record").assertIsNotEnabled()
    compose.onNodeWithText("Actual shutter time (seconds)").performScrollTo().performTextReplacement("8")
    compose.onNodeWithText("Notes (optional)").performScrollTo().performTextReplacement("Night exposure")
    compose.onNodeWithText("Save manual record").performClick()
    compose.runOnIdle { assertEquals(Triple(8.0, 8.0, "Night exposure"), saved) }
  }

}
