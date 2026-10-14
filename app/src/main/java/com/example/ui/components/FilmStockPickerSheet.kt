package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
  var query by rememberSaveable { mutableStateOf("") }
  var filmType by rememberSaveable { mutableStateOf("All") }
  var showAll by rememberSaveable { mutableStateOf(false) }
  val draftStock = stocks.firstOrNull { it.id == draftStockId }
    ?: selectedStock?.takeIf { it.id == draftStockId }
  val validSettings = draftIso in 1..102400 && (draftStock == null || draftStock.iso > 0) &&
    (draftPushStops?.let { it in 0..4 && (it == 0 || draftStock?.isoForPush(it) != null) } ?: true)
  val view = LocalView.current
  val filteredStocks = stocks.filter { film ->
    val matchesType = when (filmType) {
      "Color" -> film.type == "Color Negative" || film.type == "Tungsten Cine"
      "B&W" -> film.type == "Black & White"
      "Slide" -> film.type == "Color Slide"
      else -> true
    }
    matchesType && "${film.brand} ${film.name} ${film.iso}".contains(query.trim(), ignoreCase = true)
  }
  val visibleStocks = if (showAll) filteredStocks else filteredStocks.take(9)

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
      LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp * LocalDensity.current.fontScale),
        modifier = Modifier.weight(1f).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
      ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${stocks.size} film presets", style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextField(
              value = query,
              onValueChange = { query = it; showAll = false },
              modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search films" },
              placeholder = { Text("Search films") },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
              trailingIcon = if (query.isNotEmpty()) ({
                IconButton(onClick = { query = ""; showAll = false }) {
                  Icon(Icons.Default.Close, contentDescription = "Clear film search")
                }
              }) else null,
              singleLine = true,
              shape = MaterialTheme.shapes.small,
              colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
              )
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("All", "Color", "B&W", "Slide").forEach { type ->
                FilterChip(
                  selected = filmType == type,
                  onClick = { filmType = type; showAll = false },
                  label = { Text(type) }
                )
              }
            }
          }
        }
        items(visibleStocks, key = { it.id }) { film ->
          val selected = draftStockId == film.id
          Column(
            modifier = Modifier.fillMaxWidth()
              .selectable(selected = selected, role = Role.RadioButton, onClick = {
                hapticManager.performDialClick(view)
                if (draftStockId != film.id) {
                  draftStockId = film.id
                  draftIso = film.iso
                  draftPushStops = 0
                }
              })
              .semantics {
                contentDescription = "${film.brand} ${film.name} · ISO ${film.iso} · ${film.type} · ${film.correctionLabel}"
              },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Box(
              Modifier.fillMaxWidth().height(104.dp).background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.medium
              ),
              contentAlignment = Alignment.Center
            ) {
              CartoonFilmRoll(stock = film, size = 76.dp, isSelected = selected)
              IconButton(onClick = { detailsFilmId = film.id },
                modifier = Modifier.align(Alignment.TopEnd).size(40.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Details for ${film.name}",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
              }
              if (selected) {
                Icon(Icons.Default.Check, contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp).size(12.dp))
              }
            }
            Text(film.name, style = MaterialTheme.typography.labelLarge,
              textAlign = TextAlign.Center)
            Text("${film.brand} · ISO ${film.iso}", style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center)
          }
        }
        if (filteredStocks.size > 9) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
              Text(if (showAll) "Show fewer films" else "View all ${filteredStocks.size} films")
            }
          }
        }
        if (filteredStocks.isEmpty()) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            Text(if (stocks.isEmpty()) "Your film library is loading." else "No matching films",
              style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 20.dp))
          }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider()
            draftStock?.let {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(if (it.name.startsWith(it.brand, ignoreCase = true)) it.name else "${it.brand} ${it.name}",
                    style = MaterialTheme.typography.titleMedium)
                  Text("Box ISO ${it.iso} · ${it.type}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(it.correctionLabel, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { detailsFilmId = it.id }) {
                  Icon(Icons.Default.Info, contentDescription = "Selected film details")
                }
              }
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
              Text("Choose a film to use push choices.", style = MaterialTheme.typography.bodyMedium)
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
          }
        }
      }
      Button(
        enabled = validSettings,
        onClick = {
          if (needsConfirmation(draftStock, draftIso, draftPushStops)) showConfirmation = true
          else if (onApply(draftStock, draftIso, draftPushStops, false)) onDismiss()
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 54.dp)
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
