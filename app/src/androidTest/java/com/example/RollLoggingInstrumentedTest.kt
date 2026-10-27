package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.model.FilmRoll
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.settings.RollsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RollLoggingInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val store = ViewModelStore()
  private val ownedRollNames = mutableSetOf<String>()
  private val ownedFilmNames = mutableSetOf<String>()
  private val application: Application get() = ApplicationProvider.getApplicationContext()

  @After
  fun cleanUpOwnedState() {
    compose.runOnIdle { store.clear() }
    runBlocking(Dispatchers.IO) {
      val database = AppDatabase.getDatabase(application)
      val repository = database.repository()
      repository.allRolls.first().filter { it.name in ownedRollNames }.forEach { repository.deleteRoll(it.id) }
      repository.allFilmStocks.first().filter { it.isCustom && it.name in ownedFilmNames }
        .forEach { repository.deleteStock(it.id) }
    }
  }

  private fun meter(): LightMeterViewModel {
    lateinit var model: LightMeterViewModel
    compose.runOnIdle {
      model = LightMeterViewModel(application)
      store.put("roll-logging", model)
    }
    compose.waitUntil(timeoutMillis = 10_000) { model.setupReady.value }
    return model
  }

  @Test
  fun manualShotsUseTheOpenedRollWithoutMeteringAndCanBeUndone() {
    val model = meter()
    val targetName = "Manual ${UUID.randomUUID().toString().take(8)}"
    ownedRollNames += targetName
    val target = FilmRoll(id = UUID.randomUUID().toString(), name = targetName, iso = 125, frameCapacity = 24)
    runBlocking(Dispatchers.IO) { AppDatabase.getDatabase(application).repository().saveRoll(target) }
    val originalRollId = model.currentRollId.value
    compose.setContent { MyApplicationTheme { RollsScreen(model) } }
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(targetName))
    compose.onNodeWithText(targetName).performClick()
    compose.onNodeWithText("Add shot").performClick()
    compose.onNodeWithText("Actual shutter time (seconds)").performScrollTo().performTextReplacement("1/125")
    compose.onNodeWithText("Add photo").performScrollTo().assertIsEnabled()
    compose.onNodeWithText("Save manual record").performClick()
    compose.waitUntil(timeoutMillis = 10_000) { model.allLogs.value.count { it.rollId == target.id } == 1 }
    compose.runOnIdle {
      val entry = model.allLogs.value.single { it.rollId == target.id }
      assertTrue(entry.isManual)
      assertFalse(entry.hasMeterReading)
      assertEquals(125, entry.iso)
      assertEquals(1.0 / 125, entry.correctedSeconds, 0.0)
      assertEquals(originalRollId, model.currentRollId.value)
    }
    compose.onNodeWithText("Add shot").performClick()
    compose.onNodeWithText("Actual shutter time (seconds)").performScrollTo().performTextReplacement("1/60")
    compose.onNodeWithText("Save manual record").performClick()
    compose.waitUntil(timeoutMillis = 10_000) { model.allLogs.value.count { it.rollId == target.id } == 2 }
    compose.onNodeWithText("Frame 2 saved · manual").assertExists()
    compose.onNodeWithText("Undo").performClick()
    compose.waitUntil(timeoutMillis = 10_000) { model.allLogs.value.count { it.rollId == target.id } == 1 }
    compose.runOnIdle { assertEquals(originalRollId, model.currentRollId.value) }
  }

  @Test
  fun newRollCanCreateACustomBlackAndWhiteFilmWithoutChangingTheMeter() {
    val model = meter()
    val rollName = "Custom roll ${UUID.randomUUID().toString().take(8)}"
    val filmName = "Custom film ${UUID.randomUUID().toString().take(8)}"
    ownedRollNames += rollName
    ownedFilmNames += filmName
    val originalRollId = model.currentRollId.value
    val originalFilmId = model.selectedFilmStock.value?.id
    compose.setContent { MyApplicationTheme { RollsScreen(model) } }
    compose.onNodeWithText("Start a new roll").performClick()
    compose.onNodeWithText("Roll name").performScrollTo().performTextReplacement(rollName)
    compose.onNodeWithContentDescription("Choose roll film").performScrollTo().performClick()
    compose.onNodeWithText("Add custom film").performClick()
    compose.onNodeWithText("Film name").performScrollTo().performTextReplacement(filmName)
    compose.onNodeWithText("Box ISO").performScrollTo().performTextReplacement("125")
    compose.onNodeWithText("Color Negative").performScrollTo().performClick()
    compose.onNodeWithText("Black & White").performClick()
    compose.onNodeWithText("Save film").performClick()
    compose.waitUntil(timeoutMillis = 10_000) { model.allStocks.value.any { it.name == filmName } && !model.isAddingFilm.value }
    compose.onNodeWithText("Film: $filmName").assertExists()
    compose.onNodeWithText("Start roll").performClick()
    compose.waitUntil(timeoutMillis = 10_000) { model.allRolls.value.any { it.name == rollName } && !model.isSaving.value }
    compose.runOnIdle {
      val created = model.allRolls.value.single { it.name == rollName }
      assertEquals(filmName, created.filmStockName)
      assertEquals("Black & White", created.filmType)
      assertEquals(125, created.iso)
      assertEquals(originalRollId, model.currentRollId.value)
      assertEquals(originalFilmId, model.selectedFilmStock.value?.id)
    }
    compose.onNodeWithText("Add shot").assertIsEnabled()
  }
}
