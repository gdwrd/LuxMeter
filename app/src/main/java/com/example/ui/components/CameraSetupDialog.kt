package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CameraFormat
import com.example.data.model.CameraSetup
import com.example.sensor.ExposureCalculator
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun CameraSetupDialog(
  initial: CameraSetup? = null,
  saving: Boolean,
  onDismiss: () -> Unit,
  onSave: (CameraSetup) -> Unit
) {
  var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
  var formatName by rememberSaveable(initial?.id) { mutableStateOf(initial?.format ?: CameraFormat.FORMAT_35MM.name) }
  var focalText by rememberSaveable(initial?.id) { mutableStateOf((initial?.focalLengthMm ?: 50).toString()) }
  var minimumText by rememberSaveable(initial?.id) { mutableStateOf(initial?.minAperture?.let(::formatSetupNumber).orEmpty()) }
  var maximumText by rememberSaveable(initial?.id) { mutableStateOf(initial?.maxAperture?.let(::formatSetupNumber).orEmpty()) }
  var shutterText by rememberSaveable(initial?.id) {
    mutableStateOf(initial?.shutterSpeeds?.let(ExposureCalculator::parseShutterSpeeds)
      ?.joinToString(" ", transform = ::formatSetupShutter).orEmpty())
  }
  var allowsBulb by rememberSaveable(initial?.id) { mutableStateOf(initial?.allowsBulb ?: false) }
  var showFormats by rememberSaveable { mutableStateOf(false) }
  val format = CameraFormat.entries.firstOrNull { it.name == formatName }
  val focalLength = focalText.trim().toIntOrNull()
  val minimum = parseLocalizedDecimal(minimumText)
  val maximum = parseLocalizedDecimal(maximumText)
  val shutters = parseSetupShutters(shutterText)
  val nameValid = name.trim().length in 1..100
  val focalValid = focalLength != null && focalLength in 10..1000
  val minimumValid = minimum != null && minimum in 1.0..64.0
  val maximumValid = maximum != null && maximum in 1.0..64.0 && (!minimumValid || maximum >= minimum!!)
  val valid = nameValid && format != null && focalValid && minimumValid && maximumValid && shutters != null

  AlertDialog(
    onDismissRequest = { if (!saving) onDismiss() },
    title = { Text(if (initial == null) "Add camera setup" else "Edit camera setup") },
    text = {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("Enter the settings marked on your camera and lens.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
          name, { name = it }, label = { Text("Setup name") },
          placeholder = { Text("Canon P · 50mm") }, modifier = Modifier.fillMaxWidth(),
          enabled = !saving, singleLine = true,
          isError = name.isNotBlank() && !nameValid,
          supportingText = if (name.trim().length > 100) ({ Text("Use 100 characters or fewer.") }) else null
        )
        Box {
          OutlinedButton(onClick = { showFormats = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text("Format · ${format?.displayName ?: "Choose format"}")
          }
          DropdownMenu(expanded = showFormats, onDismissRequest = { showFormats = false }) {
            CameraFormat.entries.forEach { choice ->
              DropdownMenuItem(text = { Text(choice.displayName) }, onClick = {
                formatName = choice.name
                showFormats = false
              })
            }
          }
        }
        OutlinedTextField(
          focalText, { focalText = it }, label = { Text("Lens focal length (mm)") },
          modifier = Modifier.fillMaxWidth(), enabled = !saving, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          isError = focalText.isNotBlank() && !focalValid,
          supportingText = { Text("10–1,000 mm") }
        )
        OutlinedTextField(
          minimumText, { minimumText = it }, label = { Text("Widest aperture (f-number)") },
          modifier = Modifier.fillMaxWidth(), enabled = !saving, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          isError = minimumText.isNotBlank() && !minimumValid,
          supportingText = { Text("Smallest f-number on the lens, from 1 to 64.") }
        )
        OutlinedTextField(
          maximumText, { maximumText = it }, label = { Text("Smallest aperture (f-number)") },
          modifier = Modifier.fillMaxWidth(), enabled = !saving, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          isError = maximumText.isNotBlank() && !maximumValid,
          supportingText = { Text("Largest f-number, up to 64; no smaller than the first value.") }
        )
        OutlinedTextField(
          shutterText, { shutterText = it }, label = { Text("Shutter speeds") },
          modifier = Modifier.fillMaxWidth(), enabled = !saving, minLines = 2, maxLines = 5,
          placeholder = { Text("1/1000 1/500 1/250 1/125 1/60 1/30 1/15 1/8 1/4 1/2 1") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
          isError = shutterText.isNotBlank() && shutters == null,
          supportingText = { Text("Separate speeds with spaces. Use fractions or seconds, such as 1/125 or ${formatSetupNumber(0.5)}. Up to 64 speeds; maximum 60 seconds.") }
        )
        Row(
          Modifier.fillMaxWidth().toggleable(
            value = allowsBulb, enabled = !saving, role = Role.Checkbox,
            onValueChange = { allowsBulb = it }
          ).padding(vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(checked = allowsBulb, onCheckedChange = null, enabled = !saving)
          Text("Bulb available for longer exposures", modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
      }
    },
    confirmButton = {
      TextButton(enabled = valid && !saving, onClick = {
        if (valid && !saving) onSave(CameraSetup(
          id = initial?.id ?: 0,
          name = name.trim(),
          format = format!!.name,
          focalLengthMm = focalLength!!,
          minAperture = minimum!!,
          maxAperture = maximum!!,
          shutterSpeeds = shutters!!.joinToString(","),
          allowsBulb = allowsBulb
        ))
      }) { Text(if (saving) "Saving setup…" else "Save setup") }
    },
    dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
  )
}

internal fun parseLocalizedDecimal(text: String, locale: Locale = Locale.getDefault()): Double? {
  val input = text.trim()
  val format = NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false }
  val position = ParsePosition(0)
  val value = format.parse(input, position)?.toDouble() ?: return null
  return value.takeIf { position.index == input.length && it.isFinite() }
}

internal fun parseSetupShutters(text: String, locale: Locale = Locale.getDefault()): List<Double>? {
  val separator = if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') "[;\\s]+" else "[,;\\s]+"
  val normalized = text.trim().split(Regex(separator)).map { token ->
    token.lowercase(Locale.ROOT).removeSuffix("s").split('/').map { part ->
      parseLocalizedDecimal(part, locale)?.toString() ?: return null
    }.joinToString("/")
  }.joinToString(" ")
  return ExposureCalculator.parseShutterSpeeds(normalized)
}

private fun formatSetupNumber(value: Double): String = NumberFormat.getNumberInstance().apply {
  isGroupingUsed = false
  maximumFractionDigits = 340
}.format(value)

private fun formatSetupShutter(seconds: Double): String {
  val denominator = (1.0 / seconds).roundToLong()
  return if (seconds < 1.0 && denominator > 0 && seconds == 1.0 / denominator) "1/$denominator"
  else formatSetupNumber(seconds)
}
