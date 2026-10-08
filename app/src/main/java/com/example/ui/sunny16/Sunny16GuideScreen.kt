package com.example.ui.sunny16

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CameraFormat
import com.example.ui.components.BrassScrew
import com.example.ui.components.VintageCameraChassis
import com.example.ui.components.VintageTopPlate
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryDark
import com.example.ui.theme.VintageIvoryMuted
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageLeatherDark
import com.example.ui.theme.VintageLeatherElevated
import com.example.ui.theme.VintageLeatherSurface
import com.example.ui.theme.VintageMeterFace
import com.example.ui.theme.VintageWitnessAmber
import com.example.ui.theme.VintageWitnessRedBright
import com.example.ui.theme.VintageWoodDark
import com.example.ui.theme.VintageWoodWalnut

/**
 * Sunny 16 Weather Conditions Table
 */
enum class Sunny16Condition(
  val apertureNumber: Double,
  val apertureLabel: String,
  val title: String,
  val description: String,
  val shadowQuality: String,
  val icon: ImageVector,
  val evAtIso100: Int
) {
  SNOW_SAND(
    apertureNumber = 22.0,
    apertureLabel = "f/22",
    title = "Snow / Bright Sand",
    description = "Extremely bright reflections from snow, ice, or white coastal sand with direct, blazing sun.",
    shadowQuality = "Piercing, black, ultra-crisp shadows",
    icon = Icons.Default.LightMode,
    evAtIso100 = 16
  ),
  SUNNY(
    apertureNumber = 16.0,
    apertureLabel = "f/16",
    title = "Full Direct Sun",
    description = "Classic baseline Sunny 16 rule condition. Bright, unobstructed sunny day with distinct shadows.",
    shadowQuality = "Hard, sharp, clearly delineated shadows",
    icon = Icons.Default.WbSunny,
    evAtIso100 = 15
  ),
  HAZY_SUN(
    apertureNumber = 11.0,
    apertureLabel = "f/11",
    title = "Hazy Sun / Slight Cloud",
    description = "Soft sunlight through thin high haze or thin clouds. Sun is still clearly visible.",
    shadowQuality = "Soft-edged, diffused distinct shadows",
    icon = Icons.Default.CloudQueue,
    evAtIso100 = 14
  ),
  OVERCAST(
    apertureNumber = 8.0,
    apertureLabel = "f/8",
    title = "Cloudy / Moderate Overcast",
    description = "Standard overcast day. Entire sky is veiled in light grey clouds, no direct sun.",
    shadowQuality = "Faint shadow outlines barely visible",
    icon = Icons.Default.WbCloudy,
    evAtIso100 = 13
  ),
  HEAVY_OVERCAST(
    apertureNumber = 5.6,
    apertureLabel = "f/5.6",
    title = "Heavy Overcast / Full Open Shade",
    description = "Dark stormy clouds, dense overcast, or shooting under clear sky in open shade of buildings/trees.",
    shadowQuality = "No visible shadows cast on ground",
    icon = Icons.Default.Cloud,
    evAtIso100 = 12
  ),
  DEEP_SHADE(
    apertureNumber = 4.0,
    apertureLabel = "f/4",
    title = "Deep Shade / Forest Canopy",
    description = "Dense woods, alleys, building porches, under deep structural overhangs at daytime.",
    shadowQuality = "Zero shadows, ambient twilight illumination",
    icon = Icons.Default.Nightlight,
    evAtIso100 = 11
  )
}

/**
 * Format comparison item explaining 35mm vs 120 film physics
 */
data class FormatComparisonPoint(
  val title: String,
  val ruleStatus: String,
  val detail: String,
  val formatAdvice: String
)

