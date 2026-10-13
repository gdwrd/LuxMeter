package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.meter.ExposureMode
import com.example.ui.meter.ExposureReadout
import com.example.ui.meter.exposureBalanceLabel
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.ui.MainScreen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExposureReadoutTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun unavailableReadingDoesNotPresentAnExposure() {
    compose.setContent {
      MyApplicationTheme {
        ExposureReadout(ExposureMode.APERTURE_PRIORITY, "f/4", "1/500s", false, 11.0, 0.0, true, {}, {})
      }
    }
    compose.onNodeWithText("Recommended shutter").assertIsDisplayed()
    compose.onNodeWithText("—").assertIsDisplayed()
    compose.onNodeWithText("1/500s").assertDoesNotExist()
    compose.onNodeWithText("EV₁₀₀ 11.0").assertDoesNotExist()
  }

  @Test
  fun unknownFilmCanShowMeteredExposureWithoutCallingItARecommendation() {
    compose.setContent {
      MyApplicationTheme {
        ExposureReadout(ExposureMode.APERTURE_PRIORITY, "f/8", "1/125s", true, 11.0, 0.0, true, {}, {}, resultLabel = "Metered shutter")
      }
    }
    compose.onNodeWithText("Metered shutter").assertIsDisplayed()
    compose.onNodeWithText("1/125s").assertIsDisplayed()
    compose.onNodeWithText("Recommended shutter").assertDoesNotExist()
  }

  @Test
  fun largeTextKeepsTheEditableApertureReachable() {
    var apertureOpened = false
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
        MyApplicationTheme {
          Surface {
            Box(Modifier.width(320.dp)) {
              ExposureReadout(ExposureMode.APERTURE_PRIORITY, "f/4", "1/500s", true, 11.0, 0.0, true, { apertureOpened = true }, {})
            }
          }
        }
      }
    }
    compose.onNodeWithText("1/500s").assertIsDisplayed()
    compose.onNodeWithText("f/4").assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals(true, apertureOpened) }
    saveScreenshot("exposure-large-text")
  }

  @Test
  fun cameraPermissionStateKeepsHoldDisabledAndSettingsAccessible() {
    compose.setContent { MyApplicationTheme(darkTheme = false) { MainScreen() } }
    compose.onNodeWithText("Hold reading").assertIsDisplayed().assertIsNotEnabled()
    compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    compose.onNodeWithText("Enable camera").assertIsDisplayed()
    saveScreenshot("meter-permission")
  }

  @Test
  fun darkAppearanceKeepsTheCameraPermissionActionVisible() {
    compose.setContent { MyApplicationTheme(darkTheme = true) { MainScreen() } }
    compose.onNodeWithText("Enable camera").assertIsDisplayed()
    compose.onNodeWithText("Hold reading").assertIsDisplayed().assertIsNotEnabled()
    saveScreenshot("meter-dark")
  }

  private fun saveScreenshot(name: String) {
    compose.runOnIdle {
      val view = compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(Canvas(bitmap))
      val screenshot = File("build/reports/screenshots/$name.png")
      screenshot.parentFile?.mkdirs()
      screenshot.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
    }
  }

  @Test
  fun manualBalanceExplainsUnderAndOverExposure() {
    assertEquals("Matched", exposureBalanceLabel(0.1))
    assertEquals(String.format("%.1f stops under", 2.0), exposureBalanceLabel(2.0))
    assertEquals(String.format("%.1f stops over", 1.0), exposureBalanceLabel(-1.0))
  }
}
