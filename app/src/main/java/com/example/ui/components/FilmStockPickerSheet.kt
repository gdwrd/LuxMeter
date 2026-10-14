package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.data.model.FilmStock
import com.example.data.model.processingLabel
import com.example.sensor.ExposureCalculator
import com.example.sensor.HapticManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilmStockPickerSheet(
  stocks: List<FilmStock>,
  selectedStock: FilmStock?,
  customIso: Int?,
  pushStops: Int?,
  hapticManager: HapticManager,
  onApply: (FilmStock?, Int, Int?, Boolean) -> Boolean,
  needsConfirmation: (FilmStock?, Int, Int?) -> Boolean,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showIsoMenu by rememberSaveable { mutableStateOf(false) }
  var detailsFilmId by rememberSaveable { mutableStateOf<Int?>(null) }
  var draftStockId by rememberSaveable { mutableStateOf(selectedStock?.id) }
  var draftIso by rememberSaveable { mutableIntStateOf(customIso ?: selectedStock?.iso ?: 400) }
  var draftPushStops by rememberSaveable { mutableStateOf(pushStops) }
  var showConfirmation by rememberSaveable { mutableStateOf(false) }
  val draftStock = stocks.firstOrNull { it.id == draftStockId }
    ?: selectedStock?.takeIf { it.id == draftStockId }
  val validSettings = draftIso in 1..102400 && (draftStock == null || draftStock.iso > 0) &&
    (draftPushStops?.let { it in 0..4 && (it == 0 || draftStock?.isoForPush(it) != null) } ?: true)
  val view = LocalView.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 24.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Film & speed", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close film picker") }
      }
      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(vertical = 8.dp)
      ) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            draftStock?.let {
              Text("${it.name} · Box ISO ${it.iso}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Push processing", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              (0..4).forEach { stops ->
                val quickIso = if (stops == 0) draftStock?.isoForPush(0) ?: draftIso else draftStock?.isoForPush(stops)
                FilterChip(
                  selected = draftPushStops == stops && draftIso == quickIso,
                  enabled = quickIso != null,
                  onClick = {
                    quickIso?.let {
                      hapticManager.performDialClick(view)
                      draftIso = it
                      draftPushStops = stops
                    }
                  },
                  label = { Text(if (stops == 0) "Normal" else "+$stops") }
                )
              }
            }
            Text("Meter at EI $draftIso", style = MaterialTheme.typography.titleLarge)
            Text(if (draftPushStops == null) processingLabel(null) else "Ask lab: ${processingLabel(draftPushStops)}",
              style = MaterialTheme.typography.bodyMedium)
            Text("Development applies to the whole roll.", style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (draftStock == null) {
              Text("Choose a film below to use push choices.", style = MaterialTheme.typography.bodyMedium)
            } else if (draftStock.isoForPush(4) == null) {
              Text("Some choices exceed the meter's ISO limit.", style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text("Shooting ISO", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
              Box {
                OutlinedButton(onClick = { showIsoMenu = true }) { Text("ISO $draftIso") }
                DropdownMenu(expanded = showIsoMenu, onDismissRequest = { showIsoMenu = false }) {
                  (ExposureCalculator.STANDARD_ISOS + listOfNotNull(draftIso, draftStock?.iso)).distinct().sorted().forEach { value ->
                    DropdownMenuItem(
                      text = { Text("ISO $value") },
                      onClick = {
                        hapticManager.performDialClick(view)
                        draftIso = value
                        showIsoMenu = false
                      }
                    )
                  }
                }
              }
            }
            Text("Custom ISO changes metering; development stays as shown.", style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (draftStock != null && draftIso != draftStock.iso) {
              TextButton(onClick = { draftIso = draftStock.iso }) { Text("Use box speed · ISO ${draftStock.iso}") }
            }
            HorizontalDivider()
            Text("Choose film", style = MaterialTheme.typography.titleMedium)
          }
        }
        items(stocks, key = { it.id }) { film ->
          val selected = draftStockId == film.id
          Row(
            modifier = Modifier.fillMaxWidth()
              .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                  hapticManager.performDialClick(view)
                  if (draftStockId != film.id) {
                    draftStockId = film.id
                    draftIso = film.iso
                    draftPushStops = 0
                  }
                }
              )
              .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            CartoonFilmRoll(stock = film, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(film.name, style = MaterialTheme.typography.titleMedium)
              Text(
                film.brand + " · ISO " + film.iso + " · " + film.type,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(film.correctionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { detailsFilmId = film.id }) {
              Icon(Icons.Default.Info, contentDescription = "Details for ${film.name}")
            }
          }
        }
        if (stocks.isEmpty()) {
          item { Text("Your film library is loading.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 20.dp)) }
        }
      }
      Button(
        enabled = validSettings,
        onClick = {
          if (needsConfirmation(draftStock, draftIso, draftPushStops)) showConfirmation = true
          else if (onApply(draftStock, draftIso, draftPushStops, false)) onDismiss()
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
      ) { Text("Use for this roll") }
    }
  }
  if (showConfirmation) {
    FilmSettingsConfirmation(draftIso, draftPushStops,
      onConfirm = {
        if (onApply(draftStock, draftIso, draftPushStops, true)) {
          showConfirmation = false
          onDismiss()
        }
      },
      onDismiss = { showConfirmation = false }
    )
  }
  stocks.firstOrNull { it.id == detailsFilmId }?.let { film ->
    FilmDetailsDialog(stock = film, onDismiss = { detailsFilmId = null })
  }
}

@Composable
internal fun FilmSettingsConfirmation(iso: Int, pushStops: Int?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Change this roll?") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Meter at EI $iso · ${processingLabel(pushStops)}")
        Text("This changes the roll's metering and development settings. Development applies to the whole roll. Saved frames keep their original settings.")
      }
    },
    confirmButton = {
      // Material 3 1.3.0 clips wrapped actions when its reversed rows have unequal heights.
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onConfirm) { Text("Apply to whole roll") }
        TextButton(onClick = onDismiss) { Text("Keep current settings") }
      }
    }
  )
}
