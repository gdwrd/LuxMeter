package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensor.HapticManager
import com.example.ui.theme.DialKnurlColor
import com.example.ui.theme.VintageBrassBezel
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryMuted
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageWitnessRed
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VintageDial(
  title: String,
  items: List<String>,
  selectedIndex: Int,
  onIndexChanged: (Int) -> Unit,
  hapticManager: HapticManager,
  modifier: Modifier = Modifier,
  unitLabel: String = ""
) {
  val coroutineScope = rememberCoroutineScope()
  val view = LocalView.current
  val itemSpacingPx = 64f // Pixel width per dial step

  val offsetAnim = remember { Animatable(selectedIndex * itemSpacingPx) }

  // Sync animation when external selection changes
  LaunchedEffect(selectedIndex) {
    if (abs(offsetAnim.value - (selectedIndex * itemSpacingPx)) > 1f) {
      offsetAnim.animateTo(
        targetValue = selectedIndex * itemSpacingPx,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
      )
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top Header: Title, Current Value in brass badge
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = VintageBrassPrimary,
        fontFamily = FontFamily.Monospace
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          modifier = Modifier
            .background(
              brush = Brush.horizontalGradient(
                listOf(Color(0xFF261D15), Color(0xFF1E1610))
              ),
              shape = RoundedCornerShape(4.dp)
            )
            .border(1.dp, VintageBrassDark, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
          Text(
            text = items.getOrElse(selectedIndex) { "" },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = VintageBrassBright,
            fontFamily = FontFamily.Monospace
          )
        }

        if (unitLabel.isNotEmpty()) {
          Text(
            text = unitLabel,
            fontSize = 10.sp,
            color = VintageIvoryMuted,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // Cylindrical knurled barrel body
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .shadow(6.dp, RoundedCornerShape(8.dp))
        .clip(RoundedCornerShape(8.dp))
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color(0xFF181410),
              DialKnurlColor,
              Color(0xFF16120F),
              Color(0xFF0F0C0A)
            )
          )
        )
        .border(
          width = 1.5.dp,
          brush = Brush.linearGradient(
            listOf(VintageBrassDark, VintageBrassBezel, VintageBrassDark)
          ),
          shape = RoundedCornerShape(8.dp)
        )
    ) {
      // Dial canvas with scrolling graduation marks
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(items.size) {
            var startAnimVal = offsetAnim.value
            detectDragGestures(
              onDragStart = {
                startAnimVal = offsetAnim.value
              },
              onDragEnd = {
                val targetIndex = (offsetAnim.value / itemSpacingPx)
                  .roundToInt()
                  .coerceIn(0, items.lastIndex)
                coroutineScope.launch {
                  offsetAnim.animateTo(
                    targetValue = targetIndex * itemSpacingPx,
                    animationSpec = spring(stiffness = 350f, dampingRatio = 0.75f)
                  )
                  if (targetIndex != selectedIndex) {
                    hapticManager.performDialClick(view)
                    onIndexChanged(targetIndex)
                  }
                }
              },
              onDragCancel = {
                val targetIndex = (offsetAnim.value / itemSpacingPx)
                  .roundToInt()
                  .coerceIn(0, items.lastIndex)
                coroutineScope.launch {
                  offsetAnim.animateTo(targetIndex * itemSpacingPx)
                }
              },
              onDrag = { change, dragAmount ->
                change.consume()
                val newOffset = (offsetAnim.value - dragAmount.x).coerceIn(
                  0f,
                  items.lastIndex * itemSpacingPx
                )
                coroutineScope.launch {
                  offsetAnim.snapTo(newOffset)
                  val stepIdx = (newOffset / itemSpacingPx).roundToInt()
                  if (stepIdx != selectedIndex && stepIdx in items.indices) {
                    hapticManager.performDialClick(view)
                    onIndexChanged(stepIdx)
                  }
                }
              }
            )
          }
      ) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val currentOffset = offsetAnim.value

        // Draw knurled texture ribs along top & bottom edge
        val ribCount = 38
        val ribSpacing = w / ribCount
        for (i in 0..ribCount) {
          val rx = i * ribSpacing
          drawLine(
            color = Color(0x33B8860B),
            start = Offset(rx, 0f),
            end = Offset(rx, 7f),
            strokeWidth = 2f
          )
          drawLine(
            color = Color(0x33B8860B),
            start = Offset(rx, h - 7f),
            end = Offset(rx, h),
            strokeWidth = 2f
          )
        }

        // Draw ticks and labels for each item
        drawContext.canvas.nativeCanvas.apply {
          val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
              android.graphics.Typeface.MONOSPACE,
              android.graphics.Typeface.BOLD
            )
          }

          items.forEachIndexed { index, label ->
            val itemX = centerX + (index * itemSpacingPx - currentOffset)
            if (itemX in -60f..(w + 60f)) {
              val distFromCenter = abs(itemX - centerX)
              val alpha = (1f - (distFromCenter / (w * 0.45f))).coerceIn(0.15f, 1f)

              // Tick mark
              val tickHeight = if (index == selectedIndex) 16f else 10f
              drawLine(
                color = if (index == selectedIndex) {
                  VintageBrassBright.copy(alpha = alpha)
                } else {
                  VintageIvoryMuted.copy(alpha = alpha * 0.7f)
                },
                start = Offset(itemX, 10f),
                end = Offset(itemX, 10f + tickHeight),
                strokeWidth = if (index == selectedIndex) 3f else 1.5f
              )

              // Label text
              textPaint.textSize = if (index == selectedIndex) 30f else 24f
              textPaint.color = if (index == selectedIndex) {
                android.graphics.Color.argb((255 * alpha).toInt(), 243, 210, 121)
              } else {
                android.graphics.Color.argb((180 * alpha).toInt(), 180, 170, 155)
              }
              drawText(label, itemX, h * 0.76f, textPaint)
            }
          }
        }

        // Center Red Witness Mark (Mechanical index indicator on vintage lens)
        drawLine(
          color = VintageWitnessRed,
          start = Offset(centerX, 2f),
          end = Offset(centerX, 18f),
          strokeWidth = 3f
        )
        drawLine(
          color = VintageWitnessRed,
          start = Offset(centerX, h - 18f),
          end = Offset(centerX, h - 2f),
          strokeWidth = 3f
        )
        drawCircle(
          color = VintageWitnessRed,
          radius = 3.5f,
          center = Offset(centerX, 6f)
        )

        // Vignette gradient edges (giving authentic 3D cylindrical wheel depth)
        drawRect(
          brush = Brush.horizontalGradient(
            colors = listOf(Color(0xE60F0C0A), Color.Transparent),
            startX = 0f,
            endX = w * 0.22f
          ),
          topLeft = Offset.Zero,
          size = Size(w * 0.22f, h)
        )
        drawRect(
          brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color(0xE60F0C0A)),
            startX = w * 0.78f,
            endX = w
          ),
          topLeft = Offset(w * 0.78f, 0f),
          size = Size(w * 0.22f, h)
        )
      }

      // Left and Right stepping buttons for precise tap adjustment
      IconButton(
        onClick = {
          if (selectedIndex > 0) {
            hapticManager.performDialClick(view)
            onIndexChanged(selectedIndex - 1)
          }
        },
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = 2.dp)
          .size(36.dp)
      ) {
        Box(
          modifier = Modifier
            .size(24.dp)
            .background(Color(0x99241B13), CircleShape)
            .border(1.dp, VintageBrassDark.copy(alpha = 0.5f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = "Previous $title",
            tint = VintageBrassPrimary,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      IconButton(
        onClick = {
          if (selectedIndex < items.lastIndex) {
            hapticManager.performDialClick(view)
            onIndexChanged(selectedIndex + 1)
          }
        },
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = 2.dp)
          .size(36.dp)
      ) {
        Box(
          modifier = Modifier
            .size(24.dp)
            .background(Color(0x99241B13), CircleShape)
            .border(1.dp, VintageBrassDark.copy(alpha = 0.5f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Next $title",
            tint = VintageBrassPrimary,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}
