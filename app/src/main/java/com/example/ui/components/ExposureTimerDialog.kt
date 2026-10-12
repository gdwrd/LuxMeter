package com.example.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.sensor.HapticManager
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun ExposureTimerDialog(
  rawSeconds: Double,
  correctedSeconds: Double,
  stockName: String,
  hapticManager: HapticManager,
  onDismiss: () -> Unit
) {
  val meteredSeconds = rememberSaveable { rawSeconds }
  val exposureSeconds = rememberSaveable { correctedSeconds }
  val filmName = rememberSaveable { stockName }
  val validDuration = exposureSeconds.isFinite() && exposureSeconds > 0.0 &&
    exposureSeconds < Long.MAX_VALUE / 1000.0
  val totalMillis = if (validDuration) (exposureSeconds * 1000).roundToLong().coerceAtLeast(1000L) else 0L
  var remainingMillis by rememberSaveable { mutableLongStateOf(totalMillis) }
  var remainingAtStart by rememberSaveable { mutableLongStateOf(totalMillis) }
  var startedAt by rememberSaveable { mutableLongStateOf(0L) }
  var running by rememberSaveable { mutableStateOf(false) }
  var finished by rememberSaveable { mutableStateOf(false) }
  var confirmCancel by rememberSaveable { mutableStateOf(false) }
  val view = LocalView.current

  DisposableEffect(view, running) {
    val previous = view.keepScreenOn
    if (running) view.keepScreenOn = true
    onDispose { view.keepScreenOn = previous }
  }

  // Elapsed time remains accurate after delayed frames or a configuration change.
  LaunchedEffect(running) {
    while (running) {
      val previousSecond = remainingMillis / 1000
      remainingMillis = remainingExposureMillis(remainingAtStart, startedAt, SystemClock.elapsedRealtime())
      if (remainingMillis == 0L) {
        running = false
        finished = true
        confirmCancel = false
        hapticManager.performShutterRelease(view)
      } else {
        if (remainingMillis / 1000 != previousSecond) hapticManager.performDialClick(view)
        delay(100)
      }
    }
  }

  val close = {
    if (running) confirmCancel = true else onDismiss()
  }

  AlertDialog(
    onDismissRequest = close,
    title = { Text(if (finished) "Exposure complete" else "Exposure timer", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (validDuration) {
          Text(filmName, style = MaterialTheme.typography.titleMedium)
          Text(
            String.format(Locale.getDefault(), "Metered %.1f s · corrected %.1f s", meteredSeconds, exposureSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            if (finished) "Done"
            else if (remainingMillis >= 60_000L) String.format(Locale.getDefault(), "%d:%02d", remainingMillis / 60_000L, remainingMillis % 60_000L / 1000L)
            else String.format(Locale.getDefault(), "%.1f s", remainingMillis / 1000.0),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
          )
          LinearProgressIndicator(
            progress = { (remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
          )
          Text(
            if (finished) "Close your camera's shutter."
            else "Start your camera's shutter separately. Keep this screen open while timing.",
            style = MaterialTheme.typography.bodyMedium
          )
          if (!running && remainingMillis != totalMillis) {
            TextButton(onClick = {
              remainingMillis = totalMillis
              remainingAtStart = totalMillis
              finished = false
            }) { Text("Reset timer") }
          }
        } else {
          Text("This exposure cannot be timed. Check the film's long-exposure profile.")
        }
      }
    },
    confirmButton = {
      if (validDuration && !finished) {
        Button(onClick = {
          if (running) {
            remainingMillis = remainingExposureMillis(remainingAtStart, startedAt, SystemClock.elapsedRealtime())
            running = false
            if (remainingMillis == 0L) finished = true
          } else {
            remainingAtStart = remainingMillis
            startedAt = SystemClock.elapsedRealtime()
            running = true
          }
          hapticManager.performDialClick(view)
        }) {
          Text(if (running) "Pause" else if (remainingMillis < totalMillis) "Resume" else "Start timer")
        }
      } else {
        TextButton(onClick = onDismiss) { Text("Done") }
      }
    },
    dismissButton = {
      if (validDuration && !finished) TextButton(onClick = close) { Text("Close") }
    }
  )

  if (confirmCancel) {
    AlertDialog(
      onDismissRequest = { confirmCancel = false },
      title = { Text("Cancel the running timer?") },
      text = { Text("The countdown will stop. Your camera's shutter still needs to be closed separately.") },
      confirmButton = {
        TextButton(onClick = {
          running = false
          confirmCancel = false
          onDismiss()
        }) { Text("Cancel timer", color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep timing") } }
    )
  }
}

internal fun remainingExposureMillis(remainingAtStart: Long, startedAt: Long, now: Long): Long =
  (remainingAtStart - (now - startedAt).coerceAtLeast(0L)).coerceAtLeast(0L)