@Composable
fun Sunny16GuideScreen(
  viewModel: LightMeterViewModel,
  onApplyPreset: ((Sunny16Condition, CameraFormat, Int) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val view = LocalView.current
  val selectedFormat by viewModel.selectedFormat.collectAsState()
  var chosenIso by rememberSaveable { mutableIntStateOf(viewModel.activeIso) }
  var activeCondition by rememberSaveable { mutableStateOf(Sunny16Condition.SUNNY) }
  var showCalculator by rememberSaveable { mutableStateOf(true) }
  var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Guide & Calculator, 1: 35mm vs 120 Deep Dive

  VintageCameraChassis(modifier = modifier) {
    Column(modifier = Modifier.fillMaxSize()) {
      VintageTopPlate(
        title = "SUNNY 16 EXPOSURE RULE",
        subtitle = "MANUAL APERTURE & SHUTTER LAW • 35MM & 120 FILM"
      )

      // Tab switcher
      VintageTabsHeader(
        selectedTab = selectedTab,
        onTabSelected = { tab ->
          viewModel.hapticManager.performDialClick(view)
          selectedTab = tab
        }
      )

      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (selectedTab == 0) {
          if (isWide) {
            // Foldable dual pane: Left Calculator & Interactive tool, Right condition table
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
              horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              Column(
                modifier = Modifier
                  .weight(1f)
                  .verticalScroll(rememberScrollState())
              ) {
                InteractiveSunnyCalculatorCard(
                  activeCondition = activeCondition,
                  iso = chosenIso,
                  cameraFormat = selectedFormat,
                  onConditionSelect = { condition ->
                    viewModel.hapticManager.performDialClick(view)
                    activeCondition = condition
                  },
                  onIsoChanged = { iso ->
                    viewModel.hapticManager.performDialClick(view)
                    chosenIso = iso
                  },
                  onApplyToMeter = {
                    viewModel.hapticManager.performDialClick(view)
                    viewModel.setApertureDirectly(activeCondition.apertureLabel)
                    val recShutter = calculateShutterForIso(chosenIso)
                    viewModel.setShutterDirectly(recShutter)
                    viewModel.setIso(chosenIso)
                  }
                )

                Spacer(modifier = Modifier.height(14.dp))
                FundamentalRuleCard()
              }

              Column(
                modifier = Modifier
                  .weight(1f)
                  .verticalScroll(rememberScrollState())
              ) {
                ConditionsTableCard(
                  activeCondition = activeCondition,
                  onSelect = { condition ->
                    viewModel.hapticManager.performDialClick(view)
                    activeCondition = condition
                  }
                )
              }
            }
          } else {
            // Compact single pane
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              item {
                FundamentalRuleCard()
              }

              item {
                InteractiveSunnyCalculatorCard(
                  activeCondition = activeCondition,
                  iso = chosenIso,
                  cameraFormat = selectedFormat,
                  onConditionSelect = { condition ->
                    viewModel.hapticManager.performDialClick(view)
                    activeCondition = condition
                  },
                  onIsoChanged = { iso ->
                    viewModel.hapticManager.performDialClick(view)
                    chosenIso = iso
                  },
                  onApplyToMeter = {
                    viewModel.hapticManager.performDialClick(view)
                    viewModel.setApertureDirectly(activeCondition.apertureLabel)
                    val recShutter = calculateShutterForIso(chosenIso)
                    viewModel.setShutterDirectly(recShutter)
                    viewModel.setIso(chosenIso)
                  }
                )
              }

              item {
                ConditionsTableCard(
                  activeCondition = activeCondition,
                  onSelect = { condition ->
                    viewModel.hapticManager.performDialClick(view)
                    activeCondition = condition
                  }
                )
              }
            }
          }
        } else {
          // Tab 1: 35mm vs 120 Deep Dive
          FormatComparisonScreen(isWide = isWide)
        }
      }
    }
  }
}

