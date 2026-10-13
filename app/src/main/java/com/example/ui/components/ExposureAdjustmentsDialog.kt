package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExposureAdjustmentsDialog(
  exposureCompensation: Double,
  filterStops: Double,
  onCompensationChanged: (Double) -> Unit,
  onFilterStopsChanged: (Double) -> Unit,
  onReset: () -> Unit,
  onDismiss: () -> Unit
) {
  var filterText by rememberSaveable { mutableStateOf(formatExposureInput(filterStops)) }
  LaunchedEffect(filterStops) { filterText = formatExposureInput(filterStops) }
  val filterValue = parseExposureInput(filterText)
  val validFilter = filterValue != null && filterValue in 0.0..16.0
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Exposure adjustments") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Exposure compensation", style = MaterialTheme.typography.titleMedium)
        Text("Positive values give more exposure. Adjust in ⅓-stop steps.", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          IconButton(enabled = exposureCompensation > -3.0, onClick = {
            onCompensationChanged(((exposureCompensation * 3).roundToInt() - 1).coerceAtLeast(-9) / 3.0)
          }) { Icon(Icons.Default.Remove, contentDescription = "Decrease exposure compensation") }
          Text(formatCompensation(exposureCompensation), style = MaterialTheme.typography.titleLarge)
          IconButton(enabled = exposureCompensation < 3.0, onClick = {
            onCompensationChanged(((exposureCompensation * 3).roundToInt() + 1).coerceAtMost(9) / 3.0)
          }) { Icon(Icons.Default.Add, contentDescription = "Increase exposure compensation") }
        }
        OutlinedTextField(
          value = filterText,
          onValueChange = { text ->
            filterText = text
            parseExposureInput(text)?.takeIf { it in 0.0..16.0 }?.let(onFilterStopsChanged)
          },
          label = { Text("Filter strength (stops)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          isError = !validFilter,
          supportingText = { Text(if (validFilter) "Enter light lost to the filter: for example, 3 for an ND8 filter." else "Enter a filter strength from 0 to 16 stops.") }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(0, 3, 6, 10).forEach { stops ->
            TextButton(onClick = {
              filterText = stops.toString()
              onFilterStopsChanged(stops.toDouble())
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (stops == 0) "No filter" else "$stops stops") }
          }
        }
        Text("Changes apply to the meter immediately. Film correction is applied after these adjustments.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = {
          onReset()
          filterText = formatExposureInput(0.0)
        }) { Text("Reset adjustments") }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss, enabled = validFilter) { Text("Done") } }
  )
}

internal fun formatCompensation(value: Double): String = String.format(Locale.getDefault(), "%+.2f EV", value)
