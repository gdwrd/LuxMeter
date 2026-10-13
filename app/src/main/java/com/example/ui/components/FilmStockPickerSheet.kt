package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.data.model.FilmStock
import com.example.sensor.ExposureCalculator
import com.example.sensor.HapticManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmStockPickerSheet(
  stocks: List<FilmStock>,
  selectedStock: FilmStock?,
  customIso: Int?,
  hapticManager: HapticManager,
  onStockSelected: (FilmStock) -> Unit,
  onCustomIsoSelected: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showIsoMenu by rememberSaveable { mutableStateOf(false) }
  var detailsFilmId by rememberSaveable { mutableStateOf<Int?>(null) }
  val iso = customIso ?: selectedStock?.iso ?: 400
  val view = LocalView.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 24.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Film", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close film picker") }
      }
      Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Shooting ISO", style = MaterialTheme.typography.titleMedium)
          Text(
            if (selectedStock != null) "Changing ISO keeps your film's long-exposure correction."
            else "Choose a film below for long-exposure correction.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Box {
          OutlinedButton(onClick = { showIsoMenu = true }) { Text("ISO " + iso) }
          DropdownMenu(expanded = showIsoMenu, onDismissRequest = { showIsoMenu = false }) {
            (ExposureCalculator.STANDARD_ISOS + iso).distinct().sorted().forEach { value ->
              DropdownMenuItem(
                text = { Text("ISO " + value) },
                onClick = {
                  hapticManager.performDialClick(view)
                  onCustomIsoSelected(value)
                  showIsoMenu = false
                }
              )
            }
          }
        }
      }
      if (customIso != null && selectedStock != null && customIso != selectedStock.iso) {
        TextButton(onClick = { onCustomIsoSelected(selectedStock.iso) }) {
          Text("Use box speed · ISO " + selectedStock.iso)
        }
      }
      HorizontalDivider()
      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(vertical = 8.dp)
      ) {
        items(stocks, key = { it.id }) { film ->
          val selected = selectedStock?.id == film.id
          Row(
            modifier = Modifier.fillMaxWidth()
              .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                  hapticManager.performDialClick(view)
                  onStockSelected(film)
                  onDismiss()
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
      TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Done") }
    }
  }
  stocks.firstOrNull { it.id == detailsFilmId }?.let { film ->
    FilmDetailsDialog(stock = film, onDismiss = { detailsFilmId = null })
  }
}