@Composable
private fun VintageTabsHeader(
  selectedTab: Int,
  onTabSelected: (Int) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF1E1712))
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    SunnyTabButton(
      text = "SUNNY 16 RULE & CALCULATOR",
      icon = Icons.Default.WbSunny,
      isSelected = selectedTab == 0,
      onClick = { onTabSelected(0) },
      modifier = Modifier.weight(1f)
    )

    SunnyTabButton(
      text = "35MM vs 120 FILM TRUTH",
      icon = Icons.Default.PhotoCamera,
      isSelected = selectedTab == 1,
      onClick = { onTabSelected(1) },
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun SunnyTabButton(
  text: String,
  icon: ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .height(44.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(
        if (isSelected) Color(0xFF2C221A) else Color(0xFF16120F)
      )
      .border(
        width = 1.dp,
        color = if (isSelected) VintageBrassPrimary else VintageBrassDark.copy(alpha = 0.5f),
        shape = RoundedCornerShape(6.dp)
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = text,
        tint = if (isSelected) VintageBrassBright else VintageIvoryDark,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        color = if (isSelected) VintageBrassBright else VintageIvoryDark
      )
    }
  }
}

@Composable
private fun FundamentalRuleCard() {
  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = VintageLeatherElevated),
    border = BorderStroke(1.dp, VintageBrassDark),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        BrassScrew(sizeDp = 10, slotAngleDeg = 45f)
        Text(
          text = "THE FUNDAMENTAL PRINCIPLE",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 1.sp,
          color = VintageBrassPrimary
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "On a bright sunny day, set your aperture to f/16. Then set your shutter speed to the reciprocal of your film's ISO speed.",
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        color = VintageIvoryText
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Formula box
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0xFF120E0B))
          .border(1.dp, VintageBrassDark.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
          .padding(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "ISO",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageIvoryDark
            )
            Text(
              text = "ISO 400",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = VintageBrassBright
            )
          }

          Text(
            text = "+",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = VintageIvoryDark
          )

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "APERTURE",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageIvoryDark
            )
            Text(
              text = "f/16",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = VintageBrassBright
            )
          }

          Text(
            text = "➔",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = VintageBrassPrimary
          )

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "SHUTTER SPEED",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageIvoryDark
            )
            Text(
              text = "1/400s or 1/500s",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = VintageWitnessAmber
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InteractiveSunnyCalculatorCard(
  activeCondition: Sunny16Condition,
  iso: Int,
  cameraFormat: CameraFormat,
  onConditionSelect: (Sunny16Condition) -> Unit,
  onIsoChanged: (Int) -> Unit,
  onApplyToMeter: () -> Unit
) {
  val shutterReciprocal = calculateShutterForIso(iso)

  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = VintageLeatherElevated),
    border = BorderStroke(1.2.dp, VintageBrassPrimary),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          BrassScrew(sizeDp = 10, slotAngleDeg = 15f)
          Text(
            text = "LIVE SUNNY 16 CALCULATOR",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            color = VintageBrassPrimary
          )
        }

        Text(
          text = "ACTIVE FORMAT: ${cameraFormat.displayName}",
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          color = VintageBrassBright
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // ISO Selector Chips
      Text(
        text = "FILM SPEED (ISO):",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = VintageIvoryMuted
      )
      Spacer(modifier = Modifier.height(6.dp))

      val commonIsos = listOf(50, 100, 160, 200, 400, 800, 1600, 3200)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        commonIsos.take(4).forEach { isoVal ->
          IsoChip(
            value = isoVal,
            isSelected = iso == isoVal,
            onClick = { onIsoChanged(isoVal) },
            modifier = Modifier.weight(1f)
          )
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        commonIsos.drop(4).forEach { isoVal ->
          IsoChip(
            value = isoVal,
            isSelected = iso == isoVal,
            onClick = { onIsoChanged(isoVal) },
            modifier = Modifier.weight(1f)
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Weather condition picker
      Text(
        text = "LIGHTING CONDITION:",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = VintageIvoryMuted
      )
      Spacer(modifier = Modifier.height(6.dp))

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Sunny16Condition.values().forEach { cond ->
          ConditionSelectorRow(
            condition = cond,
            isSelected = activeCondition == cond,
            onClick = { onConditionSelect(cond) }
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Calculated readout plate
      CalculatedExposurePlate(
        condition = activeCondition,
        iso = iso,
        shutter = shutterReciprocal,
        onApplyToMeter = onApplyToMeter
      )
    }
  }
}

@Composable
private fun IsoChip(
  value: Int,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .height(36.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(if (isSelected) VintageBrassPrimary else Color(0xFF1E1712))
      .border(
        width = 1.dp,
        color = if (isSelected) VintageBrassBright else VintageBrassDark,
        shape = RoundedCornerShape(6.dp)
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = value.toString(),
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace,
      color = if (isSelected) Color(0xFF1B1309) else VintageIvoryText
    )
  }
}

@Composable
private fun ConditionSelectorRow(
  condition: Sunny16Condition,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(44.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(if (isSelected) Color(0xFF332418) else Color(0xFF1A1410))
      .border(
        width = if (isSelected) 1.5.dp else 0.5.dp,
        color = if (isSelected) VintageBrassPrimary else VintageBrassDark.copy(alpha = 0.4f),
        shape = RoundedCornerShape(6.dp)
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = condition.icon,
        contentDescription = condition.title,
        tint = if (isSelected) VintageWitnessAmber else VintageIvoryDark,
        modifier = Modifier.size(18.dp)
      )
      Text(
        text = condition.title,
        fontSize = 12.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        color = if (isSelected) VintageIvoryText else VintageIvoryDark
      )
    }

    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(if (isSelected) VintageBrassPrimary else Color(0xFF261D15))
        .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
      Text(
        text = condition.apertureLabel,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = if (isSelected) Color(0xFF1E1712) else VintageBrassBright
      )
    }
  }
}

@Composable
private fun CalculatedExposurePlate(
  condition: Sunny16Condition,
  iso: Int,
  shutter: String,
  onApplyToMeter: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(Color(0xFF2B2016), Color(0xFF1E1610))
        )
      )
      .border(1.2.dp, VintageBrassPrimary, RoundedCornerShape(8.dp))
      .padding(14.dp)
  ) {
    Column {
      Text(
        text = "RECOMMENDED CAMERA DIAL SETTINGS",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        color = VintageBrassPrimary
      )

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "LENS APERTURE",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryDark
          )
          Text(
            text = condition.apertureLabel,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageBrassBright
          )
        }

        Text(
          text = "•",
          fontSize = 20.sp,
          color = VintageBrassDark
        )

        Column {
          Text(
            text = "SHUTTER SPEED",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryDark
          )
          Text(
            text = shutter,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageWitnessAmber
          )
        }

        Text(
          text = "•",
          fontSize = 20.sp,
          color = VintageBrassDark
        )

        Column {
          Text(
            text = "FILM ISO",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryDark
          )
          Text(
            text = "$iso",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryText
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Apply button with full visible text
      Button(
        onClick = onApplyToMeter,
        colors = ButtonDefaults.buttonColors(
          containerColor = VintageBrassPrimary,
          contentColor = Color(0xFF1B1309)
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "Apply Preset",
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "APPLY TO LIGHT METER DIALS",
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 1.sp,
          fontSize = 12.sp
        )
      }
    }
  }
}

