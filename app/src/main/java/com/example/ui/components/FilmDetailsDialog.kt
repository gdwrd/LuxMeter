package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.FilmStock

@Composable
fun FilmDetailsDialog(stock: FilmStock, onDismiss: () -> Unit) {
  val context = LocalContext.current
  var linkUnavailable by remember(stock.id) { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stock.name) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${stock.brand} · ISO ${stock.iso} · ${stock.type}", style = MaterialTheme.typography.bodyMedium)
        Text(stock.correctionLabel, style = MaterialTheme.typography.titleMedium)
        Text(stock.rangeText, style = MaterialTheme.typography.bodyLarge)
        stock.sourceUrl?.let { source ->
          TextButton(onClick = {
            try {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source)))
            } catch (_: ActivityNotFoundException) {
              linkUnavailable = true
            } catch (_: SecurityException) {
              linkUnavailable = true
            }
          }) { Text("Open film maker's guidance") }
        }
        if (linkUnavailable) Text("No app is available to open this link.", color = MaterialTheme.colorScheme.error)
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
  )
}
