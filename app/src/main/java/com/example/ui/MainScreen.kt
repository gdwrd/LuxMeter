package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.meter.LightMeterScreen
import com.example.ui.meter.LightMeterViewModel
import com.example.ui.settings.RollsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.sunny16.Sunny16GuideScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LightMeterViewModel = viewModel()) {
  var selectedTab by rememberSaveable { mutableStateOf("meter") }
  var detail by rememberSaveable { mutableStateOf<String?>(null) }
  var returnToSettings by rememberSaveable { mutableStateOf(false) }
  val snackbar = remember { SnackbarHostState() }
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

  LaunchedEffect(userMessage) {
    userMessage?.let {
      snackbar.showSnackbar(it)
      viewModel.clearUserMessage()
    }
  }
  BackHandler(enabled = detail != null || selectedTab != "meter") {
    if (detail == "sunny16" && returnToSettings) {
      detail = "settings"
      returnToSettings = false
    } else if (detail != null) detail = null else selectedTab = "meter"
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = {
          Text(when (detail) {
            "settings", "camera_settings" -> "Settings"
            "sunny16" -> "Sunny 16"
            else -> if (selectedTab == "meter") "LuxMeter" else "Rolls"
          })
        },
        navigationIcon = {
          if (detail != null) {
            IconButton(onClick = {
              detail = if (detail == "sunny16" && returnToSettings) "settings" else null
              returnToSettings = false
            }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        },
        actions = {
          if (detail == null) {
            IconButton(onClick = { detail = "settings" }) {
              Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
    },
    bottomBar = {
      if (detail == null) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
          NavigationBarItem(
            selected = selectedTab == "meter",
            onClick = { selectedTab = "meter" },
            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
            label = { Text("Meter") }
          )
          NavigationBarItem(
            selected = selectedTab == "rolls",
            onClick = { selectedTab = "rolls" },
            icon = { Icon(Icons.Default.CollectionsBookmark, contentDescription = null) },
            label = { Text("Rolls") }
          )
        }
      }
    },
    snackbarHost = { SnackbarHost(snackbar) }
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when (detail) {
        "settings", "camera_settings" -> SettingsScreen(viewModel, openCameraSetup = detail == "camera_settings", onOpenSunny16 = {
          returnToSettings = true
          detail = "sunny16"
        })
        "sunny16" -> Sunny16GuideScreen(viewModel, onApplyPreset = { _, _, _ ->
          selectedTab = "meter"
          returnToSettings = false
          detail = null
        })
        else -> if (selectedTab == "meter") {
          LightMeterScreen(viewModel, onOpenSunny16 = { detail = "sunny16" },
            onOpenRolls = { selectedTab = "rolls"; detail = null },
            onOpenCameraSetup = { detail = "camera_settings" })
        } else {
          RollsScreen(viewModel)
        }
      }
    }
  }
}