@Composable
private fun ConditionsTableCard(
  activeCondition: Sunny16Condition,
  onSelect: (Sunny16Condition) -> Unit
) {
  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = VintageLeatherElevated),
    border = BorderStroke(1.dp, VintageBrassDark),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        BrassScrew(sizeDp = 10, slotAngleDeg = 90f)
        Text(
          text = "SUNNY 16 CONDITIONS REFERENCE TABLE",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 1.sp,
          color = VintageBrassPrimary
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Sunny16Condition.values().forEach { cond ->
          ConditionCardDetail(
            condition = cond,
            isSelected = activeCondition == cond,
            onClick = { onSelect(cond) }
          )
        }
      }
    }
  }
}

@Composable
private fun ConditionCardDetail(
  condition: Sunny16Condition,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(if (isSelected) Color(0xFF2E2015) else Color(0xFF1A140F))
      .border(
        width = if (isSelected) 1.2.dp else 0.5.dp,
        color = if (isSelected) VintageBrassPrimary else VintageBrassDark.copy(alpha = 0.35f),
        shape = RoundedCornerShape(6.dp)
      )
      .clickable(onClick = onClick)
      .padding(12.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = condition.icon,
            contentDescription = condition.title,
            tint = if (isSelected) VintageBrassBright else VintageWitnessAmber,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = condition.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) VintageBrassBright else VintageIvoryText
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = "EV ${condition.evAtIso100}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryDark
          )
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (isSelected) VintageBrassPrimary else Color(0xFF281F17))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = condition.apertureLabel,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = if (isSelected) Color(0xFF1E1712) else VintageBrassBright
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = condition.description,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = VintageIvoryDark
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Shadows: ${condition.shadowQuality}",
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = VintageWitnessAmber
      )
    }
  }
}

