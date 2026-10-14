package com.example.ui.meter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CameraFormat
import com.example.data.model.processingLabel
import com.example.sensor.DepthOfFieldCalculator
import com.example.sensor.ExposureCalculator
import com.example.ui.components.CameraViewfinder
import com.example.ui.components.ExposureTimerDialog
import com.example.ui.components.ExposureAdjustmentsDialog
import com.example.ui.components.FilmDetailsDialog
import com.example.ui.components.FilmStockPickerSheet
import com.example.ui.components.ManualExposureDialog
import com.example.ui.components.formatCompensation
import com.example.ui.components.formatExposureInput
import java.util.Locale
import kotlin.math.abs

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun LightMeterScreen(
  viewModel: LightMeterViewModel,
  modifier: Modifier = Modifier,
  onOpenSunny16: () -> Unit = {},
  onOpenRolls: () -> Unit = {},
  onOpenCameraSetup: () -> Unit = {}
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val compactViewfinderHeight = (LocalConfiguration.current.screenHeightDp - 700).coerceIn(160, 280)
  val view = LocalView.current
  val lux by viewModel.sensorManager.lux.collectAsStateWithLifecycle()
  val source by viewModel.measurementSource.collectAsStateWithLifecycle()
  val status by viewModel.readingStatus.collectAsStateWithLifecycle()
  val hasReading by viewModel.hasReading.collectAsStateWithLifecycle()
  val measuredEv by viewModel.measuredEv100.collectAsStateWithLifecycle()
  val message by viewModel.readingMessage.collectAsStateWithLifecycle()
  val format by viewModel.selectedFormat.collectAsStateWithLifecycle()
  val stock by viewModel.selectedFilmStock.collectAsStateWithLifecycle()
  val customIso by viewModel.customIso.collectAsStateWithLifecycle()
  val pushStops by viewModel.pushStops.collectAsStateWithLifecycle()
  val focalLength by viewModel.focalLengthMm.collectAsStateWithLifecycle()
  val apertureIndex by viewModel.selectedApertureIndex.collectAsStateWithLifecycle()
  val shutterIndex by viewModel.selectedShutterIndex.collectAsStateWithLifecycle()
  val mode by viewModel.exposureMode.collectAsStateWithLifecycle()
  val calibration by viewModel.calibrationOffsetEv.collectAsStateWithLifecycle()
  val focusDistance by viewModel.focusDistanceMeters.collectAsStateWithLifecycle()
  val dofExpanded by viewModel.isDofExpanded.collectAsStateWithLifecycle()
  val savedEntry by viewModel.savedEntry.collectAsStateWithLifecycle()
  val saving by viewModel.isSaving.collectAsStateWithLifecycle()
  val ready by viewModel.setupReady.collectAsStateWithLifecycle()
  val showFilm by viewModel.showStockPickerSheet.collectAsStateWithLifecycle()
  val showTimer by viewModel.showTimerDialog.collectAsStateWithLifecycle()
  val stocks by viewModel.allStocks.collectAsStateWithLifecycle()
  val cameraSetup by viewModel.currentCameraSetup.collectAsStateWithLifecycle()
  val cameraSetups by viewModel.allCameraSetups.collectAsStateWithLifecycle()
  val roll by viewModel.currentRoll.collectAsStateWithLifecycle()
  val rollName by viewModel.currentRollName.collectAsStateWithLifecycle()
  val frameCount by viewModel.activeRollFrameCount.collectAsStateWithLifecycle()
  val compensation by viewModel.exposureCompensation.collectAsStateWithLifecycle()
  val filterStops by viewModel.filterStops.collectAsStateWithLifecycle()
  var picker by rememberSaveable { mutableStateOf<String?>(null) }
  var showAdjustments by rememberSaveable { mutableStateOf(false) }
  var showFilmDetails by rememberSaveable { mutableStateOf(false) }
  var showManualRecord by rememberSaveable { mutableStateOf(false) }
  var freshManualFrame by rememberSaveable { mutableStateOf(false) }

  val iso = customIso ?: stock?.iso ?: 400
  val held = status == ReadingStatus.HELD
  val apertureLabels = viewModel.apertureLabels
  val shutterLabels = viewModel.shutterLabels
  val aperture = viewModel.availableApertures[apertureIndex]
  val selectedSeconds = viewModel.availableShutterSeconds[shutterIndex]
  val rawSeconds = viewModel.getRawShutterSeconds()
  val correctedSeconds = viewModel.getCorrectedShutterSeconds()
  val sceneSeconds = viewModel.getBaseMeteredSeconds()
  val adjustedSeconds = viewModel.getFilteredMeteredSeconds()
  val exposureSupported = rawSeconds != null && correctedSeconds != null
  val requiresBulb = mode == ExposureMode.APERTURE_PRIORITY && correctedSeconds != null && correctedSeconds >= 1.0 &&
    viewModel.availableShutterSeconds.none { abs(it - correctedSeconds) < 1e-6 }
  val ev100 = if (measuredEv != null) viewModel.computeEv100() else Double.NaN
  val evDelta = viewModel.getEvDelta()
  val exposureGuidance = viewModel.getExposureGuidance()
  val rollGuidance = viewModel.getCurrentRollGuidance()
  val canSaveRoll = viewModel.canSaveToCurrentRoll()
  val adjustmentsActive = abs(compensation) > 0.0001 || filterStops > 0.0001
  val showMeteredOnly = mode == ExposureMode.APERTURE_PRIORITY && rawSeconds != null && correctedSeconds == null &&
    stock?.hasReciprocityProfile == false && evDelta != null && abs(evDelta) <= 0.6

  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> viewModel.resumeMetering()
        Lifecycle.Event.ON_PAUSE -> viewModel.pauseMetering()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      viewModel.resumeMetering()
    }
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.pauseMetering()
    }
  }
  LaunchedEffect(savedEntry?.id) {
    if (savedEntry != null) {
      viewModel.hapticManager.performShutterRelease(view)
      showManualRecord = false
    }
  }

  val preview: @Composable (Int) -> Unit = { viewportHeight ->
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
          onClick = { picker = "source" },
          enabled = !held && !saving,
          contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
          val sourceName = when (source) {
            MeasurementSource.CAMERA -> "Camera"
            MeasurementSource.SENSOR -> "Light sensor"
            MeasurementSource.SUNNY_16 -> "Sunny 16"
          }
          val stateName = when (status) {
            ReadingStatus.WAITING -> if (hasReading) "Adjusting" else "Measuring"
            ReadingStatus.UNAVAILABLE -> "Unavailable"
            ReadingStatus.HELD -> if (source == MeasurementSource.SUNNY_16) "Estimate held" else "Held"
            ReadingStatus.LIVE -> if (source == MeasurementSource.SUNNY_16) "Estimate" else "Live"
          }
          Text("$sourceName · $stateName", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        }
        TextButton(onClick = { picker = "format" }, enabled = !held && !saving) {
          Text(format.displayName)
          Icon(Icons.Default.ExpandMore, contentDescription = null)
        }
      }

      if (source == MeasurementSource.CAMERA) {
          CameraViewfinder(
            format = format,
            focalLengthMm = focalLength,
            onFocalLengthChanged = viewModel::setFocalLength,
            hapticManager = viewModel.hapticManager,
            isLocked = held,
            onToggleLock = viewModel::toggleHold,
            onMeasuredEvChanged = { ev -> viewModel.onOpticalEvMeasured(ev, format, focalLength) },
            onCameraStatusChanged = viewModel::setCameraStatus,
            onMeteringAreaChanged = viewModel::invalidateCameraReading,
            viewportHeightDp = viewportHeight
          )
      } else {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              if (source == MeasurementSource.SENSOR) "Light reaching your phone" else "An estimate from the conditions",
              style = MaterialTheme.typography.titleMedium
            )
            Text(
              if (source == MeasurementSource.SENSOR) {
                if (hasReading) String.format(Locale.getDefault(), "%.0f lux", lux) else message ?: "Waiting for the light sensor"
              } else {
                if (hasReading) "Sunny 16 exposure ready" else "Choose the light and weather conditions."
              },
              style = MaterialTheme.typography.bodyLarge
            )
            if (source == MeasurementSource.SUNNY_16) {
              TextButton(onClick = onOpenSunny16) { Text("Choose conditions") }
            } else {
              Text("Keep the phone's light sensor uncovered.", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
    }
  }

  val controls: @Composable () -> Unit = {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      TextButton(onClick = { picker = "mode" }, contentPadding = PaddingValues(horizontal = 0.dp), enabled = !saving) {
        Text(when (mode) {
          ExposureMode.APERTURE_PRIORITY -> "Choose aperture"
          ExposureMode.SHUTTER_PRIORITY -> "Choose shutter"
          ExposureMode.MANUAL_MATCH -> "Manual exposure"
        })
        Icon(Icons.Default.ExpandMore, contentDescription = null)
      }
      ExposureReadout(
        mode = mode,
        apertureLabel = apertureLabels[apertureIndex],
        shutterLabel = if (showMeteredOnly) ExposureCalculator.formatSeconds(rawSeconds!!)
          else if (mode == ExposureMode.APERTURE_PRIORITY) correctedSeconds?.let(ExposureCalculator::formatSeconds) ?: "—"
          else ExposureCalculator.formatSeconds(selectedSeconds),
        available = hasReading && (exposureSupported || showMeteredOnly),
        ev100 = ev100,
        evDelta = evDelta ?: 0.0,
        enabled = !saving,
        onPickAperture = { picker = "aperture" },
        onPickShutter = { picker = "shutter" },
        resultLabel = if (showMeteredOnly) "Metered shutter" else if (requiresBulb) "Bulb exposure" else null
      )

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { showAdjustments = true }, enabled = !saving) {
          Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
          Text("Exposure adjustments", Modifier.padding(start = 6.dp))
        }
        if (adjustmentsActive) {
          TextButton(onClick = viewModel::resetExposureAdjustments, enabled = !saving) { Text("Reset adjustments") }
        }
      }
      if (adjustmentsActive) {
        Text(
          "Compensation ${formatCompensation(compensation)} · filter ${formatExposureInput(filterStops)} stops",
          style = MaterialTheme.typography.bodyMedium
        )
        if (hasReading && mode == ExposureMode.APERTURE_PRIORITY) {
          Text(
            "Scene ${sceneSeconds?.let(ExposureCalculator::formatSeconds) ?: "—"} · after adjustments ${adjustedSeconds?.let(ExposureCalculator::formatSeconds) ?: "—"} · film exposure ${correctedSeconds?.let(ExposureCalculator::formatSeconds) ?: "unavailable"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (hasReading && mode != ExposureMode.MANUAL_MATCH && evDelta != null && abs(evDelta) > 0.6) {
        Text("Outside the available exposure range. Choose another aperture or shutter speed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
      }
      if (hasReading && exposureSupported && mode != ExposureMode.MANUAL_MATCH && evDelta != null && abs(evDelta) in 0.15..0.6) {
        Text("Closest dial setting · ${exposureBalanceLabel(evDelta)} target", style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (hasReading && !exposureSupported && mode == ExposureMode.APERTURE_PRIORITY && adjustedSeconds != null) {
        Text("Metered target ${ExposureCalculator.formatSeconds(adjustedSeconds)} · recommendation unavailable", style = MaterialTheme.typography.bodyMedium)
      }
      if (hasReading && exposureGuidance != null) {
        Text(exposureGuidance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (stock != null && (exposureGuidance != null || !exposureSupported)) {
        TextButton(onClick = { showFilmDetails = true }) { Text("Film correction details") }
      }
      if (hasReading && rawSeconds != null && correctedSeconds != null && correctedSeconds >= 1.0) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(Modifier.weight(1f)) {
            Text((if (requiresBulb) "Use Bulb · " else "Exposure ") + ExposureCalculator.formatSeconds(correctedSeconds), style = MaterialTheme.typography.bodyMedium)
            if (correctedSeconds > rawSeconds) {
              Text("${stock?.name} · metered ${ExposureCalculator.formatSeconds(rawSeconds)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          TextButton(onClick = { viewModel.setShowTimerDialog(true) }) {
            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Timer", Modifier.padding(start = 6.dp))
          }
        }
      }

      if (source == MeasurementSource.CAMERA && message != null && !hasReading) {
        Text(message!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      HorizontalDivider(Modifier.padding(top = 8.dp))
      TextButton(
        onClick = viewModel::toggleDofExpanded,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 10.dp)
      ) {
        Text("Depth of field", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(if (dofExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (dofExpanded) "Collapse depth of field" else "Expand depth of field")
      }
      if (dofExpanded) {
        val dof = DepthOfFieldCalculator.calculate(focalLength.toDouble(), aperture, focusDistance, format)
        Text("Focus at ${DepthOfFieldCalculator.formatDistance(focusDistance)}")
        Slider(
          value = focusDistance.toFloat().coerceIn(0.5f, 30f),
          onValueChange = { viewModel.setFocusDistance(it.toDouble()) },
          valueRange = 0.5f..30f,
          modifier = Modifier.semantics { contentDescription = "Focus distance in metres" }
        )
        Text(
          "${DepthOfFieldCalculator.formatDistance(dof.nearMeters)} – ${DepthOfFieldCalculator.formatDistance(dof.farMeters)} in focus",
          style = MaterialTheme.typography.titleMedium
        )
        TextButton(onClick = viewModel::setFocusToHyperfocal) {
          Text("Focus at hyperfocal · ${DepthOfFieldCalculator.formatDistance(dof.hyperfocalMeters)}")
        }
      }
      TextButton(onClick = onOpenSunny16, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Estimate with Sunny 16", Modifier.padding(start = 8.dp))
      }
      if (calibration != 0f) {
        Text(String.format(Locale.getDefault(), "Calibration %+.1f EV", calibration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }

  Column(modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.weight(1f)) {
      val wide = maxWidth >= 600.dp && LocalDensity.current.fontScale < 1.5f
      // Reading-dependent footer changes must not resize the camera and reset its selected area.
      val viewportHeight = if (wide) 330 else compactViewfinderHeight
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = { picker = "camera" }, enabled = ready && !saving) {
            Text(cameraSetup?.name ?: "Camera setup")
            Icon(Icons.Default.ExpandMore, contentDescription = null)
          }
          TextButton(onClick = onOpenRolls, enabled = !saving) {
            Text(when {
              roll?.isFinished == true -> "$rollName · finished"
              roll != null && frameCount >= roll!!.frameCapacity -> "$rollName · full"
              else -> "$rollName · next frame ${frameCount + 1}"
            })
          }
        }
        if (rollGuidance != null) {
          Text(rollGuidance, style = MaterialTheme.typography.bodyMedium)
          TextButton(onClick = onOpenRolls, enabled = !saving) { Text("Open rolls") }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilledTonalButton(
            onClick = { viewModel.setShowStockPickerSheet(true) },
            enabled = ready && !saving,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
          ) {
            Column(Modifier.weight(1f)) {
              Text(stock?.name ?: "Choose film", style = MaterialTheme.typography.titleSmall)
              Text("EI $iso · ${processingLabel(pushStops)}", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null)
          }
          TextButton(onClick = { picker = "lens" }, enabled = !held && !saving) {
            Text("$focalLength mm")
            Icon(Icons.Default.ExpandMore, contentDescription = null)
          }
        }
        if (wide) {
          Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(Modifier.weight(1.15f)) { preview(viewportHeight) }
            Box(Modifier.weight(1f)) { controls() }
          }
        } else {
          preview(viewportHeight)
          controls()
        }
      }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (held) {
          val manualRecord = savedEntry?.takeIf { it.isManual }
          HeldExposureSummary(
            iso = manualRecord?.iso ?: iso,
            apertureLabel = manualRecord?.apertureText ?: apertureLabels[apertureIndex],
            actualSeconds = manualRecord?.correctedSeconds ?: correctedSeconds,
            manual = manualRecord != null
          )
        }
        Button(
          onClick = {
            viewModel.hapticManager.performDialClick(view)
            viewModel.toggleHold()
          },
          enabled = (held || status == ReadingStatus.LIVE) && !saving,
          modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) {
          Text(if (held) "Resume metering" else "Hold reading")
        }
        if (held && savedEntry == null) {
          OutlinedButton(
            onClick = { viewModel.logExposure() },
            enabled = ready && !saving && exposureSupported && canSaveRoll,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
          ) {
            if (saving) {
              CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
              Spacer(Modifier.size(8.dp))
            }
            Text(if (saving) "Saving frame…" else "Save frame")
          }
        }
        if ((!hasReading || !exposureSupported) && savedEntry == null) {
          TextButton(
            onClick = {
              freshManualFrame = false
              showManualRecord = true
            },
            enabled = ready && !saving && canSaveRoll,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
          ) { Text("Record manual exposure") }
        }
        savedEntry?.let { saved ->
          Row(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Frame ${saved.frameNumber} saved" + if (saved.isManual) " · manual" else "", modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::undoLastSavedExposure, enabled = !saving) { Text("Undo") }
          }
          if (saved.isManual) {
            TextButton(
              enabled = !saving && canSaveRoll,
              onClick = {
                if (viewModel.beginNextManualFrame()) {
                  freshManualFrame = true
                  showManualRecord = true
                }
              },
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Next manual frame") }
          }
        }

      }
    }
  }

  if (showFilm) {
    FilmStockPickerSheet(
      stocks = stocks,
      selectedStock = stock,
      customIso = customIso,
      pushStops = pushStops,
      hapticManager = viewModel.hapticManager,
      onApply = viewModel::applyFilmSettings,
      needsConfirmation = viewModel::needsFilmSettingsConfirmation,
      onDismiss = { viewModel.setShowStockPickerSheet(false) }
    )
  }
  if (showAdjustments) {
    ExposureAdjustmentsDialog(
      exposureCompensation = compensation,
      filterStops = filterStops,
      onCompensationChanged = viewModel::setExposureCompensation,
      onFilterStopsChanged = viewModel::setFilterStops,
      onReset = viewModel::resetExposureAdjustments,
      onDismiss = { showAdjustments = false }
    )
  }
  if (showFilmDetails) stock?.let { FilmDetailsDialog(it, onDismiss = { showFilmDetails = false }) }
  if (showManualRecord) {
    ManualExposureDialog(
      initialAperture = aperture,
      initialSeconds = if (freshManualFrame) 0.0 else correctedSeconds ?: rawSeconds ?: selectedSeconds,
      saving = saving,
      onSave = viewModel::recordManualExposure,
      onDismiss = { showManualRecord = false }
    )
  }
  if (showTimer) {
    ExposureTimerDialog(
      rawSeconds = rawSeconds ?: 0.0,
      correctedSeconds = correctedSeconds ?: 0.0,
      stockName = stock?.name ?: "ISO $iso",
      hapticManager = viewModel.hapticManager,
      onDismiss = { viewModel.setShowTimerDialog(false) }
    )
  }
  picker?.let { kind ->
    val formats = CameraFormat.entries
    val focalLengths = (DepthOfFieldCalculator.getCommonFocalLengths(format) + focalLength).distinct().sorted()
    val labels = when (kind) {
      "camera" -> listOf("Generic camera") + cameraSetups.map { it.name } + "Add camera…"
      "source" -> listOf("Camera · frame metering", "Light sensor", "Sunny 16 · estimate")
      "format" -> formats.map { it.displayName }
      "lens" -> focalLengths.map { "$it mm" }
      "mode" -> listOf("Choose aperture", "Choose shutter", "Manual exposure")
      "aperture" -> apertureLabels
      else -> shutterLabels
    }
    val selected = when (kind) {
      "camera" -> cameraSetups.indexOfFirst { it.id == cameraSetup?.id } + 1
      "source" -> source.ordinal
      "format" -> formats.indexOf(format)
      "lens" -> focalLengths.indexOf(focalLength)
      "mode" -> mode.ordinal
      "aperture" -> apertureIndex
      else -> shutterIndex
    }
    ValuePicker(
      title = when (kind) {
        "camera" -> "Camera setup"
        "source" -> "Metering source"
        "format" -> "Film format"
        "lens" -> "Lens focal length"
        "mode" -> "Exposure control"
        "aperture" -> "Aperture"
        else -> "Shutter speed"
      },
      labels = labels,
      selected = selected,
      onDismiss = { picker = null },
      onSelect = { index ->
        viewModel.hapticManager.performDialClick(view)
        when (kind) {
          "camera" -> when (index) {
            0 -> if (cameraSetup != null) viewModel.selectCameraSetup(null)
            cameraSetups.size + 1 -> onOpenCameraSetup()
            else -> cameraSetups.getOrNull(index - 1)?.let { camera ->
              if (camera.id != cameraSetup?.id) viewModel.selectCameraSetup(camera.id)
            }
          }
          "source" -> if (index == 2) onOpenSunny16() else viewModel.setMeasurementSource(MeasurementSource.entries[index])
          "format" -> viewModel.setFormat(formats[index])
          "lens" -> viewModel.setFocalLength(focalLengths[index])
          "mode" -> viewModel.setExposureMode(ExposureMode.entries[index])
          "aperture" -> viewModel.setApertureIndex(index)
          "shutter" -> viewModel.setShutterIndex(index)
        }
        picker = null
      }
    )
  }
}

@Composable
internal fun ExposureReadout(
  mode: ExposureMode,
  apertureLabel: String,
  shutterLabel: String,
  available: Boolean,
  ev100: Double,
  evDelta: Double,
  enabled: Boolean,
  onPickAperture: () -> Unit,
  onPickShutter: () -> Unit,
  resultLabel: String? = null
) {
  val manual = mode == ExposureMode.MANUAL_MATCH
  val choosingAperture = mode == ExposureMode.APERTURE_PRIORITY
  BoxWithConstraints {
    val stack = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
    val result: @Composable () -> Unit = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          resultLabel ?: when {
            manual -> "Exposure balance"
            choosingAperture -> "Recommended shutter"
            else -> "Recommended aperture"
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          when {
            !available -> "—"
            manual -> exposureBalanceLabel(evDelta)
            choosingAperture -> shutterLabel
            else -> apertureLabel
          },
          style = if (manual) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displayMedium
        )
        if (available) {
          Text(String.format(Locale.getDefault(), "EV₁₀₀ %.1f", ev100), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    val inputs: @Composable () -> Unit = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (choosingAperture || manual) {
          Column {
            Text("Aperture", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onPickAperture, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(apertureLabel) }
          }
        }
        if (!choosingAperture || manual) {
          Column {
            Text("Shutter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onPickShutter, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(shutterLabel) }
          }
        }
      }
    }
    if (stack) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        result()
        inputs()
      }
    } else {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) { result() }
        inputs()
      }
    }
  }
}

internal fun exposureBalanceLabel(delta: Double): String = when {
  abs(delta) < 0.2 -> "Matched"
  delta > 0 -> String.format(Locale.getDefault(), "%.1f stops under", abs(delta))
  else -> String.format(Locale.getDefault(), "%.1f stops over", abs(delta))
}

@Composable
internal fun HeldExposureSummary(iso: Int, apertureLabel: String, actualSeconds: Double?, manual: Boolean = false) {
  val state = if (manual) "Manual exposure saved" else "Reading held"
  val shutter = actualSeconds?.let(ExposureCalculator::formatSeconds) ?: "actual shutter not supplied"
  val spokenShutter = actualSeconds?.let {
    if (it < 1.0) "1 over ${kotlin.math.round(1.0 / it).toInt()} second" else "${formatExposureInput(it)} seconds"
  } ?: "actual shutter not supplied"
  Column(
    Modifier.fillMaxWidth().clearAndSetSemantics {
      contentDescription = "$state. ISO $iso. Aperture ${apertureLabel.replace("f/", "f ")}. Shutter $spokenShutter."
      liveRegion = LiveRegionMode.Polite
    },
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(state, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("$apertureLabel · $shutter · ISO $iso", style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
internal fun ValuePicker(
  title: String,
  labels: List<String>,
  selected: Int,
  onDismiss: () -> Unit,
  onSelect: (Int) -> Unit
) {
  val firstVisible = (selected - 2).coerceIn(0, labels.lastIndex.coerceAtLeast(0))
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstVisible)
  LaunchedEffect(selected, labels) {
    if (labels.isNotEmpty()) listState.scrollToItem(firstVisible)
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      LazyColumn(Modifier.heightIn(max = 360.dp), state = listState) {
        itemsIndexed(labels) { index, label ->
          Surface(
            onClick = { onSelect(index) },
            color = if (index == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
          ) {
            ListItem(
              headlineContent = { Text(label) },
              trailingContent = { if (index == selected) Icon(Icons.Default.Check, contentDescription = "Selected") },
              colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
  )
}
