package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BrassScrew
import com.example.ui.meter.LightMeterScreen
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.settings.SettingsAndLogScreen
import com.example.ui.sunny16.Sunny16GuideScreen
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryDark
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageLeatherDark
import com.example.ui.theme.VintageWoodDark
import com.example.ui.theme.VintageWoodWalnut

@Composable
fun MainScreen(
  viewModel: LightMeterViewModel = viewModel()
) {
  var currentScreenIndex by rememberSaveable { mutableIntStateOf(0) }
  val view = LocalView.current

  // Handle back press to return to Light Meter screen if on Settings
  BackHandler(enabled = currentScreenIndex != 0) {
    viewModel.hapticManager.performDialClick(view)
    currentScreenIndex = 0
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding(),
    containerColor = VintageLeatherDark,
    bottomBar = {
      VintageBottomNavBar(
        selectedIndex = currentScreenIndex,
        onSelect = { idx ->
          viewModel.hapticManager.performDialClick(view)
          currentScreenIndex = idx
        }
      )
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      AnimatedContent(
        targetState = currentScreenIndex,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
      ) { screenIdx ->
        when (screenIdx) {
          0 -> LightMeterScreen(viewModel = viewModel)
          1 -> Sunny16GuideScreen(
            viewModel = viewModel,
            onApplyPreset = { _, _, _ ->
              currentScreenIndex = 0
            }
          )
          2 -> SettingsAndLogScreen(viewModel = viewModel)
        }
      }
    }
  }
}

@Composable
private fun VintageBottomNavBar(
  selectedIndex: Int,
  onSelect: (Int) -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .shadow(12.dp)
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            VintageWoodDark,
            VintageWoodWalnut,
            Color(0xFF24160E)
          )
        )
      )
      .border(
        width = 1.5.dp,
        brush = Brush.horizontalGradient(
          colors = listOf(
            VintageBrassDark,
            VintageBrassPrimary,
            VintageBrassDark
          )
        ),
        shape = androidx.compose.ui.graphics.RectangleShape
      )
      .padding(horizontal = 8.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 640.dp)
        .height(56.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      BrassScrew(sizeDp = 10, slotAngleDeg = 15f)

      VintageNavItem(
        title = "LIGHT METER",
        icon = Icons.Default.ShutterSpeed,
        isSelected = selectedIndex == 0,
        onClick = { onSelect(0) }
      )

      VintageNavItem(
        title = "SUNNY 16",
        icon = Icons.Default.WbSunny,
        isSelected = selectedIndex == 1,
        onClick = { onSelect(1) }
      )

      VintageNavItem(
        title = "SETTINGS & LOG",
        icon = Icons.Default.Book,
        isSelected = selectedIndex == 2,
        onClick = { onSelect(2) }
      )

      BrassScrew(sizeDp = 10, slotAngleDeg = 75f)
    }
  }
}

@Composable
private fun VintageNavItem(
  title: String,
  icon: ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(
        if (isSelected) Color(0xFF1E1712) else Color.Transparent
      )
      .border(
        width = if (isSelected) 1.dp else 0.dp,
        color = if (isSelected) VintageBrassPrimary else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = if (isSelected) VintageBrassBright else VintageIvoryDark,
        modifier = Modifier.size(19.dp)
      )
      Text(
        text = title,
        fontSize = 9.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.5.sp,
        color = if (isSelected) VintageBrassBright else VintageIvoryDark,
        maxLines = 1
      )
    }
  }
}