/**
 * 35mm vs 120 Film Deep Dive comparison screen
 */
@Composable
private fun FormatComparisonScreen(isWide: Boolean) {
  val comparisonPoints = remember {
    listOf(
      FormatComparisonPoint(
        title = "EXPOSURE VALUES (EV) & LUMEN PHYSICS",
        ruleStatus = "IDENTICAL FOR BOTH (NO DIFFERENCE)",
        detail = "Aperture (f-stop) is the ratio of focal length to physical pupil diameter (f = F/D). Because f-stop measures light intensity per unit of area, f/16 produces the EXACT same luminous exposure (lux-seconds) onto 35mm film as it does onto giant 120 medium format film. If Sunny 16 dictates f/16 at 1/500s for ISO 400, that applies equally to Leica 35mm, Hasselblad 6x6, and Pentax 67.",
        formatAdvice = "You do NOT adjust your shutter speed or f-stop when switching from 35mm to 120!"
      ),
      FormatComparisonPoint(
        title = "DEPTH OF FIELD & BLUR CIRCLE (THE CRUCIAL DIFFERENCE)",
        ruleStatus = "RADICALLY DIFFERENT (1.5 to 2.5 STOPS OF DOF)",
        detail = "To achieve the same field of view as a 50mm lens on 35mm, medium format cameras use longer lenses (80mm on 6x6, 105mm on 6x7). Longer focal lengths yield dramatically shallower Depth of Field at the same f-stop. Stopping down to f/16 on 6x7 gives the approximate depth of field of f/8 on 35mm.",
        formatAdvice = "If you want deep landscapes on 120 film, you will often shoot at f/22 or f/32 to match 35mm's f/16 depth!"
      ),
      FormatComparisonPoint(
        title = "DIFFRACTION & RESOLUTION LIMITS",
        ruleStatus = "DIFFERENT PRACTICAL SWEET SPOTS",
        detail = "On 35mm film, stopping down past f/16 causes optical diffraction blur because the tiny negative requires extreme 8x to 12x enlargement. On 120 medium format, the negative is 3x to 5x larger, needing far less enlargement. Therefore, f/22 and f/32 remain remarkably sharp and usable on 120 film without diffraction degradation.",
        formatAdvice = "Do not fear f/22 or f/32 on 120 cameras (e.g. Yashica Mat, Mamiya, Rolleiflex, Pentax 67)."
      ),
      FormatComparisonPoint(
        title = "SHUTTER VIBRATION & HANDHELD LIMITS",
        ruleStatus = "DIFFERENT HANDHELD RULES",
        detail = "35mm cameras (especially rangefinders) are light and easily handheld at 1/60s or 1/30s. Medium format SLR bodies (Pentax 67, Hasselblad 500C, Mamiya RB67) have massive mirror slap and heavier bodies, while 6x6 TLRs have leaf shutters with zero mirror slap.",
        formatAdvice = "Rule of thumb: Handheld min shutter = 1 / focal length. For a 90mm or 105mm medium format lens, keep shutter at 1/125s or faster, or use a sturdy tripod."
      ),
      FormatComparisonPoint(
        title = "FILM ROLL EXPENSES & LATITUDE",
        ruleStatus = "PRACTICAL FILM SHOOTING TIP",
        detail = "35mm rolls provide 36 exposures, allowing bracketing. 120 rolls give only 10 to 16 shots (10 on 6x7, 12 on 6x6, 16 on 645). Furthermore, color negative film (Portra, Gold) handles 2-3 stops of overexposure safely, while slide film (Velvia, Provia) requires strict 1/3 stop precision.",
        formatAdvice = "When in doubt under the Sunny 16 rule with color negative 120 film, favor 1/2 to 1 stop OVEREXPOSURE (e.g. open to f/11 instead of f/16) to ensure rich shadow details."
      )
    )
  }

  if (isWide) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
      ) {
        ComparisonSummaryBanner()
        Spacer(modifier = Modifier.height(14.dp))
        comparisonPoints.take(2).forEach { pt ->
          ComparisonPointCard(point = pt)
          Spacer(modifier = Modifier.height(12.dp))
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
      ) {
        comparisonPoints.drop(2).forEach { pt ->
          ComparisonPointCard(point = pt)
          Spacer(modifier = Modifier.height(12.dp))
        }
      }
    }
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        ComparisonSummaryBanner()
      }

      items(comparisonPoints) { pt ->
        ComparisonPointCard(point = pt)
      }
    }
  }
}

