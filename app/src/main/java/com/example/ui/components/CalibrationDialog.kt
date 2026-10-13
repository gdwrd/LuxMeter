package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sensor.ExposureCalculator
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.meter.MeasurementSource
import com.example.ui.meter.ReadingStatus
import java.util.Locale

@Composable
fun CalibrationDialog(viewModel: LightMeterViewModel, onDismiss: () -> Unit) {
  val sourceName = rememberSaveable { viewModel.measurementSource.value.name }
  val source = MeasurementSource.valueOf(sourceName)
  val activeSource by viewModel.measurementSource.collectAsStateWithLifecycle()
  val readingStatus by viewModel.readingStatus.collectAsStateWithLifecycle()
  val measuredEv by viewModel.measuredEv100.collectAsStateWithLifecycle()
  val readingMessage by viewModel.readingMessage.collectAsStateWithLifecycle()
  val saving by viewModel.isSaving.collectAsStateWithLifecycle()
  var apertureText by rememberSaveable { mutableStateOf("") }
  var shutterText by rememberSaveable { mutableStateOf("") }
  var isoText by rememberSaveable { mutableStateOf(viewModel.activeIso.toString()) }
  val supportedSource = source != MeasurementSource.SUNNY_16
  val sameSource = activeSource == source
  val validReading = supportedSource && sameSource && measuredEv?.isFinite() == true &&
    (readingStatus == ReadingStatus.LIVE || readingStatus == ReadingStatus.HELD)
  val held = validReading && readingStatus == ReadingStatus.HELD
  val aperture = parseLocalizedDecimal(apertureText)
  val seconds = parseReferenceSeconds(shutterText)
  val iso = isoText.trim().toIntOrNull()
  val apertureValid = aperture != null && aperture in 1.0..64.0
  val isoValid = iso != null && iso in 1..102400
  val referenceValid = apertureValid && seconds != null && isoValid
  val proposed = if (held && referenceValid) viewModel.proposeCalibrationOffset(aperture!!, seconds!!, iso!!) else null
  val sourceLabel = if (source == MeasurementSource.CAMERA) "camera" else "light sensor"

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (supportedSource) "Calibrate $sourceLabel" else "Guided calibration") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
          !supportedSource -> Text("Sunny 16 is an estimate and cannot be calibrated. On Meter, choose Camera or Light sensor, take a reading and tap Hold. Then return here.")
          !sameSource -> Text("The measuring source changed. Close this dialog and restart calibration for the source you want to use.")
          !validReading -> {
            Text("1. Capture a reading", style = MaterialTheme.typography.titleMedium)
            Text("On Meter, compare the $sourceLabel with a trusted meter in steady light. Tap Hold, then return here.")
            readingMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
          }
          !held -> {
            Text("1. Hold the reading", style = MaterialTheme.typography.titleMedium)
            Text(if (source == MeasurementSource.CAMERA)
              "Aim both meters at the same evenly lit, neutral subject. Keep the framing and light unchanged."
            else "Place the phone and reference meter in the same steady light. Keep the phone's light sensor uncovered.")
            Button(onClick = viewModel::toggleHold, enabled = !saving) { Text("Hold reading") }
          }
          else -> {
            Text("1. Reading held", style = MaterialTheme.typography.titleMedium)
            Text(String.format(Locale.getDefault(), "Current calibration: %+.1f EV", viewModel.calibrationOffsetFor(source)))
            Text("2. Enter the reference reading", style = MaterialTheme.typography.titleMedium)
            Text("Use the trusted meter's settings for the same light, before any film or filter correction.")
            OutlinedTextField(
              apertureText, { apertureText = it }, label = { Text("Reference aperture (f-number)") },
              modifier = Modifier.fillMaxWidth(), singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              isError = apertureText.isNotBlank() && !apertureValid,
              supportingText = { Text("Enter a value from 1 to 64.") }
            )
            OutlinedTextField(
              shutterText, { shutterText = it }, label = { Text("Reference shutter speed") },
              placeholder = { Text("1/125") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
              isError = shutterText.isNotBlank() && seconds == null,
              supportingText = { Text("Use a fraction or seconds, up to one day.") }
            )
            OutlinedTextField(
              isoText, { isoText = it }, label = { Text("Reference ISO") },
              modifier = Modifier.fillMaxWidth(), singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              isError = isoText.isNotBlank() && !isoValid,
              supportingText = { Text("1–102,400") }
            )
            if (referenceValid) {
              if (proposed != null) {
                Text("3. Apply calibration", style = MaterialTheme.typography.titleMedium)
                Text(String.format(Locale.getDefault(), "%+.1f EV", proposed), style = MaterialTheme.typography.headlineMedium)
                Text("This replaces the $sourceLabel offset on this phone. Check it in another light level after applying.")
              } else {
                Text("The difference is beyond ±3 EV. Check the reference settings, measuring method and light before trying again.", color = MaterialTheme.colorScheme.error)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (held) TextButton(enabled = proposed != null && !saving, onClick = {
        if (!viewModel.isSaving.value && viewModel.measurementSource.value == source &&
          viewModel.readingStatus.value == ReadingStatus.HELD && referenceValid) {
          viewModel.proposeCalibrationOffset(aperture!!, seconds!!, iso!!)?.let { offset ->
            viewModel.setCalibrationOffset(offset, source)
            onDismiss()
          }
        }
      }) { Text("Apply calibration") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(if (held || validReading) "Cancel" else "Close") } }
  )
}

internal fun parseReferenceSeconds(text: String, locale: Locale = Locale.getDefault()): Double? {
  val parts = text.trim().removeSuffix("s").split('/')
  val seconds = when (parts.size) {
    1 -> parseLocalizedDecimal(parts[0], locale)
    2 -> parseLocalizedDecimal(parts[0], locale)?.let { numerator ->
      parseLocalizedDecimal(parts[1], locale)?.takeIf { it > 0.0 }?.let { numerator / it }
    }
    else -> null
  }
  return seconds?.takeIf { it.isFinite() && it > 0.0 && it <= ExposureCalculator.MAX_EXPOSURE_SECONDS }
}
