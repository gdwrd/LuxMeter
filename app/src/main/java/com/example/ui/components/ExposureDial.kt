package com.example.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
internal fun ExposureDial(
  label: String,
  values: List<String>,
  selectedIndex: Int,
  enabled: Boolean,
  onSelect: (Int) -> Unit,
  onPick: () -> Unit,
  onTick: () -> Unit = {},
  compact: Boolean = false
) {
  val currentIndex by rememberUpdatedState(selectedIndex)
  val select by rememberUpdatedState(onSelect)
  val tick by rememberUpdatedState(onTick)
  val stepPixels = with(LocalDensity.current) { 40.dp.toPx() }
  val showNeighbours = !compact && LocalDensity.current.fontScale < 1.5f
  var dragIndex by remember { mutableIntStateOf(selectedIndex) }
  var distance by remember { mutableFloatStateOf(0f) }
  val dragState = rememberDraggableState { delta ->
    distance += delta
    if (abs(distance) >= stepPixels) {
      val steps = (distance / stepPixels).toInt()
      val next = (dragIndex + steps).coerceIn(0, values.lastIndex)
      distance -= steps * stepPixels
      if (next != dragIndex) {
        dragIndex = next
        select(next)
        tick()
      }
    }
  }
  Column(Modifier.widthIn(max = if (compact) 120.dp else 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
      modifier = Modifier.widthIn(min = if (compact) 96.dp else 180.dp).draggable(
        state = dragState, orientation = Orientation.Horizontal, enabled = enabled && values.size > 1,
        onDragStarted = { dragIndex = currentIndex; distance = 0f },
        onDragStopped = { distance = 0f }
      ).semantics {
        contentDescription = "$label dial"
        stateDescription = values[selectedIndex]
        if (enabled) customActions = listOfNotNull(
          if (selectedIndex > 0) CustomAccessibilityAction("Previous $label value") {
            onSelect(selectedIndex - 1); onTick(); true
          } else null,
          if (selectedIndex < values.lastIndex) CustomAccessibilityAction("Next $label value") {
            onSelect(selectedIndex + 1); onTick(); true
          } else null
        )
      },
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (showNeighbours) Text(values.getOrNull(selectedIndex - 1).orEmpty(), style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      FilledTonalButton(onClick = onPick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(values[selectedIndex])
      }
      if (showNeighbours) Text(values.getOrNull(selectedIndex + 1).orEmpty(), style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(if (compact) "Swipe ↔ · tap" else "Swipe to change · tap to choose", style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
