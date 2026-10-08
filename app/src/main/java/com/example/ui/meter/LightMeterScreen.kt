package com.example.ui.meter

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CameraFormat
import com.example.sensor.DepthOfFieldCalculator
import com.example.sensor.ExposureCalculator
import com.example.ui.components.AnalogMeterGauge
import com.example.ui.components.BrassScrew
import com.example.ui.components.CameraViewfinder
import com.example.ui.components.CartoonFilmRoll
import com.example.ui.components.ExposureTimerDialog
import com.example.ui.components.FilmStockPickerSheet
import com.example.ui.components.MechanicalFrameCounter
import com.example.ui.components.VintageCameraChassis
import com.example.ui.components.VintageTopPlate
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryDark
import com.example.ui.theme.VintageIvoryMuted
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageLeatherSurface
import com.example.ui.theme.VintageWitnessAmber
import com.example.ui.theme.VintageWitnessRed
import com.example.ui.theme.VintageWoodWalnut

@Composable
fun LightMeterScreen(
  viewModel: LightMeterViewModel,
  modifier: Modifier = Modifier
) {
  val view = LocalView.current
  val lux by viewModel.sensorManager.lux.collectAsStateWithLifecycle()
  val isLocked by viewModel.sensorManager.isLocked.collectAsStateWithLifecycle()
  val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
  val selectedStock by viewModel.selectedFilmStock.collectAsStateWithLifecycle()
  val customIso by viewModel.customIso.collectAsStateWithLifecycle()
  val activeIso = viewModel.activeIso
  val mode by viewModel.exposureMode.collectAsStateWithLifecycle()
  val selectedApertureIdx by viewModel.selectedApertureIndex.collectAsStateWithLifecycle()
  val selectedShutterIdx by viewModel.selectedShutterIndex.collectAsStateWithLifecycle()
  val activeRollFrameCount by viewModel.activeRollFrameCount.collectAsStateWithLifecycle()
  val allStocks by viewModel.allStocks.collectAsStateWithLifecycle()
  val showTimerDialog by viewModel.showTimerDialog.collectAsStateWithLifecycle()
  val showStockPickerSheet by viewModel.showStockPickerSheet.collectAsStateWithLifecycle()
  val isViewfinderMode by viewModel.isViewfinderMode.collectAsStateWithLifecycle()
  val focalLengthMm by viewModel.focalLengthMm.collectAsStateWithLifecycle()
  val focusDistanceMeters by viewModel.focusDistanceMeters.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

  val currentApValue = ExposureCalculator.APERTURE_VALUES[selectedApertureIdx]
  val currentApLabel = ExposureCalculator.APERTURE_LABELS[selectedApertureIdx]
  val currentShutterLabel = ExposureCalculator.SHUTTER_LABELS[selectedShutterIdx]
  val rawSec = viewModel.getRawShutterSeconds()
  val corrSec = viewModel.getCorrectedShutterSeconds()
  val isLongExp = viewModel.isLongExposure()

  val targetEv100 = viewModel.computeEv100(lux)
  val targetEvForIso = viewModel.computeEvForActiveIso(lux)

  val dofResult = DepthOfFieldCalculator.calculate(
    focalLengthMm.toDouble(),
    currentApValue,
    focusDistanceMeters,
    selectedFormat
  )

  // Ensure default stock is loaded
  LaunchedEffect(allStocks) {
    if (selectedStock == null && customIso == null && allStocks.isNotEmpty()) {
      viewModel.setFilmStock(allStocks.first())
    }
  }

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(userMessage) {
    userMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearUserMessage()
    }
  }

  VintageCameraChassis(modifier = modifier) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val isFoldableWide = maxWidth >= 600.dp

      if (isFoldableWide) {
        // ==========================================
        // FOLDABLE UNFOLDED / TABLET TWO-PANE LAYOUT
        // ==========================================
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Left Pane: Optical Viewport & Framing
          val leftScroll = rememberScrollState()
          Column(
            modifier = Modifier
              .weight(1.05f)
              .fillMaxHeight()
              .verticalScroll(leftScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Header Top Plate
            TopPlateHeader(
              isViewfinderMode = isViewfinderMode,
              activeRollFrameCount = activeRollFrameCount,
              selectedFormat = selectedFormat,
              onToggleMode = {
                viewModel.hapticManager.performDialClick(view)
                viewModel.setViewfinderMode(!isViewfinderMode)
              }
            )

            // Format Selector Tabs
            FormatSelectorRow(
              selectedFormat = selectedFormat,
              onSelectFormat = { fmt ->
                viewModel.hapticManager.performDialClick(view)
                viewModel.setFormat(fmt)
              }
            )

            // Viewport (Tall 340dp for rich foldable ground-glass view)
            if (isViewfinderMode) {
              CameraViewfinder(
                format = selectedFormat,
                focalLengthMm = focalLengthMm,
                onFocalLengthChanged = { viewModel.setFocalLength(it) },
                hapticManager = viewModel.hapticManager,
                isLocked = isLocked,
                onToggleLock = { viewModel.toggleHold() },
                onMeasuredEvChanged = { ev -> viewModel.onOpticalEvMeasured(ev) },
                currentEv = targetEv100,
                viewportHeightDp = 330
              )
            } else {
              AnalogMeterGauge(
                targetEv = targetEv100,
                lux = lux,
                isLocked = isLocked,
                onToggleHold = {
                  viewModel.hapticManager.performDialClick(view)
                  viewModel.toggleHold()
                }
              )
            }

            // Ambient Sunny 16 Quick Strip
            Sunny16ReferenceStrip(
              onSelectEv = { evVal ->
                viewModel.hapticManager.performDialClick(view)
                viewModel.setPresetEv100(evVal)
              }
            )
          }

          // Right Pane: Precision Exposure Controls & Action
          val rightScroll = rememberScrollState()
          Column(
            modifier = Modifier
              .weight(0.95f)
              .fillMaxHeight()
              .verticalScroll(rightScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Exposure Console with Visible Buttons
            UnifiedExposureConsole(
              mode = mode,
              targetEvForIso = targetEvForIso,
              activeIso = activeIso,
              selectedFormat = selectedFormat,
              currentApValue = currentApValue,
              currentApLabel = currentApLabel,
              selectedApertureIdx = selectedApertureIdx,
              currentShutterLabel = currentShutterLabel,
              selectedShutterIdx = selectedShutterIdx,
              corrSec = corrSec,
              isLongExp = isLongExp,
              selectedStock = selectedStock,
              onSelectMode = { newMode ->
                viewModel.hapticManager.performDialClick(view)
                viewModel.setExposureMode(newMode)
              },
              onApertureStep = { step ->
                val newIdx = (selectedApertureIdx + step).coerceIn(0, ExposureCalculator.APERTURE_VALUES.lastIndex)
                viewModel.hapticManager.performDialClick(view)
                viewModel.setApertureIndex(newIdx)
              },
              onShutterStep = { step ->
                val newIdx = (selectedShutterIdx + step).coerceIn(0, ExposureCalculator.SHUTTER_SECONDS.lastIndex)
                viewModel.hapticManager.performDialClick(view)
                viewModel.setShutterIndex(newIdx)
              },
              onTimerClick = {
                viewModel.hapticManager.performDialClick(view)
                viewModel.setShowTimerDialog(true)
              },
              onPickFilmClick = {
                viewModel.hapticManager.performDialClick(view)
                viewModel.setShowStockPickerSheet(true)
              }
            )

            // Depth of Field Bar
            DepthOfFieldBar(
              focalLengthMm = focalLengthMm,
              currentApLabel = currentApLabel,
              selectedFormat = selectedFormat,
              focusDistanceMeters = focusDistanceMeters,
              dofResult = dofResult,
              onStepDistance = { delta ->
                viewModel.hapticManager.performDialClick(view)
                viewModel.setFocusDistance((focusDistanceMeters + delta).coerceIn(0.6, 30.0))
              },
              onSnapHyperfocal = {
                viewModel.hapticManager.performDialClick(view)
                viewModel.setFocusToHyperfocal()
              }
            )

            // Primary Shutter Release Action Button
            ShutterReleaseButton(
              selectedFormat = selectedFormat,
              activeRollFrameCount = activeRollFrameCount,
              onClick = {
                viewModel.hapticManager.performShutterRelease(view)
                viewModel.logExposure()
              }
            )
          }
        }
      } else {
        // ==========================================
        // COMPACT PHONE / FOLDED SCREEN LAYOUT
        // ==========================================
        val scrollState = rememberScrollState()
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 10.dp)
            .padding(top = 2.dp, bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // 1. Header Top Plate
          TopPlateHeader(
            isViewfinderMode = isViewfinderMode,
            activeRollFrameCount = activeRollFrameCount,
            selectedFormat = selectedFormat,
            onToggleMode = {
              viewModel.hapticManager.performDialClick(view)
              viewModel.setViewfinderMode(!isViewfinderMode)
            }
          )

          // 2. Camera Format Selector
          FormatSelectorRow(
            selectedFormat = selectedFormat,
            onSelectFormat = { fmt ->
              viewModel.hapticManager.performDialClick(view)
              viewModel.setFormat(fmt)
            }
          )

          // 3. Viewport (Viewfinder or Analog Needle Meter)
          if (isViewfinderMode) {
            CameraViewfinder(
              format = selectedFormat,
              focalLengthMm = focalLengthMm,
              onFocalLengthChanged = { viewModel.setFocalLength(it) },
              hapticManager = viewModel.hapticManager,
              isLocked = isLocked,
              onToggleLock = { viewModel.toggleHold() },
              onMeasuredEvChanged = { ev -> viewModel.onOpticalEvMeasured(ev) },
              currentEv = targetEv100,
              viewportHeightDp = 220
            )
          } else {
            AnalogMeterGauge(
              targetEv = targetEv100,
              lux = lux,
              isLocked = isLocked,
              onToggleHold = {
                viewModel.hapticManager.performDialClick(view)
                viewModel.toggleHold()
              }
            )
          }

          // 4. Compact Ambient Reference Strip (Sunny 16)
          Sunny16ReferenceStrip(
            onSelectEv = { evVal ->
              viewModel.hapticManager.performDialClick(view)
              viewModel.setPresetEv100(evVal)
            }
          )

          // 5. UNIFIED EXPOSURE CONSOLE (Aperture, Shutter, Film & ISO)
          UnifiedExposureConsole(
            mode = mode,
            targetEvForIso = targetEvForIso,
            activeIso = activeIso,
            selectedFormat = selectedFormat,
            currentApValue = currentApValue,
            currentApLabel = currentApLabel,
            selectedApertureIdx = selectedApertureIdx,
            currentShutterLabel = currentShutterLabel,
            selectedShutterIdx = selectedShutterIdx,
            corrSec = corrSec,
            isLongExp = isLongExp,
            selectedStock = selectedStock,
            onSelectMode = { newMode ->
              viewModel.hapticManager.performDialClick(view)
              viewModel.setExposureMode(newMode)
            },
            onApertureStep = { step ->
              val newIdx = (selectedApertureIdx + step).coerceIn(0, ExposureCalculator.APERTURE_VALUES.lastIndex)
              viewModel.hapticManager.performDialClick(view)
              viewModel.setApertureIndex(newIdx)
            },
            onShutterStep = { step ->
              val newIdx = (selectedShutterIdx + step).coerceIn(0, ExposureCalculator.SHUTTER_SECONDS.lastIndex)
              viewModel.hapticManager.performDialClick(view)
              viewModel.setShutterIndex(newIdx)
            },
            onTimerClick = {
              viewModel.hapticManager.performDialClick(view)
              viewModel.setShowTimerDialog(true)
            },
            onPickFilmClick = {
              viewModel.hapticManager.performDialClick(view)
              viewModel.setShowStockPickerSheet(true)
            }
          )

          // 6. STREAMLINED DEPTH OF FIELD BAR
          DepthOfFieldBar(
            focalLengthMm = focalLengthMm,
            currentApLabel = currentApLabel,
            selectedFormat = selectedFormat,
            focusDistanceMeters = focusDistanceMeters,
            dofResult = dofResult,
            onStepDistance = { delta ->
              viewModel.hapticManager.performDialClick(view)
              viewModel.setFocusDistance((focusDistanceMeters + delta).coerceIn(0.6, 30.0))
            },
            onSnapHyperfocal = {
              viewModel.hapticManager.performDialClick(view)
              viewModel.setFocusToHyperfocal()
            }
          )

          // 7. PRIMARY ACTION: VINTAGE SHUTTER RELEASE BUTTON ("LOG EXPOSURE")
          ShutterReleaseButton(
            selectedFormat = selectedFormat,
            activeRollFrameCount = activeRollFrameCount,
            onClick = {
              viewModel.hapticManager.performShutterRelease(view)
              viewModel.logExposure()
            }
          )
        }
      }

      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
      )
    }

    // Modal Sheet for Stock Picker
    if (showStockPickerSheet) {
      FilmStockPickerSheet(
        stocks = allStocks,
        selectedStock = selectedStock,
        customIso = customIso,
        hapticManager = viewModel.hapticManager,
        onStockSelected = { stock ->
          viewModel.setFilmStock(stock)
        },
        onCustomIsoSelected = { iso ->
          viewModel.setCustomIso(iso)
        },
        onDismiss = {
          viewModel.setShowStockPickerSheet(false)
        }
      )
    }

    // Exposure Countdown Timer Dialog
    if (showTimerDialog) {
      ExposureTimerDialog(
        rawSeconds = rawSec,
        correctedSeconds = corrSec,
        stockName = selectedStock?.name ?: "ISO $activeIso",
        hapticManager = viewModel.hapticManager,
        onDismiss = {
          viewModel.setShowTimerDialog(false)
        }
      )
    }
  }
}

