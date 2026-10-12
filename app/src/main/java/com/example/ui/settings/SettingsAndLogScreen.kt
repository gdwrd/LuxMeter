package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmStock
import com.example.sensor.ExposureCalculator
import com.example.ui.meter.LightMeterViewModel
import java.text.DateFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Date
import java.util.Locale
import kotlin.math.pow

@Composable
fun RollsScreen(viewModel: LightMeterViewModel, modifier: Modifier = Modifier) {
  val logs by viewModel.allLogs.collectAsStateWithLifecycle()
  val ready by viewModel.setupReady.collectAsStateWithLifecycle()
  val saving by viewModel.isSaving.collectAsStateWithLifecycle()
  val currentId by viewModel.currentRollId.collectAsStateWithLifecycle()
  val currentName by viewModel.currentRollName.collectAsStateWithLifecycle()
  val frameCount by viewModel.activeRollFrameCount.collectAsStateWithLifecycle()
  val format by viewModel.selectedFormat.collectAsStateWithLifecycle()
  val stock by viewModel.selectedFilmStock.collectAsStateWithLifecycle()
  var openRollId by rememberSaveable { mutableStateOf<String?>(null) }
  var showNewRoll by rememberSaveable { mutableStateOf(false) }
  var newRollName by rememberSaveable { mutableStateOf("") }
  var pendingDeleteId by rememberSaveable { mutableStateOf<Int?>(null) }
  val rolls = remember(logs) { logs.groupBy { it.rollId } }
  val openEntries = openRollId?.let { rolls[it] }.orEmpty().sortedBy { it.frameNumber }
  val openRollName = if (openRollId == currentId) currentName
    else openEntries.firstOrNull()?.rollName ?: "Roll"

  BackHandler(enabled = openRollId != null) { openRollId = null }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        if (openRollId != null) {
          TextButton(onClick = { openRollId = null }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text("All rolls", Modifier.padding(start = 8.dp))
          }
        }
        if (openRollId != null) Text(openRollName, style = MaterialTheme.typography.headlineLarge)
        Text(
          if (openRollId == null) "Your photographs, one frame at a time."
          else openEntries.size.toString() + " saved frames",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (openRollId == null) {
        item {
          Card(onClick = { openRollId = currentId }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Current roll", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text(currentName, style = MaterialTheme.typography.titleLarge)
                  Text(
                    listOfNotNull(stock?.name, format.displayName).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
              }
              Text(
                "Frame " + frameCount + " of " + format.standardFramesPerRoll,
                style = MaterialTheme.typography.bodyLarge
              )
            }
          }
        }
        item {
          Button(
            onClick = {
              newRollName = "Roll #" + (rolls.keys.plus(currentId).distinct().size + 1)
              showNewRoll = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = ready && !saving
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Start a new roll", Modifier.padding(start = 8.dp))
          }
        }
        val pastRolls = rolls.filterKeys { it != currentId }.toList()
        if (pastRolls.isNotEmpty()) {
          item { Text("Previous rolls", style = MaterialTheme.typography.titleMedium) }
          items(pastRolls, key = { it.first }) { (id, entries) ->
            Card(onClick = { openRollId = id }, modifier = Modifier.fillMaxWidth()) {
              Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(entries.first().rollName, style = MaterialTheme.typography.titleMedium)
                  Text(
                    entries.size.toString() + " frames · " + entries.first().filmStockName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
              }
            }
          }
        } else {
          item {
            Text(
              "Saved exposures stay on this device. Save a frame from the meter to begin your log.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else if (openEntries.isEmpty()) {
        item {
          Text(
            "No frames saved yet. Return to the meter and save your first exposure.",
            style = MaterialTheme.typography.bodyLarge
          )
        }
      } else {
        items(openEntries, key = { it.id }) { entry ->
          FilmLogRow(entry = entry, onDelete = { pendingDeleteId = entry.id })
        }
      }
    }
  }

  if (showNewRoll) {
    AlertDialog(
      onDismissRequest = { showNewRoll = false },
      title = { Text("Start a new roll") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("Your saved frames stay in the previous roll. The new roll starts at frame 1.")
          OutlinedTextField(
            value = newRollName,
            onValueChange = { newRollName = it },
            label = { Text("Roll name") },
            singleLine = true
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = newRollName.isNotBlank() && ready && !saving,
          onClick = {
            viewModel.startNewRoll(newRollName.trim())
            showNewRoll = false
          }
        ) { Text("Start roll") }
      },
      dismissButton = { TextButton(onClick = { showNewRoll = false }) { Text("Cancel") } }
    )
  }

  logs.firstOrNull { it.id == pendingDeleteId }?.let { entry ->
    AlertDialog(
      onDismissRequest = { pendingDeleteId = null },
      title = { Text("Delete frame " + entry.frameNumber + "?") },
      text = { Text("This removes the saved exposure from " + entry.rollName + ". It cannot be undone.") },
      confirmButton = {
        TextButton(enabled = !saving, onClick = {
          viewModel.deleteLogEntry(entry.id)
          pendingDeleteId = null
        }) { Text("Delete frame", color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Keep frame") } }
    )
  }
}

@Composable
private fun FilmLogRow(entry: FilmLogEntry, onDelete: () -> Unit) {
  val date = remember(entry.timestamp) {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.timestamp))
  }
  val hasCorrection = entry.correctedSeconds.isFinite() && entry.correctedSeconds > 0.0 &&
    entry.correctedSeconds != entry.rawSeconds
  val shutter = if (hasCorrection) ExposureCalculator.formatSeconds(entry.correctedSeconds) else entry.shutterText
  Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Frame " + entry.frameNumber, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(entry.apertureText + " · " + shutter, style = MaterialTheme.typography.titleLarge)
        if (hasCorrection) {
          Text(
            "Metered " + ExposureCalculator.formatSeconds(entry.rawSeconds) + " · film correction applied",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Text(
          entry.filmStockName + " · ISO " + entry.iso + " · " + entry.format,
          style = MaterialTheme.typography.bodyMedium
        )
        Text(
          date + " · EV " + String.format(Locale.getDefault(), "%.1f", entry.evValue),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodyMedium)
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete frame " + entry.frameNumber)
      }
    }
  }
}

@Composable
fun SettingsScreen(
  viewModel: LightMeterViewModel,
  modifier: Modifier = Modifier,
  onOpenSunny16: () -> Unit = {}
) {
  val stocks by viewModel.allStocks.collectAsStateWithLifecycle()
  val addingFilm by viewModel.isAddingFilm.collectAsStateWithLifecycle()
  val filmAddedId by viewModel.filmAddedId.collectAsStateWithLifecycle()
  val saving by viewModel.isSaving.collectAsStateWithLifecycle()
  val offset by viewModel.calibrationOffsetEv.collectAsStateWithLifecycle()
  var hapticsEnabled by remember { mutableStateOf(viewModel.hapticManager.isHapticsEnabled) }
  var showAddFilm by rememberSaveable { mutableStateOf(false) }
  var pendingDeleteId by rememberSaveable { mutableStateOf<Int?>(null) }

  LaunchedEffect(filmAddedId) {
    if (filmAddedId != null) {
      showAddFilm = false
      viewModel.clearFilmAddedReceipt()
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      item {
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Calibration", style = MaterialTheme.typography.titleLarge)
            Text(
              "Compare readings with a trusted meter, then adjust the EV offset for this device. Accuracy depends on the phone and measuring conditions.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              String.format(Locale.getDefault(), "%+.1f EV", offset),
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.primary
            )
            Slider(
              value = offset,
              onValueChange = viewModel::setCalibrationOffset,
              valueRange = -3f..3f,
              steps = 11,
              modifier = Modifier.semantics { contentDescription = "Calibration offset in EV" }
            )
            TextButton(onClick = { viewModel.setCalibrationOffset(0f) }, enabled = offset != 0f) { Text("Reset calibration") }
          }
        }
      }
      item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text("Haptic feedback", style = MaterialTheme.typography.titleMedium)
            Text("A small tick when you adjust exposure.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(modifier = Modifier.semantics { contentDescription = "Haptic feedback" }, checked = hapticsEnabled, onCheckedChange = {
            hapticsEnabled = it
            viewModel.setHapticsEnabled(it)
          })
        }
      }
      item {
        TextButton(onClick = onOpenSunny16) { Text("Sunny 16 guide") }
        HorizontalDivider()
      }
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Film library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
          TextButton(onClick = {
            viewModel.clearFilmAddedReceipt()
            showAddFilm = true
          }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add film", Modifier.padding(start = 4.dp))
          }
        }
      }
      items(stocks, key = { it.id }) { film ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(film.name, style = MaterialTheme.typography.titleMedium)
            Text(film.brand + " · ISO " + film.iso + " · " + film.type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (film.isCustom) Text("Custom film", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
          }
          if (film.isCustom) {
            IconButton(onClick = { pendingDeleteId = film.id }) {
              Icon(Icons.Default.DeleteOutline, contentDescription = "Delete " + film.name)
            }
          }
        }
      }
      item {
        Text(
          "Long-exposure corrections use each film's reciprocity profile. Check the film maker's guidance before a critical exposure.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }

  if (showAddFilm) {
    AddFilmDialog(
      saving = addingFilm,
      onDismiss = { showAddFilm = false },
      onAdd = viewModel::addCustomFilmStock
    )
  }

  stocks.firstOrNull { it.id == pendingDeleteId }?.let { film ->
    AlertDialog(
      onDismissRequest = { pendingDeleteId = null },
      title = { Text("Delete " + film.name + "?") },
      text = { Text("This removes the custom film profile. Saved exposures are kept.") },
      confirmButton = {
        TextButton(enabled = !saving, onClick = {
          viewModel.deleteFilmStock(film.id)
          pendingDeleteId = null
        }) { Text("Delete film", color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Keep film") } }
    )
  }
}

@Composable
private fun AddFilmDialog(saving: Boolean, onDismiss: () -> Unit, onAdd: (FilmStock) -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  var brand by rememberSaveable { mutableStateOf("") }
  var isoText by rememberSaveable { mutableStateOf("400") }
  var exponentText by rememberSaveable { mutableStateOf(NumberFormat.getNumberInstance().format(1.25)) }
  val iso = isoText.toIntOrNull()
  val exponent = parseReciprocityExponent(exponentText)
  val isoValid = iso != null && iso in 1..12800
  val exponentValid = isValidReciprocityExponent(exponent)

  AlertDialog(
    onDismissRequest = { if (!saving) onDismiss() },
    title = { Text("Add film") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedTextField(name, { name = it }, label = { Text("Film name") }, singleLine = true, enabled = !saving)
        OutlinedTextField(brand, { brand = it }, label = { Text("Brand (optional)") }, singleLine = true, enabled = !saving)
        OutlinedTextField(
          isoText, { isoText = it }, label = { Text("Box ISO") }, singleLine = true, enabled = !saving,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          isError = !isoValid,
          supportingText = if (isoValid) null else {
            { Text("Enter an ISO from 1 to 12,800.") }
          }
        )
        OutlinedTextField(
          exponentText, { exponentText = it }, label = { Text("Reciprocity exponent") }, singleLine = true, enabled = !saving,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          isError = !exponentValid,
          supportingText = { Text(if (exponentValid) "Use the film maker's value for tᵖ. Use 1 for no correction." else "Use an exponent of at least 1 that keeps a 60-second exposure within one day.") }
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = !saving && name.isNotBlank() && isoValid && exponentValid,
        onClick = {
          if (!saving && name.isNotBlank() && isoValid && exponentValid) {
            onAdd(FilmStock(
              name = name.trim(),
              brand = brand.trim().ifBlank { "Custom" },
              iso = iso!!,
              type = "Color Negative",
              reciprocityFactor = exponent!!,
              description = "Custom film profile",
              badgeStyle = "CUSTOM",
              isCustom = true
            ))
          }
        }
      ) { Text(if (saving) "Saving film…" else "Save film") }
    },
    dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
  )
}

internal fun isValidReciprocityExponent(exponent: Double?): Boolean =
  exponent != null && exponent.isFinite() && exponent >= 1.0 &&
    ExposureCalculator.SHUTTER_SECONDS.max().pow(exponent) <= ExposureCalculator.MAX_EXPOSURE_SECONDS

internal fun parseReciprocityExponent(text: String, locale: Locale = Locale.getDefault()): Double? {
  val input = text.trim()
  val format = NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false }
  val position = ParsePosition(0)
  val number = format.parse(input, position)?.toDouble() ?: return null
  return number.takeIf { position.index == input.length && it.isFinite() }
}
