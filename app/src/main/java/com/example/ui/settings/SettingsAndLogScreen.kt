package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmStock
import com.example.ui.components.BrassScrew
import com.example.ui.components.CartoonFilmRoll
import com.example.ui.components.VintageCameraChassis
import com.example.ui.components.VintageTopPlate
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.theme.VintageBrassBezel
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryDark
import com.example.ui.theme.VintageIvoryMuted
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageLeatherDark
import com.example.ui.theme.VintageLeatherSurface
import com.example.ui.theme.VintageWitnessAmber
import com.example.ui.theme.VintageWitnessRed
import com.example.ui.theme.VintageWoodWalnut
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsAndLogScreen(
  viewModel: LightMeterViewModel,
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val view = LocalView.current

  VintageCameraChassis(modifier = modifier) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val isWideScreen = maxWidth >= 600.dp

      Column(modifier = Modifier.fillMaxSize()) {
        // Top plate header
        VintageTopPlate(
          title = "EXPOSURE ARCHIVE",
          subtitle = if (isWideScreen) "DUAL PANE ROLL LOG & STOCK STUDIO" else "ROLL LOG & STOCK LIBRARY"
        )

        if (isWideScreen) {
          // Foldable Unfolded Dual-Pane View
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
              FilmLogTabContent(viewModel = viewModel)
            }
            Box(
              modifier = Modifier
                .width(1.5.dp)
                .fillMaxSize()
                .background(VintageBrassDark.copy(alpha = 0.5f))
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
              StocksAndSettingsTabContent(viewModel = viewModel)
            }
          }
        } else {
          // Compact Tabbed View
          TabRow(
            selectedTabIndex = selectedTab,
            containerColor = VintageLeatherDark,
            contentColor = VintageBrassBright,
            indicator = { tabPositions ->
              TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = VintageBrassPrimary,
                height = 3.dp
              )
            },
            divider = {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(1.dp)
                  .background(VintageBrassDark.copy(alpha = 0.5f))
              )
            }
          ) {
            Tab(
              selected = selectedTab == 0,
              onClick = {
                viewModel.hapticManager.performDialClick(view)
                selectedTab = 0
              },
              text = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                  Text(
                    "FILM LOG",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            )

            Tab(
              selected = selectedTab == 1,
              onClick = {
                viewModel.hapticManager.performDialClick(view)
                selectedTab = 1
              },
              text = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                  Text(
                    "STOCKS & SETTINGS",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            )
          }

          // Tab Content
          if (selectedTab == 0) {
            FilmLogTabContent(viewModel = viewModel)
          } else {
            StocksAndSettingsTabContent(viewModel = viewModel)
          }
        }
      }
    }
  }
}

@Composable
private fun FilmLogTabContent(viewModel: LightMeterViewModel) {
  val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
  val activeRollName by viewModel.currentRollName.collectAsStateWithLifecycle()
  val activeRollFrameCount by viewModel.activeRollFrameCount.collectAsStateWithLifecycle()
  val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
  var showNewRollDialog by remember { mutableStateOf(false) }
  val view = LocalView.current

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp),
    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Current Active Roll Banner
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(6.dp, RoundedCornerShape(12.dp))
          .clip(RoundedCornerShape(12.dp))
          .background(VintageWoodWalnut)
          .border(1.5.dp, VintageBrassPrimary, RoundedCornerShape(12.dp))
          .padding(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = activeRollName.uppercase(),
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Serif,
              letterSpacing = 1.sp,
              color = VintageBrassBright
            )
            Text(
              text = "Format: ${selectedFormat.displayName} (${selectedFormat.dimensionsMm})",
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageIvoryText
            )
            Text(
              text = "Frames Shot: $activeRollFrameCount / ${selectedFormat.standardFramesPerRoll} exposures",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageIvoryMuted
            )
          }

          Button(
            onClick = {
              viewModel.hapticManager.performDialClick(view)
              showNewRollDialog = true
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = VintageBrassPrimary,
              contentColor = Color.Black
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              "NEW ROLL",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }
        }
      }
    }

    // List header
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "RECORDED EXPOSURES (${allLogs.size})",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 1.sp,
          color = VintageBrassPrimary
        )

        if (allLogs.isNotEmpty()) {
          Text(
            text = "Chronological Log",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryDark
          )
        }
      }
    }

    if (allLogs.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(VintageLeatherSurface, RoundedCornerShape(12.dp))
            .border(1.dp, VintageBrassDark.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "NO EXPOSURES LOGGED YET",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = VintageIvoryMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Tap 'LOG EXPOSURE' on the light meter screen\nto record frame settings.",
              fontSize = 11.sp,
              fontFamily = FontFamily.Default,
              color = VintageIvoryDark,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      }
    } else {
      items(allLogs, key = { it.id }) { entry ->
        FilmLogCard(
          entry = entry,
          onDelete = {
            viewModel.hapticManager.performDialClick(view)
            viewModel.deleteLogEntry(entry.id)
          }
        )
      }
    }
  }

  if (showNewRollDialog) {
    var rollNameInput by remember { mutableStateOf("Roll #${(allLogs.size / 12) + 2}") }
    AlertDialog(
      onDismissRequest = { showNewRollDialog = false },
      containerColor = VintageLeatherDark,
      title = {
        Text(
          "START NEW FILM ROLL",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Serif,
          color = VintageBrassBright
        )
      },
      text = {
        Column {
          Text(
            "This resets your active frame counter to zero and tags subsequent frames under the new roll name.",
            fontSize = 12.sp,
            color = VintageIvoryMuted
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = rollNameInput,
            onValueChange = { rollNameInput = it },
            label = { Text("Roll Name / Identifier") },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = VintageBrassPrimary,
              unfocusedBorderColor = VintageBrassDark,
              focusedTextColor = VintageIvoryText,
              unfocusedTextColor = VintageIvoryText
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.startNewRoll(rollNameInput.ifBlank { "New Roll" })
            showNewRollDialog = false
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = VintageBrassPrimary,
            contentColor = Color.Black
          )
        ) {
          Text("CREATE ROLL", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewRollDialog = false }) {
          Text("CANCEL", color = VintageIvoryMuted)
        }
      }
    )
  }
}