@Composable
private fun TopPlateHeader(
  isViewfinderMode: Boolean,
  activeRollFrameCount: Int,
  selectedFormat: CameraFormat,
  onToggleMode: () -> Unit
) {
  VintageTopPlate(
    title = "LUXMETER",
    subtitle = "PRECISION FILM EXPOSURE",
    trailingContent = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Gauge Mode Toggle
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isViewfinderMode) VintageBrassPrimary else Color(0xFF261D15))
            .border(0.8.dp, VintageBrassDark, RoundedCornerShape(6.dp))
            .clickable(onClick = onToggleMode)
            .padding(horizontal = 7.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = if (isViewfinderMode) Icons.Default.Videocam else Icons.Default.Speed,
              contentDescription = "Toggle viewfinder",
              tint = if (isViewfinderMode) Color.Black else VintageBrassBright,
              modifier = Modifier.size(13.dp)
            )
            Text(
              text = if (isViewfinderMode) "OPTICAL" else "NEEDLE",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = if (isViewfinderMode) Color.Black else VintageBrassBright
            )
          }
        }

        MechanicalFrameCounter(
          currentFrame = activeRollFrameCount,
          totalFrames = selectedFormat.standardFramesPerRoll
        )
      }
    }
  )
}

@Composable
private fun FormatSelectorRow(
  selectedFormat: CameraFormat,
  onSelectFormat: (CameraFormat) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    CameraFormat.entries.forEach { format ->
      val isSelected = format == selectedFormat
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(6.dp))
          .background(if (isSelected) VintageWoodWalnut else VintageLeatherSurface)
          .border(
            width = if (isSelected) 1.2.dp else 0.8.dp,
            color = if (isSelected) VintageBrassPrimary else VintageBrassDark.copy(alpha = 0.5f),
            shape = RoundedCornerShape(6.dp)
          )
          .clickable { onSelectFormat(format) }
          .padding(vertical = 4.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = format.displayName,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) VintageBrassBright else VintageIvoryMuted,
            maxLines = 1
          )
          Text(
            text = format.aspectRatioLabel,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) VintageWitnessAmber else VintageIvoryDark,
            maxLines = 1
          )
        }
      }
    }
  }
}