@Composable
private fun ComparisonSummaryBanner() {
  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = VintageLeatherElevated),
    border = BorderStroke(1.2.dp, VintageBrassPrimary),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        BrassScrew(sizeDp = 10, slotAngleDeg = 30f)
        Text(
          text = "THE VERDICT: IS SUNNY 16 DIFFERENT FOR 35MM VS 120?",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 1.sp,
          color = VintageBrassBright
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0xFF140F0B))
          .border(1.dp, VintageBrassDark, RoundedCornerShape(6.dp))
          .padding(12.dp)
      ) {
        Column {
          Text(
            text = "PHOTOMETRIC EXPOSURE: 100% THE SAME",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF81C784)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "f/16 delivers the exact same volume of photons per square millimeter regardless of format size. You do NOT change exposure math.",
            fontSize = 12.sp,
            color = VintageIvoryText
          )

          HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = VintageBrassDark.copy(alpha = 0.5f)
          )

          Text(
            text = "OPTICAL DEPTH OF FIELD: TOTALLY DIFFERENT",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageWitnessAmber
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "f/16 on 6x7 medium format creates shallow depth equivalent to f/8 on 35mm. For wide landscape focus, 120 cameras routinely shoot at f/22 or f/32.",
            fontSize = 12.sp,
            color = VintageIvoryText
          )
        }
      }
    }
  }
}

@Composable
private fun ComparisonPointCard(point: FormatComparisonPoint) {
  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = VintageLeatherElevated),
    border = BorderStroke(1.dp, VintageBrassDark),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = point.title,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp,
          color = VintageBrassPrimary,
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .background(Color(0xFF231911))
          .border(0.5.dp, VintageBrassDark, RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(
          text = point.ruleStatus,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          color = if (point.ruleStatus.contains("IDENTICAL")) Color(0xFF81C784) else VintageWitnessAmber
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = point.detail,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = VintageIvoryText
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Pro Tip callout
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0xFF140E0A))
          .padding(10.dp)
      ) {
        Row(
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Pro Tip",
            tint = VintageBrassBright,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = point.formatAdvice,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = VintageBrassBright,
            lineHeight = 16.sp
          )
        }
      }
    }
  }
}

/**
 * Standard shutter reciprocal helper for ISO
 */
private fun calculateShutterForIso(iso: Int): String {
  return when {
    iso <= 50 -> "1/60s"
    iso <= 100 -> "1/125s"
    iso <= 160 -> "1/160s"
    iso <= 200 -> "1/250s"
    iso <= 400 -> "1/500s"
    iso <= 800 -> "1/1000s"
    iso <= 1600 -> "1/2000s"
    else -> "1/4000s"
  }
}
