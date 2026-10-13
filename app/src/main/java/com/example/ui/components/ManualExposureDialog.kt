package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.example.sensor.ExposureCalculator
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

@Composable
fun ManualExposureDialog(
  initialAperture: Double,
  initialSeconds: Double,
  saving: Boolean,
  onSave: (Double, Double, String) -> Unit,
  onDismiss: () -> Unit
) {
  var apertureText by rememberSaveable { mutableStateOf(formatExposureInput(initialAperture)) }
  var secondsText by rememberSaveable { mutableStateOf(if (initialSeconds > 0) formatExposureInput(initialSeconds) else "") }
  var notes by rememberSaveable { mutableStateOf("") }
  val aperture = parseExposureInput(apertureText)
  val seconds = parseExposureInput(secondsText)
  val apertureValid = aperture != null && aperture in 1.0..64.0
  val secondsValid = seconds != null && seconds > 0.0 && seconds <= ExposureCalculator.MAX_EXPOSURE_SECONDS
  AlertDialog(
    onDismissRequest = { if (!saving) onDismiss() },
    title = { Text("Record manual exposure") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enter the settings you used. This records your choice without applying a film correction.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
          value = apertureText, onValueChange = { apertureText = it },
          label = { Text("Aperture (f-number)") }, singleLine = true, enabled = !saving,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          isError = !apertureValid,
          supportingText = { if (!apertureValid) Text("Enter an f-number from 1 to 64.") }
        )
        OutlinedTextField(
          value = secondsText, onValueChange = { secondsText = it },
          label = { Text("Actual shutter time (seconds)") }, singleLine = true, enabled = !saving,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          isError = secondsText.isNotEmpty() && !secondsValid,
          supportingText = { Text(if (secondsText.isNotEmpty() && !secondsValid) "Enter more than 0 and at most 86,400 seconds." else "For 1/125 second, enter 0.008.") }
        )
        OutlinedTextField(
          value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") },
          enabled = !saving, minLines = 2, maxLines = 4, isError = notes.length > 5000,
          supportingText = { if (notes.length > 5000) Text("Keep notes within 5,000 characters.") }
        )
      }
    },
    confirmButton = {
      TextButton(enabled = !saving && apertureValid && secondsValid && notes.length <= 5000, onClick = {
        if (apertureValid && secondsValid) onSave(aperture!!, seconds!!, notes.trim())
      }) { Text(if (saving) "Saving…" else "Save manual record") }
    },
    dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
  )
}

internal fun parseExposureInput(text: String, locale: Locale = Locale.getDefault()): Double? {
  val input = text.trim()
  val position = ParsePosition(0)
  val number = NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false }.parse(input, position)?.toDouble()
  return number?.takeIf { position.index == input.length && it.isFinite() }
}

internal fun formatExposureInput(value: Double): String = NumberFormat.getNumberInstance().apply {
  isGroupingUsed = false
  maximumFractionDigits = 6
}.format(value)