@Composable
private fun Sunny16ReferenceStrip(
  onSelectEv: (Double) -> Unit
) {
  LazyRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    val presets = listOf(
      "Full Sun (15)" to 15.0,
      "Hazy Sun (14)" to 14.0,
      "Overcast (12)" to 12.0,
      "Shade (10)" to 10.0,
      "Golden Hr (8)" to 8.0,
      "Interior (6)" to 6.0,
      "Night (3)" to 3.0
    )
    items(presets) { (label, evVal) ->
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(5.dp))
          .background(Color(0xFF201812))
          .border(0.6.dp, VintageBrassDark.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
          .clickable { onSelectEv(evVal) }
          .padding(horizontal = 7.dp, vertical = 3.dp)
      ) {
        Text(
          text = label,
          fontSize = 9.sp,
          fontFamily = FontFamily.Monospace,
          color = VintageIvoryDark
        )
      }
    }
  }
}

@Composable
private fun UnifiedExposureConsole(
  mode: ExposureMode,
  targetEvForIso: Double,
  activeIso: Int,
  selectedFormat: CameraFormat,
  currentApValue: Double,
  currentApLabel: String,
  selectedApertureIdx: Int,
  currentShutterLabel: String,
  selectedShutterIdx: Int,
  corrSec: Double,
  isLongExp: Boolean,
  selectedStock: com.example.data.model.FilmStock?,
  onSelectMode: (ExposureMode) -> Unit,
  onApertureStep: (Int) -> Unit,
  onShutterStep: (Int) -> Unit,
  onTimerClick: () -> Unit,
  onPickFilmClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(6.dp, RoundedCornerShape(12.dp))
      .clip(RoundedCornerShape(12.dp))
      .background(VintageLeatherSurface)
      .border(1.5.dp, VintageBrassDark, RoundedCornerShape(12.dp))
      .padding(8.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      // Mode Selector Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = "MODE:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageBrassPrimary
          )
          ExposureMode.entries.forEach { expMode ->
            val isSelected = expMode == mode
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) VintageBrassPrimary else Color(0xFF261D15))
                .border(0.8.dp, if (isSelected) VintageBrassBright else VintageBrassDark, RoundedCornerShape(4.dp))
                .clickable { onSelectMode(expMode) }
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = expMode.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isSelected) Color.Black else VintageIvoryText
              )
            }
          }
        }

        Text(
          text = "EV ${String.format(java.util.Locale.US, "%.1f", targetEvForIso)} (ISO $activeIso)",
          fontSize = 9.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          color = VintageWitnessAmber
        )
      }

      // 3-Column Exposure Console
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Column 1: APERTURE
        Box(
          modifier = Modifier
            .weight(1f)
            .background(Color(0xFF1E1611), RoundedCornerShape(8.dp))
            .border(1.dp, VintageBrassDark.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Text(
              text = "APERTURE",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageBrassPrimary
            )

            Text(
              text = currentApLabel,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = VintageBrassBright
            )

            Text(
              text = if (selectedFormat != CameraFormat.FORMAT_35MM) {
                "≈ f/${String.format(java.util.Locale.US, "%.1f", DepthOfFieldCalculator.getEquivalent35mmAperture(currentApValue, selectedFormat))} 35Eq"
              } else {
                "35mm Format"
              },
              fontSize = 8.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageWitnessAmber,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )

            // Visible Stepper Buttons with Clear Text
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Button(
                onClick = { onApertureStep(-1) },
                enabled = selectedApertureIdx > 0,
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF281E16),
                  contentColor = VintageBrassBright,
                  disabledContainerColor = Color(0xFF19130E),
                  disabledContentColor = Color(0xFF554433)
                ),
                border = BorderStroke(0.8.dp, VintageBrassDark),
                contentPadding = PaddingValues(horizontal = 2.dp)
              ) {
                Text(
                  text = "◀ OPEN",
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
              }

              Button(
                onClick = { onApertureStep(1) },
                enabled = selectedApertureIdx < ExposureCalculator.APERTURE_VALUES.lastIndex,
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF281E16),
                  contentColor = VintageBrassBright,
                  disabledContainerColor = Color(0xFF19130E),
                  disabledContentColor = Color(0xFF554433)
                ),
                border = BorderStroke(0.8.dp, VintageBrassDark),
                contentPadding = PaddingValues(horizontal = 2.dp)
              ) {
                Text(
                  text = "STOP ▶",
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          }
        }

        // Column 2: SHUTTER SPEED
        Box(
          modifier = Modifier
            .weight(1f)
            .background(Color(0xFF1E1611), RoundedCornerShape(8.dp))
            .border(
              1.dp,
              if (isLongExp) VintageWitnessRed else VintageBrassDark.copy(alpha = 0.7f),
              RoundedCornerShape(8.dp)
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Text(
              text = "SHUTTER",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageBrassPrimary
            )

            Text(
              text = if (isLongExp) ExposureCalculator.formatSeconds(corrSec) else currentShutterLabel,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = if (isLongExp) VintageWitnessRed else VintageBrassBright
            )

            if (isLongExp) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clickable { onTimerClick() }
              ) {
                Icon(Icons.Default.Timer, contentDescription = "Timer", tint = VintageWitnessRed, modifier = Modifier.size(10.dp))
                Text(
                  text = "RECIPROCITY ⏱",
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  color = VintageWitnessRed
                )
              }
            } else {
              Text(
                text = "Raw: $currentShutterLabel",
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                color = VintageIvoryDark,
                maxLines = 1
              )
            }

            // Visible Stepper Buttons with Clear Text
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Button(
                onClick = { onShutterStep(-1) },
                enabled = selectedShutterIdx > 0,
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF281E16),
                  contentColor = VintageBrassBright,
                  disabledContainerColor = Color(0xFF19130E),
                  disabledContentColor = Color(0xFF554433)
                ),
                border = BorderStroke(0.8.dp, VintageBrassDark),
                contentPadding = PaddingValues(horizontal = 2.dp)
              ) {
                Text(
                  text = "◀ FAST",
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
              }

              Button(
                onClick = { onShutterStep(1) },
                enabled = selectedShutterIdx < ExposureCalculator.SHUTTER_SECONDS.lastIndex,
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF281E16),
                  contentColor = VintageBrassBright,
                  disabledContainerColor = Color(0xFF19130E),
                  disabledContentColor = Color(0xFF554433)
                ),
                border = BorderStroke(0.8.dp, VintageBrassDark),
                contentPadding = PaddingValues(horizontal = 2.dp)
              ) {
                Text(
                  text = "SLOW ▶",
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          }
        }

        // Column 3: FILM & ISO (Clickable Stock Selector)
        Box(
          modifier = Modifier
            .weight(1f)
            .background(Color(0xFF1E1611), RoundedCornerShape(8.dp))
            .border(1.dp, VintageBrassDark.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Text(
              text = "FILM / ISO",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageBrassPrimary
            )

            if (selectedStock != null) {
              CartoonFilmRoll(stock = selectedStock, size = 30.dp)
              Text(
                text = selectedStock.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = VintageBrassBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "ISO ${selectedStock.iso}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = VintageWitnessAmber
              )
            } else {
              Box(
                modifier = Modifier
                  .size(30.dp)
                  .background(VintageWoodWalnut, RoundedCornerShape(4.dp))
                  .border(0.8.dp, VintageBrassPrimary, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "$activeIso",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  color = VintageBrassBright
                )
              }
              Text(
                text = "CUSTOM",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = VintageBrassBright
              )
              Text(
                text = "ISO $activeIso",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = VintageWitnessAmber
              )
            }

            // Visible Button to Pick Film
            Button(
              onClick = onPickFilmClick,
              modifier = Modifier.fillMaxWidth().height(34.dp),
              shape = RoundedCornerShape(6.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF281E16),
                contentColor = VintageBrassBright
              ),
              border = BorderStroke(0.8.dp, VintageBrassDark),
              contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
              Text(
                text = "🎞 FILM",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DepthOfFieldBar(
  focalLengthMm: Int,
  currentApLabel: String,
  selectedFormat: CameraFormat,
  focusDistanceMeters: Double,
  dofResult: com.example.sensor.DofResult,
  onStepDistance: (Double) -> Unit,
  onSnapHyperfocal: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(VintageLeatherSurface, RoundedCornerShape(8.dp))
      .border(1.dp, VintageBrassDark.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
      .padding(horizontal = 8.dp, vertical = 6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "LENS DoF: ${focalLengthMm}mm @ $currentApLabel • ${selectedFormat.displayName}",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          color = VintageBrassBright,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "Focus: ${DepthOfFieldCalculator.formatDistance(focusDistanceMeters)} ➔ Sharp: ${DepthOfFieldCalculator.formatDistance(dofResult.nearMeters)} to ${if (dofResult.isFarInfinity) "∞" else DepthOfFieldCalculator.formatDistance(dofResult.farMeters)}",
          fontSize = 9.sp,
          fontFamily = FontFamily.Monospace,
          color = VintageIvoryMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        // Distance step down
        Button(
          onClick = { onStepDistance(-0.5) },
          modifier = Modifier.height(30.dp),
          shape = RoundedCornerShape(6.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF261D15),
            contentColor = VintageBrassPrimary
          ),
          border = BorderStroke(0.7.dp, VintageBrassDark),
          contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)
        ) {
          Text("-0.5m", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }

        // Distance step up
        Button(
          onClick = { onStepDistance(0.5) },
          modifier = Modifier.height(30.dp),
          shape = RoundedCornerShape(6.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF261D15),
            contentColor = VintageBrassPrimary
          ),
          border = BorderStroke(0.7.dp, VintageBrassDark),
          contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)
        ) {
          Text("+0.5m", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }

        // Hyperfocal Snap Button with clear text
        Button(
          onClick = onSnapHyperfocal,
          modifier = Modifier.height(30.dp),
          shape = RoundedCornerShape(6.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = VintageWoodWalnut,
            contentColor = VintageBrassBright
          ),
          border = BorderStroke(0.8.dp, VintageBrassPrimary),
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = "∞ HYPER",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }
  }
}

@Composable
private fun ShutterReleaseButton(
  selectedFormat: CameraFormat,
  activeRollFrameCount: Int,
  onClick: () -> Unit
) {
  Button(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .height(52.dp)
      .shadow(6.dp, RoundedCornerShape(12.dp)),
    colors = ButtonDefaults.buttonColors(
      containerColor = VintageBrassPrimary,
      contentColor = Color(0xFF140F0C)
    ),
    shape = RoundedCornerShape(12.dp),
    border = ButtonDefaults.outlinedButtonBorder.copy(
      brush = Brush.verticalGradient(
        listOf(VintageBrassBright, VintageBrassDark)
      ),
      width = 1.5.dp
    )
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      BrassScrew(sizeDp = 12)
      Icon(
        imageVector = Icons.Default.Camera,
        contentDescription = "Log Exposure",
        modifier = Modifier.size(20.dp)
      )
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "LOG EXPOSURE",
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Serif,
          letterSpacing = 1.2.sp
        )
        Text(
          text = "${selectedFormat.displayName} • FRAME ${activeRollFrameCount + 1}/${selectedFormat.standardFramesPerRoll}",
          fontSize = 9.sp,
          fontWeight = FontWeight.Medium,
          fontFamily = FontFamily.Monospace
        )
      }
      BrassScrew(sizeDp = 12)
    }
  }
}
