package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.RollExport
import com.example.data.model.CameraFormat
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmRoll
import com.example.data.model.FilmStock
import com.example.data.model.processingLabel
import coil.compose.AsyncImage
import com.example.sensor.DepthOfFieldCalculator
import com.example.sensor.ExposureCalculator
import com.example.ui.components.AddFilmDialog
import com.example.ui.components.CalibrationDialog
import com.example.ui.components.CameraSetupDialog
import com.example.ui.components.FilmDetailsDialog
import com.example.ui.components.FilmStockPickerSheet
import com.example.ui.components.ManualExposureDialog
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.meter.MeasurementSource
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RollsScreen(viewModel: LightMeterViewModel, modifier: Modifier = Modifier) {
  val logs by viewModel.allLogs.collectAsStateWithLifecycle()
  val rolls by viewModel.allRolls.collectAsStateWithLifecycle()
  val currentRoll by viewModel.currentRoll.collectAsStateWithLifecycle()
  val ready by viewModel.setupReady.collectAsStateWithLifecycle()
  val saving by viewModel.isSaving.collectAsStateWithLifecycle()
  val format by viewModel.selectedFormat.collectAsStateWithLifecycle()
  val stock by viewModel.selectedFilmStock.collectAsStateWithLifecycle()
  val stocks by viewModel.allStocks.collectAsStateWithLifecycle()
  val customIso by viewModel.customIso.collectAsStateWithLifecycle()
  val camera by viewModel.currentCameraSetup.collectAsStateWithLifecycle()
  val cameras by viewModel.allCameraSetups.collectAsStateWithLifecycle()
  val focalLength by viewModel.focalLengthMm.collectAsStateWithLifecycle()
  val createdRollId by viewModel.createdRollId.collectAsStateWithLifecycle()
  val manualSavedEntry by viewModel.manualSavedEntry.collectAsStateWithLifecycle()
  val addingFilm by viewModel.isAddingFilm.collectAsStateWithLifecycle()
  val filmAddedId by viewModel.filmAddedId.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var sharing by remember { mutableStateOf(false) }
  var openRollId by rememberSaveable { mutableStateOf<String?>(null) }
  var showNewRoll by rememberSaveable { mutableStateOf(false) }
  var newRollName by rememberSaveable { mutableStateOf("") }
  var newCapacity by rememberSaveable { mutableStateOf("36") }
  var newPushStops by rememberSaveable { mutableStateOf<Int?>(0) }
  var newFilmId by rememberSaveable { mutableStateOf<Int?>(null) }
  var newCameraId by rememberSaveable { mutableStateOf<Int?>(null) }
  var newFormatName by rememberSaveable { mutableStateOf(CameraFormat.FORMAT_35MM.name) }
  var newFocalLength by rememberSaveable { mutableStateOf("50") }
  var newIso by rememberSaveable { mutableStateOf(400) }
  var showNewRollFilm by rememberSaveable { mutableStateOf(false) }
  var showCameraChoices by rememberSaveable { mutableStateOf(false) }
  var showFormatChoices by rememberSaveable { mutableStateOf(false) }
  var showAddFilm by rememberSaveable { mutableStateOf(false) }
  var manualRollId by rememberSaveable { mutableStateOf<String?>(null) }
  var photoTargetId by rememberSaveable { mutableStateOf<Int?>(null) }
  var showFilmSettings by rememberSaveable { mutableStateOf(false) }
  var requestedNewRoll by rememberSaveable { mutableStateOf(false) }
  var editingNumber by rememberSaveable { mutableStateOf<String?>(null) }
  var numberDraft by rememberSaveable { mutableStateOf("") }
  var submittedNumber by rememberSaveable { mutableStateOf<Int?>(null) }
  var editingEntryId by rememberSaveable { mutableStateOf<Int?>(null) }
  var notesDraft by rememberSaveable { mutableStateOf("") }
  var submittedNotes by rememberSaveable { mutableStateOf<String?>(null) }
  var pendingDeleteId by rememberSaveable { mutableStateOf<Int?>(null) }
  var exportError by rememberSaveable { mutableStateOf<String?>(null) }
  val loadedRoll = currentRoll?.takeUnless { it.isFinished }
  val loadedStock = stocks.firstOrNull { it.id == loadedRoll?.filmStockId }
  val displayedRoll = rolls.firstOrNull { it.id == openRollId }
    ?: currentRoll?.takeIf { it.id == openRollId }
  val entries = logs.filter { it.rollId == openRollId }.sortedBy { it.frameNumber }
  val activeEntry = logs.firstOrNull { it.id == editingEntryId }
  val draftFilm = stocks.firstOrNull { it.id == newFilmId }
  val draftCamera = cameras.firstOrNull { it.id == newCameraId }
  val draftFormat = CameraFormat.entries.firstOrNull { it.name == newFormatName } ?: CameraFormat.FORMAT_35MM
  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) photoTargetId?.let { viewModel.updateLogPhoto(it, uri) }
    photoTargetId = null
  }

  BackHandler(enabled = openRollId != null) { openRollId = null }
  LaunchedEffect(createdRollId, requestedNewRoll) {
    if (requestedNewRoll && createdRollId != null) {
      showNewRoll = false
      requestedNewRoll = false
      openRollId = createdRollId
      viewModel.clearCreatedRollReceipt()
    }
  }
  LaunchedEffect(manualSavedEntry?.id) {
    if (manualSavedEntry?.rollId == manualRollId) manualRollId = null
  }
  LaunchedEffect(filmAddedId, stocks, showAddFilm) {
    if (showAddFilm) stocks.firstOrNull { it.id == filmAddedId }?.let { added ->
      newFilmId = added.id
      newIso = added.iso
      newPushStops = 0
      showAddFilm = false
      viewModel.clearFilmAddedReceipt()
    }
  }
  LaunchedEffect(currentRoll, submittedNumber, saving) {
    val actual = if (editingNumber == "frame") currentRoll?.lastFrameNumber else currentRoll?.frameCapacity
    if (!saving && submittedNumber != null && submittedNumber == actual) {
      editingNumber = null
      submittedNumber = null
    }
  }
  LaunchedEffect(activeEntry?.notes, submittedNotes, saving) {
    if (!saving && submittedNotes != null && activeEntry?.notes == submittedNotes) {
      editingEntryId = null
      submittedNotes = null
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (openRollId == null) {
        if (loadedRoll != null) {
          item { RollCard(loadedRoll, "Current roll", onClick = { openRollId = loadedRoll.id }) }
        } else {
          item {
            Text("No roll loaded", style = MaterialTheme.typography.titleLarge)
            Text("Start a roll or continue one below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        item {
          Button(
            onClick = {
              newRollName = "Roll #" + (rolls.size + 1)
              newCapacity = format.standardFramesPerRoll.toString()
              newPushStops = 0
              newFilmId = stock?.id
              newCameraId = camera?.id
              newFormatName = format.name
              newFocalLength = focalLength.toString()
              newIso = stock?.iso ?: customIso ?: 400
              requestedNewRoll = false
              viewModel.clearCreatedRollReceipt()
              showNewRoll = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = ready && !saving
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Start a new roll", Modifier.padding(start = 8.dp))
          }
        }
        val otherLoaded = rolls.filter { !it.isFinished && it.id != loadedRoll?.id }
        if (otherLoaded.isNotEmpty()) {
          item { Text("Other loaded rolls", style = MaterialTheme.typography.titleMedium) }
          items(otherLoaded, key = { it.id }) { roll ->
            RollCard(roll, "Ready to continue", onClick = { openRollId = roll.id })
          }
        }
        val finished = rolls.filter { it.isFinished }
        if (finished.isNotEmpty()) {
          item { Text("Finished rolls", style = MaterialTheme.typography.titleMedium) }
          items(finished, key = { it.id }) { roll ->
            RollCard(roll, "Finished", onClick = { openRollId = roll.id })
          }
        }
      } else {
        item {
          TextButton(onClick = { openRollId = null }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text("All rolls", Modifier.padding(start = 8.dp))
          }
          Text(displayedRoll?.name ?: entries.firstOrNull()?.rollName ?: "Roll", style = MaterialTheme.typography.headlineLarge)
          Text(entries.size.toString() + " saved frames", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        displayedRoll?.let { roll ->
          item {
            Text(
              rollFilmSummary(roll),
              style = MaterialTheme.typography.bodyLarge
            )
            Text(rollProcessingSummary(roll), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(
              "Last frame exposed: " + roll.lastFrameNumber + " of " + roll.frameCapacity,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!roll.isFinished) {
              Button(
                enabled = ready && !saving && roll.lastFrameNumber < roll.frameCapacity,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                  viewModel.clearManualSavedReceipt()
                  manualRollId = roll.id
                }
              ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add shot", Modifier.padding(start = 8.dp))
              }
              if (roll.lastFrameNumber >= roll.frameCapacity) Text("This roll is full.", style = MaterialTheme.typography.bodyMedium)
            }
            manualSavedEntry?.takeIf { it.rollId == roll.id }?.let { saved ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Frame ${saved.frameNumber} saved · manual", Modifier.weight(1f))
                TextButton(enabled = !saving, onClick = viewModel::undoManualSavedExposure) { Text("Undo") }
              }
            }
            if (roll.id == loadedRoll?.id) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !saving, onClick = {
                  editingNumber = "frame"
                  numberDraft = roll.lastFrameNumber.toString()
                  submittedNumber = null
                }) { Text("Set frame") }
                TextButton(enabled = !saving, onClick = {
                  editingNumber = "capacity"
                  numberDraft = roll.frameCapacity.toString()
                  submittedNumber = null
                }) { Text("Edit capacity") }
              }
              TextButton(enabled = ready && !saving && loadedStock != null, onClick = { showFilmSettings = true }) {
                Text("Edit film and processing")
              }
              if (loadedStock == null) Text(
                "Film profile unavailable. This roll's saved speed and processing are kept.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              OutlinedButton(onClick = viewModel::finishCurrentRoll, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text("Finish roll")
              }
            } else {
              Button(onClick = { viewModel.continueRoll(roll.id) }, enabled = ready && !saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (roll.isFinished) "Reopen roll" else "Continue this roll")
              }
            }
          }
        }
        if (entries.isNotEmpty()) {
          item {
            TextButton(enabled = !sharing && displayedRoll != null, onClick = {
              val rollToShare = displayedRoll ?: return@TextButton
              sharing = true
              scope.launch {
                try {
                  RollExport.share(context, rollToShare, entries)
                } catch (error: Exception) {
                  if (error is CancellationException) throw error
                  exportError = "This roll could not be shared. Try again."
                } finally { sharing = false }
              }
            }) {
              Icon(Icons.Default.Share, contentDescription = null)
              Text(if (sharing) "Preparing CSV…" else "Share roll as CSV", Modifier.padding(start = 8.dp))
            }
          }
          items(entries, key = { it.id }) { entry ->
            FilmLogRow(
              entry = entry,
              photo = viewModel.photoFile(entry.photoFileName)?.takeIf { it.isFile },
              onOpen = {
                editingEntryId = entry.id
                notesDraft = entry.notes
                submittedNotes = null
              },
              onDelete = { pendingDeleteId = entry.id }
            )
          }
        } else {
          item { Text("No frames saved yet. Add a shot here or save an exposure from the meter.") }
        }
      }
    }
  }

  if (showFilmSettings && loadedRoll != null && loadedStock != null) {
    FilmStockPickerSheet(
      stocks = listOf(loadedStock),
      selectedStock = loadedStock,
      customIso = loadedRoll.iso,
      pushStops = loadedRoll.pushStops,
      hapticManager = viewModel.hapticManager,
      onApply = viewModel::applyFilmSettings,
      needsConfirmation = viewModel::needsFilmSettingsConfirmation,
      onDismiss = { showFilmSettings = false }
    )
  }

  val manualRoll = rolls.firstOrNull { it.id == manualRollId } ?: currentRoll?.takeIf { it.id == manualRollId }
  if (manualRoll != null) {
    ManualExposureDialog(
      initialAperture = 8.0,
      initialSeconds = 0.0,
      saving = saving,
      rollSummary = "${manualRoll.name} · next frame ${manualRoll.lastFrameNumber + 1} · EI ${manualRoll.iso}",
      onSave = { aperture, seconds, notes -> viewModel.recordManualExposureForRoll(manualRoll.id, aperture, seconds, notes) },
      onSaveWithPhoto = { aperture, seconds, notes, uri ->
        viewModel.recordManualExposureForRoll(manualRoll.id, aperture, seconds, notes, uri)
      },
      onDismiss = { manualRollId = null }
    )
  }
  if (showNewRollFilm) {
    FilmStockPickerSheet(
      stocks = stocks,
      selectedStock = draftFilm,
      customIso = newIso,
      pushStops = newPushStops,
      hapticManager = viewModel.hapticManager,
      onApply = { chosen, iso, push, _ ->
        newFilmId = chosen?.id
        newIso = iso
        newPushStops = push
        showNewRollFilm = false
        true
      },
      needsConfirmation = { _, _, _ -> false },
      onDismiss = { showNewRollFilm = false },
      onAddCustomFilm = {
        showNewRollFilm = false
        viewModel.clearFilmAddedReceipt()
        showAddFilm = true
      }
    )
  }
  if (showAddFilm) AddFilmDialog(addingFilm, { showAddFilm = false; showNewRollFilm = true }, viewModel::addCustomFilmStock)
  if (showNewRoll && !showNewRollFilm && !showAddFilm) {
    val capacity = newCapacity.toIntOrNull()
    val lens = newFocalLength.toIntOrNull()
    val validName = newRollName.trim().length in 1..100
    AlertDialog(
      onDismissRequest = { if (!saving) { showNewRoll = false; requestedNewRoll = false } },
      modifier = Modifier.safeDrawingPadding(),
      properties = DialogProperties(decorFitsSystemWindows = false),
      title = { Text("Start a new roll") },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            newRollName, { newRollName = it }, label = { Text("Roll name") }, singleLine = true, enabled = !saving,
            isError = newRollName.isNotBlank() && !validName,
            supportingText = if (newRollName.trim().length > 100) { { Text("Use 100 characters or fewer.") } } else null
          )
          OutlinedButton(enabled = !saving, onClick = { showNewRollFilm = true },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Choose roll film" }) {
            Text(draftFilm?.let { "Film: ${it.name}" } ?: "Choose film · ISO $newIso")
          }
          Box {
            OutlinedButton(enabled = !saving, onClick = { showCameraChoices = true }) {
              Text("Camera: " + (draftCamera?.name ?: "Generic camera"))
            }
            DropdownMenu(expanded = showCameraChoices, onDismissRequest = { showCameraChoices = false }) {
              DropdownMenuItem(text = { Text("Generic camera") }, onClick = { newCameraId = null; showCameraChoices = false })
              cameras.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.name) }, onClick = {
                  newCameraId = choice.id
                  newFormatName = choice.format
                  newFocalLength = choice.focalLengthMm.toString()
                  newCapacity = (CameraFormat.entries.firstOrNull { it.name == choice.format } ?: CameraFormat.FORMAT_35MM).standardFramesPerRoll.toString()
                  showCameraChoices = false
                })
              }
            }
          }
          if (draftCamera == null) {
            Box {
              OutlinedButton(enabled = !saving, onClick = { showFormatChoices = true }) { Text("Format: ${draftFormat.displayName}") }
              DropdownMenu(expanded = showFormatChoices, onDismissRequest = { showFormatChoices = false }) {
                CameraFormat.entries.forEach { choice ->
                  DropdownMenuItem(text = { Text(choice.displayName) }, onClick = {
                    newFormatName = choice.name
                    newCapacity = choice.standardFramesPerRoll.toString()
                    newFocalLength = DepthOfFieldCalculator.getDefaultFocalLength(choice).toString()
                    showFormatChoices = false
                  })
                }
              }
            }
            OutlinedTextField(
              newFocalLength, { newFocalLength = it }, label = { Text("Lens focal length (mm)") },
              singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              isError = lens == null || lens !in 10..1000
            )
          } else Text("${draftFormat.displayName} · ${draftCamera.focalLengthMm} mm", style = MaterialTheme.typography.bodyMedium)
          Text("Processing", style = MaterialTheme.typography.titleMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..4).forEach { stops ->
              FilterChip(
                selected = newPushStops == stops,
                enabled = !saving && (draftFilm?.isoForPush(stops) != null || (draftFilm == null && stops == 0)),
                onClick = { newPushStops = stops; newIso = draftFilm?.isoForPush(stops) ?: newIso },
                label = { Text(if (stops == 0) "Normal" else "+" + stops) }
              )
            }
          }
          Text("Meter at EI $newIso", style = MaterialTheme.typography.titleMedium)
          Text("Ask the lab: " + processingLabel(newPushStops), color = MaterialTheme.colorScheme.primary)
          if ((newPushStops ?: 0) > 0) Text("Use this speed for the whole roll. Confirm that your film and lab support this push.", style = MaterialTheme.typography.bodyMedium)
          OutlinedTextField(
            newCapacity, { newCapacity = it }, label = { Text("Frames on this roll") },
            singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = capacity == null || capacity !in 1..1000
          )
          Text("Previous rolls and their saved frames are kept.", style = MaterialTheme.typography.bodyMedium)
        }
      },
      confirmButton = {
        TextButton(
          enabled = !saving && ready && validName && capacity != null && capacity in 1..1000 && lens != null && lens in 10..1000 && newIso in 1..102400,
          onClick = {
            requestedNewRoll = true
            viewModel.createRoll(newRollName.trim(), capacity!!, draftFilm, draftCamera, draftFormat, lens!!, newIso, newPushStops)
          }
        ) { Text(if (saving) "Starting…" else "Start roll") }
      },
      dismissButton = { TextButton(onClick = { showNewRoll = false; requestedNewRoll = false }, enabled = !saving) { Text("Cancel") } }
    )
  }

  if (editingNumber != null && loadedRoll != null) {
    val editingFrame = editingNumber == "frame"
    val number = numberDraft.toIntOrNull()
    val highestSaved = logs.filter { it.rollId == loadedRoll.id }.maxOfOrNull { it.frameNumber } ?: 0
    val minimum = if (editingFrame) highestSaved else loadedRoll.lastFrameNumber.coerceAtLeast(1)
    val maximum = if (editingFrame) loadedRoll.frameCapacity else 1000
    AlertDialog(
      onDismissRequest = { if (!saving) editingNumber = null },
      title = { Text(if (editingFrame) "Set physical frame" else "Roll capacity") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            if (editingFrame) "Enter the last frame you exposed on the camera. This does not delete or renumber saved frames."
            else "Choose how many exposures this roll holds."
          )
          OutlinedTextField(
            numberDraft, { numberDraft = it },
            label = { Text(if (editingFrame) "Last frame exposed" else "Frames on this roll") },
            singleLine = true, enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = number == null || number !in minimum..maximum,
            supportingText = {
              Text(if (minimum <= maximum) "Enter a number from " + minimum + " to " + maximum + "."
              else "Increase the roll capacity before changing the frame.")
            }
          )
          if (editingFrame && number != null && number in minimum..maximum) {
            Text(if (number < loadedRoll.frameCapacity) "The next saved frame will be " + (number + 1) + "."
            else "This roll is full. Finish it or increase its capacity.")
          }
        }
      },
      confirmButton = {
        TextButton(enabled = !saving && number != null && number in minimum..maximum, onClick = {
          submittedNumber = number
          if (editingFrame) viewModel.setCurrentFrame(number!!) else viewModel.setCurrentRollFrameCapacity(number!!)
        }) { Text(if (saving) "Saving…" else "Save") }
      },
      dismissButton = { TextButton(onClick = { editingNumber = null }, enabled = !saving) { Text("Cancel") } }
    )
  }

  activeEntry?.let { entry ->
    AlertDialog(
      onDismissRequest = { if (!saving) editingEntryId = null },
      title = { Text("Frame " + entry.frameNumber) },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(entry.apertureText + " · " + ExposureCalculator.formatSeconds(entry.correctedSeconds))
          Text(listOfNotNull(entry.boxIso?.let { "Box ISO " + it }, "Exposed at EI " + entry.iso).joinToString(" · "))
          val photo = viewModel.photoFile(entry.photoFileName)?.takeIf { it.isFile }
          if (photo != null) {
            AsyncImage(model = photo, contentDescription = "Reference photo for frame ${entry.frameNumber}",
              modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
          } else if (entry.photoFileName != null) {
            Text("Photo unavailable on this device.", style = MaterialTheme.typography.bodyMedium)
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !saving, onClick = {
              photoTargetId = entry.id
              photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (entry.photoFileName == null) "Add photo" else "Replace photo") }
            if (entry.photoFileName != null) TextButton(enabled = !saving, onClick = { viewModel.updateLogPhoto(entry.id, null) }) {
              Text("Remove photo")
            }
          }
          Text("Reference photos are not included in cloud backups or CSV.", style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(
            notesDraft, { notesDraft = it }, label = { Text("Note") },
            placeholder = { Text("Subject, filter, location, or anything to remember") },
            minLines = 3, maxLines = 8, enabled = !saving, modifier = Modifier.fillMaxWidth(),
            isError = notesDraft.length > 5000,
            supportingText = if (notesDraft.length > 5000) { { Text("Use 5,000 characters or fewer.") } } else null
          )
        }
      },
      confirmButton = {
        TextButton(enabled = !saving && notesDraft.length <= 5000 && notesDraft != entry.notes, onClick = {
          submittedNotes = notesDraft
          viewModel.updateLogNotes(entry.id, notesDraft)
        }) { Text(if (saving) "Saving…" else "Save note") }
      },
      dismissButton = { TextButton(onClick = { editingEntryId = null }, enabled = !saving) { Text("Close") } }
    )
  }

  logs.firstOrNull { it.id == pendingDeleteId }?.let { entry ->
    AlertDialog(
      onDismissRequest = { pendingDeleteId = null },
      title = { Text("Delete frame " + entry.frameNumber + "?") },
      text = { Text("This removes the saved exposure from " + entry.rollName + ". It cannot be undone.") },
      confirmButton = {
        TextButton(enabled = !saving, onClick = { viewModel.deleteLogEntry(entry.id); pendingDeleteId = null }) {
          Text("Delete frame", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Keep frame") } }
    )
  }
  exportError?.let { message ->
    AlertDialog(onDismissRequest = { exportError = null }, title = { Text("Sharing unavailable") },
      text = { Text(message) }, confirmButton = { TextButton(onClick = { exportError = null }) { Text("OK") } })
  }
}

@Composable
private fun RollCard(roll: FilmRoll, state: String, onClick: () -> Unit) {
  Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(state, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(roll.name, style = MaterialTheme.typography.titleLarge)
        Text(rollFilmSummary(roll), style = MaterialTheme.typography.bodyMedium)
        Text(rollProcessingSummary(roll), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text("Frame " + roll.lastFrameNumber + " of " + roll.frameCapacity, style = MaterialTheme.typography.bodyMedium)
      }
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
  }
}

private fun rollFilmSummary(roll: FilmRoll): String = listOfNotNull(
  roll.filmStockName.ifBlank { "Unlisted film" },
  roll.boxIso?.let { "Box ISO " + it },
  "EI " + roll.iso,
  formatName(roll.format)
).joinToString(" · ")

private fun rollProcessingSummary(roll: FilmRoll): String =
  if (roll.pushStops == null) processingLabel(null) else "Processing requested: " + processingLabel(roll.pushStops)

private fun formatName(name: String): String = CameraFormat.entries.firstOrNull { it.name == name }?.displayName ?: name

@Composable
private fun FilmLogRow(entry: FilmLogEntry, photo: File?, onOpen: () -> Unit, onDelete: () -> Unit) {
  val date = remember(entry.timestamp) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.timestamp)) }
  val hasCorrection = !entry.isManual && entry.correctedSeconds.isFinite() &&
    entry.correctedSeconds > 0.0 && entry.correctedSeconds != entry.rawSeconds
  Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      if (photo != null) {
        AsyncImage(model = photo, contentDescription = "Reference photo for frame ${entry.frameNumber}",
          modifier = Modifier.padding(end = 12.dp).size(64.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Frame " + entry.frameNumber, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(entry.apertureText + " · " + ExposureCalculator.formatSeconds(entry.correctedSeconds), style = MaterialTheme.typography.titleLarge)
        if (entry.isManual) Text("Manual record", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (entry.photoFileName != null && photo == null) Text("Photo unavailable", style = MaterialTheme.typography.labelMedium)
        if (hasCorrection) Text(
          "Metered " + ExposureCalculator.formatSeconds(entry.rawSeconds) + " · film correction applied",
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(listOfNotNull(entry.filmStockName, entry.boxIso?.let { "Box ISO " + it }, "Exposed at EI " + entry.iso, entry.format).joinToString(" · "),
          style = MaterialTheme.typography.bodyMedium)
        if (entry.cameraName.isNotBlank() || entry.focalLengthMm > 0) {
          Text(listOfNotNull(entry.cameraName.takeIf { it.isNotBlank() }, entry.focalLengthMm.takeIf { it > 0 }?.let { "$it mm" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
          date + if (entry.hasMeterReading) " · EV " + String.format(Locale.getDefault(), "%.1f", entry.evValue) else "",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(entry.notes.ifBlank { "Tap to add a note" }, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
          color = if (entry.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
      }
      IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete frame " + entry.frameNumber) }
    }
  }
}

@Composable
fun SettingsScreen(
  viewModel: LightMeterViewModel,
  modifier: Modifier = Modifier,
  onOpenSunny16: () -> Unit = {},
  openCameraSetup: Boolean = false
) {
  val stocks by viewModel.allStocks.collectAsStateWithLifecycle()
  val addingFilm by viewModel.isAddingFilm.collectAsStateWithLifecycle()
  val filmAddedId by viewModel.filmAddedId.collectAsStateWithLifecycle()
  val saving by viewModel.isSaving.collectAsStateWithLifecycle()
  val cameras by viewModel.allCameraSetups.collectAsStateWithLifecycle()
  val currentCamera by viewModel.currentCameraSetup.collectAsStateWithLifecycle()
  val savingCamera by viewModel.isSavingCameraSetup.collectAsStateWithLifecycle()
  val cameraSavedId by viewModel.cameraSetupSavedId.collectAsStateWithLifecycle()
  val activeSource by viewModel.measurementSource.collectAsStateWithLifecycle()
  val offsets by viewModel.calibrationOffsets.collectAsStateWithLifecycle()
  var calibrationSourceName by rememberSaveable {
    mutableStateOf(if (viewModel.measurementSource.value == MeasurementSource.SENSOR) MeasurementSource.SENSOR.name else MeasurementSource.CAMERA.name)
  }
  val calibrationSource = MeasurementSource.valueOf(calibrationSourceName)
  val offset = offsets[calibrationSource] ?: 0f
  var showManualCalibration by rememberSaveable { mutableStateOf(false) }
  var showCalibration by rememberSaveable { mutableStateOf(false) }
  var hapticsEnabled by remember { mutableStateOf(viewModel.hapticManager.isHapticsEnabled) }
  var showAddFilm by rememberSaveable { mutableStateOf(false) }
  var pendingDeleteId by rememberSaveable { mutableStateOf<Int?>(null) }
  var filmDetailsId by rememberSaveable { mutableStateOf<Int?>(null) }
  var showCamera by rememberSaveable { mutableStateOf(false) }
  var editingCameraId by rememberSaveable { mutableStateOf<Int?>(null) }
  var cameraOpenHandled by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(filmAddedId) {
    if (filmAddedId != null) { showAddFilm = false; viewModel.clearFilmAddedReceipt() }
  }
  LaunchedEffect(cameraSavedId) {
    if (cameraSavedId != null) { showCamera = false; viewModel.clearCameraSetupSavedReceipt() }
  }
  LaunchedEffect(openCameraSetup) {
    if (openCameraSetup && !cameraOpenHandled) {
      editingCameraId = null
      viewModel.clearCameraSetupSavedReceipt()
      showCamera = true
      cameraOpenHandled = true
    } else if (!openCameraSetup) cameraOpenHandled = false
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Cameras", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
          TextButton(onClick = {
            editingCameraId = null
            viewModel.clearCameraSetupSavedReceipt()
            showCamera = true
          }, enabled = !savingCamera) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add camera", Modifier.padding(start = 4.dp))
          }
        }
        if (cameras.isEmpty()) Text("Save your camera's shutter speeds and aperture range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else TextButton(onClick = { viewModel.selectCameraSetup(null) }, enabled = !saving) { Text("Use without a saved camera") }
      }
      items(cameras, key = { it.id }) { camera ->
        Row(Modifier.fillMaxWidth().selectable(
          selected = currentCamera?.id == camera.id, enabled = !saving, role = Role.RadioButton,
          onClick = { viewModel.selectCameraSetup(camera.id) }
        ),
          verticalAlignment = Alignment.CenterVertically) {
          RadioButton(selected = currentCamera?.id == camera.id, onClick = null)
          Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(camera.name, style = MaterialTheme.typography.titleMedium)
            Text(formatName(camera.format) + " · " + camera.focalLengthMm + " mm", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          IconButton(enabled = !savingCamera, onClick = {
            editingCameraId = camera.id
            viewModel.clearCameraSetupSavedReceipt()
            showCamera = true
          }) { Icon(Icons.Default.Edit, contentDescription = "Edit " + camera.name) }
        }
      }
      item {
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Calibration", style = MaterialTheme.typography.titleLarge)
            Text("Compare a held reading with a trusted meter. Camera and light-sensor adjustments are saved separately.",
              style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { showCalibration = true }, enabled = activeSource != MeasurementSource.SUNNY_16 && !saving) {
              Text("Compare with a reference meter")
            }
            if (activeSource == MeasurementSource.SUNNY_16) Text("Choose Camera or Light sensor on the meter before calibrating.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { showManualCalibration = !showManualCalibration }) {
              Text(if (showManualCalibration) "Hide manual adjustment" else "Manual adjustment")
            }
            if (showManualCalibration) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = calibrationSource == MeasurementSource.CAMERA,
                  onClick = { calibrationSourceName = MeasurementSource.CAMERA.name },
                  label = { Text("Camera") }, modifier = Modifier.weight(1f)
                )
                FilterChip(
                  selected = calibrationSource == MeasurementSource.SENSOR,
                  onClick = { calibrationSourceName = MeasurementSource.SENSOR.name },
                  label = { Text("Light sensor") }, modifier = Modifier.weight(1f)
                )
              }
              Text(String.format(Locale.getDefault(), "%+.1f EV", offset), style = MaterialTheme.typography.headlineSmall)
              Slider(
                value = offset, onValueChange = { viewModel.setCalibrationOffset(it, calibrationSource) },
                valueRange = -3f..3f, steps = 11, enabled = !saving,
                modifier = Modifier.semantics { contentDescription = "Calibration offset in EV" }
              )
              TextButton(enabled = offset != 0f && !saving, onClick = { viewModel.setCalibrationOffset(0f, calibrationSource) }) {
                Text("Reset this source")
              }
            }
          }
        }
      }
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text("Haptic feedback", style = MaterialTheme.typography.titleMedium)
            Text("A small tick when you adjust exposure.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(
            modifier = Modifier.semantics { contentDescription = "Haptic feedback" }, checked = hapticsEnabled,
            onCheckedChange = { hapticsEnabled = it; viewModel.setHapticsEnabled(it) }
          )
        }
      }
      item { TextButton(onClick = onOpenSunny16) { Text("Sunny 16 guide") }; HorizontalDivider() }
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Film library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
          TextButton(onClick = { viewModel.clearFilmAddedReceipt(); showAddFilm = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add film", Modifier.padding(start = 4.dp))
          }
        }
      }
      items(stocks, key = { it.id }) { film ->
        Row(
          Modifier.fillMaxWidth().clickable { filmDetailsId = film.id },
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(film.name, style = MaterialTheme.typography.titleMedium)
            Text(film.brand + " · ISO " + film.iso + " · " + film.type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(film.correctionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
          }
          if (film.isCustom) {
            IconButton(onClick = { pendingDeleteId = film.id }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete " + film.name) }
          } else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
      }
    }
  }

  if (showCamera) CameraSetupDialog(
    initial = cameras.firstOrNull { it.id == editingCameraId },
    saving = savingCamera,
    onDismiss = { showCamera = false },
    onSave = viewModel::saveCameraSetup
  )
  if (showCalibration) CalibrationDialog(viewModel, onDismiss = { showCalibration = false })
  stocks.firstOrNull { it.id == filmDetailsId }?.let { FilmDetailsDialog(it) { filmDetailsId = null } }
  if (showAddFilm) AddFilmDialog(addingFilm, { showAddFilm = false }, viewModel::addCustomFilmStock)
  stocks.firstOrNull { it.id == pendingDeleteId }?.let { film ->
    AlertDialog(
      onDismissRequest = { pendingDeleteId = null },
      title = { Text("Delete " + film.name + "?") },
      text = { Text("This removes the custom film profile. Saved exposures are kept.") },
      confirmButton = {
        TextButton(enabled = !saving, onClick = { viewModel.deleteFilmStock(film.id); pendingDeleteId = null }) {
          Text("Delete film", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Keep film") } }
    )
  }
}
