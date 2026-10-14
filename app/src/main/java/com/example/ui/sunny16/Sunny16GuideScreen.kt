package com.example.ui.sunny16

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CameraFormat
import com.example.data.model.processingLabel
import com.example.sensor.ExposureCalculator
import com.example.ui.components.FilmSettingsConfirmation
import com.example.ui.meter.LightMeterViewModel
import kotlin.math.abs
import kotlin.math.log2

enum class Sunny16Condition(
  val apertureNumber: Double,
  val apertureLabel: String,
  val title: String,
  val description: String,
  val shadowQuality: String,
  val icon: ImageVector,
  val evAtIso100: Int
) {
  SNOW_SAND(22.0, "f/22", "Snow or bright sand", "Bright reflective ground in direct sun.", "Very sharp shadows", Icons.Default.LightMode, 16),
  SUNNY(16.0, "f/16", "Full sun", "Direct, unobstructed sunlight.", "Sharp shadows", Icons.Default.WbSunny, 15),
  HAZY_SUN(11.0, "f/11", "Hazy sun", "Sunlight through thin cloud.", "Soft-edged shadows", Icons.Default.CloudQueue, 14),
  OVERCAST(8.0, "f/8", "Overcast", "Light grey clouds cover the sky.", "Faint shadows", Icons.Default.WbCloudy, 13),
  HEAVY_OVERCAST(5.6, "f/5.6", "Heavy cloud or open shade", "Dark cloud or shade beneath an open sky.", "No distinct shadows", Icons.Default.Cloud, 12),
  DEEP_SHADE(4.0, "f/4", "Deep shade", "Dense trees, a porch or a sheltered alley.", "Very little daylight", Icons.Default.Nightlight, 11)
}

@Composable
fun Sunny16GuideScreen(
  viewModel: LightMeterViewModel,
  onApplyPreset: ((Sunny16Condition, CameraFormat, Int) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val format by viewModel.selectedFormat.collectAsStateWithLifecycle()
  val stock by viewModel.selectedFilmStock.collectAsStateWithLifecycle()
  val pushStops by viewModel.pushStops.collectAsStateWithLifecycle()
  val initialIso = rememberSaveable { viewModel.activeIso }
  var iso by rememberSaveable { mutableIntStateOf(initialIso) }
  var conditionName by rememberSaveable { mutableStateOf(Sunny16Condition.SUNNY.name) }
  var showIsoMenu by rememberSaveable { mutableStateOf(false) }
  var showExplanation by rememberSaveable { mutableStateOf(false) }
  var showConfirmation by rememberSaveable { mutableStateOf(false) }
  val condition = Sunny16Condition.valueOf(conditionName)
  val shutter = sunny16ShutterForIso(iso)
  val view = LocalView.current
  val applyExposure: (Boolean) -> Unit = { confirmed ->
    if (viewModel.applyFilmSettings(stock, iso, pushStops, confirmed)) {
      viewModel.hapticManager.performDialClick(view)
      viewModel.applySunny16Preset(condition.apertureLabel, shutter, iso)
      onApplyPreset?.invoke(condition, format, iso)
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
          Text(
            "Estimate an exposure from the light around you.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        item {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Shooting ISO", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box {
              OutlinedButton(onClick = { showIsoMenu = true }) { Text("ISO " + iso) }
              DropdownMenu(expanded = showIsoMenu, onDismissRequest = { showIsoMenu = false }) {
                (ExposureCalculator.STANDARD_ISOS + listOfNotNull(initialIso, stock?.iso, iso)).distinct().sorted().forEach { value ->
                  DropdownMenuItem(
                    text = { Text("ISO " + value) },
                    onClick = { iso = value; showIsoMenu = false }
                  )
                }
              }
            }
          }
          Text("Meter at EI $iso · ${processingLabel(pushStops)}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("Changing ISO keeps the roll's development setting.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Text("Choose the light", style = MaterialTheme.typography.titleMedium) }
        items(Sunny16Condition.entries) { option ->
          val selected = option == condition
          Surface(
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().selectable(
              selected = selected,
              role = Role.RadioButton,
              onClick = {
                conditionName = option.name
                viewModel.hapticManager.performDialClick(view)
              }
            )
          ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(option.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(option.title, style = MaterialTheme.typography.titleSmall)
                Text(option.shadowQuality, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              RadioButton(selected = selected, onClick = null)
            }
          }
        }
        item {
          TextButton(onClick = { showExplanation = !showExplanation }) {
            Text(if (showExplanation) "Hide explanation" else "How Sunny 16 works")
          }
          if (showExplanation) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(
                "In full sun, use f/16 and a shutter speed close to 1 ÷ ISO. Choose another lighting condition to adjust the aperture. The shutter here is rounded to a speed available on the meter.",
                style = MaterialTheme.typography.bodyMedium
              )
              Text(
                "This is an estimate, not a live measurement. Check important exposures with a trusted meter. Color negative film is usually more forgiving than slide film.",
                style = MaterialTheme.typography.bodyMedium
              )
              Text(
                "35mm and 120 use the same exposure settings. Medium format needs longer lenses for the same framing, so depth of field is shallower at the same f-stop. A smaller aperture increases depth of field, but diffraction still applies.",
                style = MaterialTheme.typography.bodyMedium
              )
              Text(
                "For handheld shots, a shutter speed around 1 ÷ focal length is only a starting point. Camera movement and mirror vibration can require a faster speed or a tripod.",
                style = MaterialTheme.typography.bodyMedium
              )
            }
          }
        }
      }

      Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Estimated exposure", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          Text(condition.apertureLabel + "  ·  " + shutter, style = MaterialTheme.typography.headlineMedium)
          Button(
            onClick = {
              if (viewModel.needsFilmSettingsConfirmation(stock, iso, pushStops)) showConfirmation = true
              else applyExposure(false)
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("Use this exposure") }
        }
      }
    }
  }
  if (showConfirmation) {
    FilmSettingsConfirmation(iso, pushStops,
      onConfirm = { showConfirmation = false; applyExposure(true) },
      onDismiss = { showConfirmation = false }
    )
  }
}

internal fun sunny16ShutterForIso(iso: Int): String {
  require(iso > 0)
  val index = ExposureCalculator.SHUTTER_SECONDS.indices.minBy {
    abs(log2(ExposureCalculator.SHUTTER_SECONDS[it] * iso))
  }
  return ExposureCalculator.SHUTTER_LABELS[index]
}