@Composable
private fun FilmLogCard(
  entry: FilmLogEntry,
  onDelete: () -> Unit
) {
  val dateFormatted = remember(entry.timestamp) {
    SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(entry.timestamp))
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(3.dp, RoundedCornerShape(10.dp))
      .clip(RoundedCornerShape(10.dp))
      .background(VintageLeatherSurface)
      .border(1.dp, VintageBrassDark.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
      .padding(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Frame Number Badge
        Box(
          modifier = Modifier
            .size(44.dp)
            .background(Color(0xFF261D15), RoundedCornerShape(8.dp))
            .border(1.dp, VintageBrassPrimary, RoundedCornerShape(8.dp)),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "EXP",
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              color = VintageWitnessRed,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = String.format(Locale.US, "%02d", entry.frameNumber),
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = VintageBrassBright,
              fontFamily = FontFamily.Monospace
            )
          }
        }

        // Details Column
        Column {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${entry.apertureText}  •  ${entry.shutterText}",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = VintageIvoryText,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = entry.format,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = VintageBrassPrimary,
              fontFamily = FontFamily.Monospace
            )
          }

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = "${entry.filmStockName} • ISO ${entry.iso}",
            fontSize = 11.sp,
            color = VintageIvoryMuted,
            fontFamily = FontFamily.Monospace
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "EV: ${String.format(Locale.US, "%.1f", entry.evValue)}",
              fontSize = 10.sp,
              color = VintageWitnessAmber,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "•  $dateFormatted",
              fontSize = 10.sp,
              color = VintageIvoryDark,
              fontFamily = FontFamily.Monospace
            )
          }
        }
      }

      IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete log entry",
          tint = VintageWitnessRed.copy(alpha = 0.8f),
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
private fun StocksAndSettingsTabContent(viewModel: LightMeterViewModel) {
  val allStocks by viewModel.allStocks.collectAsStateWithLifecycle()
  val calibrationOffset by viewModel.calibrationOffsetEv.collectAsStateWithLifecycle()
  var isHapticsOn by remember { mutableStateOf(viewModel.hapticManager.isHapticsEnabled) }
  var showAddStockDialog by remember { mutableStateOf(false) }
  val view = LocalView.current

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp),
    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Film Stock Library Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "SAVED FILM STOCK LIBRARY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            color = VintageBrassPrimary
          )
          Text(
            text = "Visual film canisters with Schwarzschild reciprocity constants",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryDark
          )
        }

        Button(
          onClick = {
            viewModel.hapticManager.performDialClick(view)
            showAddStockDialog = true
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = VintageBrassPrimary,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            "ADD STOCK",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // Stocks items
    items(allStocks, key = { it.id }) { stock ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(3.dp, RoundedCornerShape(10.dp))
          .clip(RoundedCornerShape(10.dp))
          .background(VintageLeatherSurface)
          .border(1.dp, VintageBrassDark.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
          .padding(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CartoonFilmRoll(stock = stock, size = 64.dp)

            Column {
              Text(
                text = stock.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = VintageBrassBright
              )
              Text(
                text = "${stock.brand} • ${stock.type} • ISO ${stock.iso}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = VintageIvoryText
              )
              Text(
                text = "Reciprocity exponent: p=${stock.reciprocityFactor} (tc = t^p)",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = VintageWitnessAmber
              )
              if (stock.description.isNotEmpty()) {
                Text(
                  text = stock.description,
                  fontSize = 10.sp,
                  color = VintageIvoryMuted,
                  maxLines = 2
                )
              }
            }
          }

          if (stock.isCustom) {
            IconButton(
              onClick = {
                viewModel.hapticManager.performDialClick(view)
                viewModel.deleteFilmStock(stock.id)
              },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete custom stock",
                tint = VintageWitnessRed,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }

    // 2. Hardware Settings & Calibration
    item {
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "METER CALIBRATION & HARDWARE",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        color = VintageBrassPrimary
      )
    }

    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(VintageLeatherSurface, RoundedCornerShape(10.dp))
          .border(1.dp, VintageBrassDark.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
          .padding(14.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          // Calibration offset slider
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "LIGHT SENSOR EV OFFSET",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = VintageIvoryText
              )
              Text(
                text = "${if (calibrationOffset >= 0) "+" else ""}${String.format(Locale.US, "%.1f", calibrationOffset)} EV",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = VintageBrassBright
              )
            }
            Text(
              text = "Calibrate device incident light sensor to reference handheld meter.",
              fontSize = 10.sp,
              color = VintageIvoryMuted
            )

            Slider(
              value = calibrationOffset,
              onValueChange = { newVal ->
                viewModel.setCalibrationOffset(newVal)
              },
              valueRange = -3.0f..3.0f,
              steps = 11,
              colors = SliderDefaults.colors(
                thumbColor = VintageBrassBright,
                activeTrackColor = VintageBrassPrimary,
                inactiveTrackColor = Color(0xFF382A1D)
              )
            )
          }

          // Haptic Feedback Switch
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "TACTILE DIAL HAPTICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = VintageIvoryText
              )
              Text(
                text = "Mechanical clock tick vibration when turning dials",
                fontSize = 10.sp,
                color = VintageIvoryMuted
              )
            }

            Switch(
              checked = isHapticsOn,
              onCheckedChange = { checked ->
                isHapticsOn = checked
                viewModel.setHapticsEnabled(checked)
                if (checked) viewModel.hapticManager.performDialClick(view)
              },
              colors = SwitchDefaults.colors(
                checkedThumbColor = VintageBrassBright,
                checkedTrackColor = VintageBrassDark,
                uncheckedThumbColor = VintageIvoryDark,
                uncheckedTrackColor = VintageLeatherDark
              )
            )
          }
        }
      }
    }

    // 3. Medium Format Reference Guide Card
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(VintageWoodWalnut, RoundedCornerShape(10.dp))
          .border(1.dp, VintageBrassPrimary, RoundedCornerShape(10.dp))
          .padding(14.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "ANALOG FORMAT SPECIFICATIONS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = VintageBrassBright
          )
          Text(
            text = "• 35mm (135): 24×36mm gate, standard 36 exposures\n• 6×4.5 (120): 56×41.5mm gate, 16 exposures per roll\n• 6×6 (120): 56×56mm square gate, 12 exposures per roll\n• 6×7 (120): 56×67mm ideal format, 10 giant exposures",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryText,
            lineHeight = 16.sp
          )
        }
      }
    }
  }

  // Dialog to Add Custom Film Stock
  if (showAddStockDialog) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var isoStr by remember { mutableStateOf("400") }
    var recipFactorStr by remember { mutableStateOf("1.25") }
    var type by remember { mutableStateOf("Color Negative") }

    AlertDialog(
      onDismissRequest = { showAddStockDialog = false },
      containerColor = VintageLeatherDark,
      title = {
        Text(
          "ADD CUSTOM FILM STOCK",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Serif,
          color = VintageBrassBright
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Stock Name (e.g. Kodak Pro Image)") },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = VintageBrassPrimary,
              unfocusedBorderColor = VintageBrassDark,
              focusedTextColor = VintageIvoryText,
              unfocusedTextColor = VintageIvoryText
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = brand,
            onValueChange = { brand = it },
            label = { Text("Brand (e.g. Kodak, Ilford, Foma)") },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = VintageBrassPrimary,
              unfocusedBorderColor = VintageBrassDark,
              focusedTextColor = VintageIvoryText,
              unfocusedTextColor = VintageIvoryText
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = isoStr,
              onValueChange = { isoStr = it },
              label = { Text("Box ISO") },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VintageBrassPrimary,
                unfocusedBorderColor = VintageBrassDark,
                focusedTextColor = VintageIvoryText,
                unfocusedTextColor = VintageIvoryText
              ),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
              value = recipFactorStr,
              onValueChange = { recipFactorStr = it },
              label = { Text("Reciprocity (p)") },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VintageBrassPrimary,
                unfocusedBorderColor = VintageBrassDark,
                focusedTextColor = VintageIvoryText,
                unfocusedTextColor = VintageIvoryText
              ),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }

          Text(
            text = "Typical reciprocity exponents: Kodak 1.22-1.30, Ilford 1.30-1.33, Fuji 1.15-1.25.",
            fontSize = 10.sp,
            color = VintageIvoryMuted
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val iso = isoStr.toIntOrNull() ?: 400
            val factor = recipFactorStr.toDoubleOrNull() ?: 1.25
            val newStock = FilmStock(
              name = name.ifBlank { "Custom $iso" },
              brand = brand.ifBlank { "Custom" },
              iso = iso,
              type = type,
              reciprocityFactor = factor,
              description = "Custom stock profile added by user.",
              badgeStyle = "CUSTOM",
              isCustom = true
            )
            viewModel.addCustomFilmStock(newStock)
            showAddStockDialog = false
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = VintageBrassPrimary,
            contentColor = Color.Black
          )
        ) {
          Text("SAVE STOCK", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddStockDialog = false }) {
          Text("CANCEL", color = VintageIvoryMuted)
        }
      }
    )
  }
}
