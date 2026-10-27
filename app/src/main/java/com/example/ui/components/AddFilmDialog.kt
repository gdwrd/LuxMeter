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
import com.example.data.model.FilmStock
import com.example.sensor.ExposureCalculator
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import kotlin.math.pow

@Composable
fun AddFilmDialog(saving: Boolean, onDismiss: () -> Unit, onAdd: (FilmStock) -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  var brand by rememberSaveable { mutableStateOf("") }
  var isoText by rememberSaveable { mutableStateOf("400") }
  var type by rememberSaveable { mutableStateOf("Color Negative") }
  var showTypes by rememberSaveable { mutableStateOf(false) }
  var hasProfile by rememberSaveable { mutableStateOf(false) }
  var exponentText by rememberSaveable { mutableStateOf(NumberFormat.getNumberInstance().format(1.25)) }
  val iso = isoText.toIntOrNull()
  val exponent = parseReciprocityExponent(exponentText)
  val film = createUnlistedFilm(name, brand, iso, type, hasProfile, exponent)

  AlertDialog(
    onDismissRequest = { if (!saving) onDismiss() },
    title = { Text("Add unlisted film") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Film name") }, singleLine = true, enabled = !saving)
        OutlinedTextField(brand, { brand = it }, label = { Text("Brand (optional)") }, singleLine = true, enabled = !saving)
        OutlinedTextField(
          isoText, { isoText = it }, label = { Text("Box ISO") }, singleLine = true, enabled = !saving,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          isError = iso == null || iso !in 1..12800,
          supportingText = if (iso != null && iso in 1..12800) null else { { Text("Enter an ISO from 1 to 12,800.") } }
        )
        Box {
          OutlinedButton(onClick = { showTypes = true }, enabled = !saving) { Text(type) }
          DropdownMenu(expanded = showTypes, onDismissRequest = { showTypes = false }) {
            listOf("Color Negative", "Black & White", "Color Slide", "Tungsten Cine").forEach { value ->
              DropdownMenuItem(text = { Text(value) }, onClick = { type = value; showTypes = false })
            }
          }
        }
        Row(
          Modifier.fillMaxWidth().toggleable(
            value = hasProfile, enabled = !saving, role = Role.Checkbox, onValueChange = { hasProfile = it }
          ),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(checked = hasProfile, onCheckedChange = null, enabled = !saving)
          Text("I have a tested long-exposure profile", Modifier.weight(1f).padding(start = 8.dp))
        }
        if (hasProfile) {
          OutlinedTextField(
            exponentText, { exponentText = it }, label = { Text("Reciprocity exponent") },
            singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = !isValidReciprocityExponent(exponent),
            supportingText = { Text("Use your tested tᵖ exponent. Corrected exposures must stay within one day.") }
          )
        } else Text("Film correction unknown. Metered settings are available; record the exposure you choose.", style = MaterialTheme.typography.bodyMedium)
      }
    },
    confirmButton = {
      TextButton(enabled = !saving && film != null, onClick = {
        if (!saving && film != null) onAdd(film)
      }) { Text(if (saving) "Saving film…" else "Save film") }
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

internal fun createUnlistedFilm(
  name: String, brand: String, iso: Int?, type: String, hasProfile: Boolean, exponent: Double?
): FilmStock? {
  if (name.isBlank() || iso == null || iso !in 1..12800 ||
    type !in listOf("Color Negative", "Black & White", "Color Slide", "Tungsten Cine") ||
    (hasProfile && !isValidReciprocityExponent(exponent))) return null
  return FilmStock(
    name = name.trim(), brand = brand.trim().ifBlank { "Custom" }, iso = iso, type = type,
    reciprocityFactor = if (hasProfile) exponent!! else 1.0,
    description = if (hasProfile) "Custom tested film profile" else "Unlisted film",
    badgeStyle = "CUSTOM", isCustom = true, hasReciprocityProfile = hasProfile
  )
}
