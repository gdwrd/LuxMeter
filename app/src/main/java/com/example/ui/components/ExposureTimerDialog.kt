package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.sensor.HapticManager
import com.example.ui.theme.VintageBrassBezel
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryMuted
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageLeatherDark
import com.example.ui.theme.VintageLeatherSurface
import com.example.ui.theme.VintageWitnessRed
import com.example.ui.theme.VintageWoodWalnut
import kotlinx.coroutines.delay

@Composable
fun ExposureTimerDialog(
  rawSeconds: Double,
  correctedSeconds: Double,
  stockName: String,
  hapticManager: HapticManager,
  onDismiss: () -> Unit
) {
  val view = LocalView.current
  val totalSec = correctedSeconds.coerceAtLeast(1.0)
  var remainingSec by remember { mutableDoubleStateOf(totalSec) }
  var isRunning by remember { mutableStateOf(false) }
  var isFinished by remember { mutableStateOf(false) }

  LaunchedEffect(isRunning, remainingSec) {
    if (isRunning && remainingSec > 0.0) {
      delay(100)
      remainingSec = (remainingSec - 0.1).coerceAtLeast(0.0)
      if (Math.round(remainingSec * 10) % 10 == 0L) {
        hapticManager.performDialClick(view)
      }
      if (remainingSec <= 0.0) {
        isRunning = false
        isFinished = true
        hapticManager.performShutterRelease(view)
      }
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(16.dp, RoundedCornerShape(20.dp))
        .clip(RoundedCornerShape(20.dp))
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              VintageLeatherSurface,
              VintageLeatherDark,
              Color(0xFF19120E)
            )
          )
        )
        .border(2.dp, VintageBrassPrimary, RoundedCornerShape(20.dp))
        .padding(20.dp)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          BrassScrew(sizeDp = 10)
          Text(
            text = "RECIPROCITY TIMER",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 1.2.sp,
            color = VintageBrassBright
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close timer",
              tint = VintageIvoryMuted
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Stock & Compensation Note
        Box(
          modifier = Modifier
            .background(Color(0xFF261D15), RoundedCornerShape(8.dp))
            .border(1.dp, VintageBrassDark, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = stockName.uppercase(),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = VintageBrassBright,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "Metered: ${String.format(java.util.Locale.US, "%.1f", rawSeconds)}s  ➔  Corrected: ${String.format(java.util.Locale.US, "%.1f", correctedSeconds)}s",
              fontSize = 11.sp,
              color = VintageIvoryMuted,
              fontFamily = FontFamily.Monospace
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Big Circular Stopwatch Dial
        Box(
          modifier = Modifier
            .size(170.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(
              Brush.radialGradient(
                listOf(
                  Color(0xFF2E241B),
                  Color(0xFF17120E)
                )
              )
            )
            .border(2.5.dp, VintageBrassBezel, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          val progress = (remainingSec / totalSec).toFloat().coerceIn(0f, 1f)
          CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(152.dp),
            color = if (isFinished) Color(0xFF4CAF50) else VintageBrassPrimary,
            trackColor = Color(0xFF332619),
            strokeWidth = 6.dp
          )

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = if (isFinished) "DONE" else String.format(java.util.Locale.US, "%.1f", remainingSec),
              fontSize = if (isFinished) 28.sp else 38.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = if (isFinished) Color(0xFF81C784) else VintageIvoryText
            )
            Text(
              text = if (isFinished) "EXPOSURE COMPLETE" else "SECONDS REMAINING",
              fontSize = 9.sp,
              letterSpacing = 1.sp,
              fontFamily = FontFamily.Monospace,
              color = VintageIvoryMuted
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: Start / Pause / Reset
        Row(
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = {
              isRunning = false
              remainingSec = totalSec
              isFinished = false
              hapticManager.performDialClick(view)
            },
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = VintageIvoryText
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
              brush = Brush.linearGradient(listOf(VintageBrassDark, VintageBrassPrimary))
            ),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset timer", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("RESET", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = {
              if (isFinished) {
                remainingSec = totalSec
                isFinished = false
                isRunning = true
              } else {
                isRunning = !isRunning
              }
              hapticManager.performShutterRelease(view)
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isRunning) VintageWitnessRed else VintageBrassPrimary,
              contentColor = Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(
              imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
              contentDescription = if (isRunning) "Stop" else "Start",
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isRunning) "PAUSE" else if (isFinished) "RESTART" else "START SHUTTER",
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}
