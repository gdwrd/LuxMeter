package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageMeterFace
import com.example.ui.theme.VintageNeedleRed
import com.example.ui.theme.VintageWitnessRed
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnalogMeterGauge(
  targetEv: Double,
  lux: Float,
  isLocked: Boolean,
  modifier: Modifier = Modifier,
  onToggleHold: (() -> Unit)? = null,
  onToggleViewfinder: (() -> Unit)? = null
) {
  // Smoothly damp needle movement like a vintage magnetic galvanometer coil
  val animatedEv by animateFloatAsState(
    targetValue = targetEv.toFloat().coerceIn(1f, 18f),
    animationSpec = spring(dampingRatio = 0.65f, stiffness = 120f),
    label = "meter_needle"
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(180.dp)
      .shadow(8.dp, RoundedCornerShape(16.dp))
      .clip(RoundedCornerShape(16.dp))
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF2A2017),
            Color(0xFF1B140E),
            Color(0xFF261C14)
          )
        )
      )
      .border(
        width = 2.5.dp,
        brush = Brush.linearGradient(
          colors = listOf(
            VintageBrassPrimary,
            VintageBrassDark,
            VintageBrassPrimary
          )
        ),
        shape = RoundedCornerShape(16.dp)
      )
      .padding(8.dp)
  ) {
    // Meter dial canvas
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // 1. Draw Ivory Enamel Meter Scale Plate
      val plateCorner = 12.dp.toPx()
      drawRoundRect(
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFFFCF9EE),
            VintageMeterFace,
            Color(0xFFEFE8D3)
          )
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(plateCorner, plateCorner)
      )

      // Inner subtle border on plate
      drawRoundRect(
        color = Color(0xFFC7BDA4),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(plateCorner, plateCorner),
        style = Stroke(width = 1.5f)
      )

      // Pivot point of the needle (bottom center)
      val pivotX = w / 2f
      val pivotY = h + h * 0.45f
      val arcRadius = h * 1.15f

      // Arc angle range (in radians). -130 deg to -50 deg (sweep of 80 degrees)
      val startAngleDeg = -132f
      val endAngleDeg = -48f
      val sweepDeg = endAngleDeg - startAngleDeg

      // Min EV = 1.0, Max EV = 18.0
      val minEv = 1f
      val maxEv = 18f

      // Helper to compute angle for given EV
      fun evToAngleRad(ev: Float): Float {
        val fraction = (ev - minEv) / (maxEv - minEv)
        val deg = startAngleDeg + fraction * sweepDeg
        return Math.toRadians(deg.toDouble()).toFloat()
      }

      // 2. Draw Arc Scales (Black EV baseline and Red limit sectors)
      val arcPath = Path()
      val arcSegments = 60
      for (i in 0..arcSegments) {
        val angle = Math.toRadians((startAngleDeg + (i.toFloat() / arcSegments) * sweepDeg).toDouble()).toFloat()
        val x = pivotX + arcRadius * cos(angle)
        val y = pivotY + arcRadius * sin(angle)
        if (i == 0) arcPath.moveTo(x, y) else arcPath.lineTo(x, y)
      }
      drawPath(
        path = arcPath,
        color = Color(0xFF332E27),
        style = Stroke(width = 2.5f)
      )

      // 3. Draw Ticks & EV Numbers
      drawContext.canvas.nativeCanvas.apply {
        val tickPaint = Paint().apply {
          isAntiAlias = true
          strokeCap = Paint.Cap.ROUND
        }
        val textPaint = Paint().apply {
          isAntiAlias = true
          textAlign = Paint.Align.CENTER
          typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
          color = android.graphics.Color.parseColor("#262019")
        }

        for (ev in 1..18) {
          val rad = evToAngleRad(ev.toFloat())
          val isMajor = ev % 2 == 0 || ev == 1 || ev == 18
          val isSunny16 = ev == 15

          val innerR = if (isMajor) arcRadius - 18f else arcRadius - 10f
          val outerR = arcRadius

          val x1 = pivotX + innerR * cos(rad)
          val y1 = pivotY + innerR * sin(rad)
          val x2 = pivotX + outerR * cos(rad)
          val y2 = pivotY + outerR * sin(rad)

          tickPaint.strokeWidth = if (isMajor) 3.5f else 1.8f
          tickPaint.color = if (isSunny16) {
            android.graphics.Color.parseColor("#D32F2F")
          } else {
            android.graphics.Color.parseColor("#2B251E")
          }

          drawLine(x1, y1, x2, y2, tickPaint)

          if (isMajor) {
            val textR = innerR - 16f
            val tx = pivotX + textR * cos(rad)
            val ty = pivotY + textR * sin(rad) + 6f
            textPaint.textSize = if (isSunny16) 24f else 20f
            textPaint.color = if (isSunny16) {
              android.graphics.Color.parseColor("#D32F2F")
            } else {
              android.graphics.Color.parseColor("#262019")
            }
            drawText("$ev", tx, ty, textPaint)
          }
        }

        // Title and Units in center top
        val titlePaint = Paint().apply {
          isAntiAlias = true
          textAlign = Paint.Align.CENTER
          typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
          textSize = 22f
          color = android.graphics.Color.parseColor("#5A4A38")
          letterSpacing = 0.2f
        }
        drawText("EXPOSURE VALUE (EV₁₀₀)", pivotX, h * 0.22f, titlePaint)

        // Subtitle indicator
        val subPaint = Paint().apply {
          isAntiAlias = true
          textAlign = Paint.Align.CENTER
          typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
          textSize = 17f
          color = android.graphics.Color.parseColor("#80705E")
        }
        drawText("INCIDENT SECONDS / APERTURE MATCH", pivotX, h * 0.33f, subPaint)
      }

      // 4. Draw Red Galvanometer Needle
      val needleRad = evToAngleRad(animatedEv)
      val needleTipR = arcRadius + 4f
      val tipX = pivotX + needleTipR * cos(needleRad)
      val tipY = pivotY + needleTipR * sin(needleRad)

      // Needle shadow
      drawLine(
        color = Color(0x33000000),
        start = Offset(pivotX + 2f, pivotY + 2f),
        end = Offset(tipX + 2f, tipY + 2f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
      )

      // Main crimson needle
      drawLine(
        color = VintageNeedleRed,
        start = Offset(pivotX, pivotY),
        end = Offset(tipX, tipY),
        strokeWidth = 3.5f,
        cap = StrokeCap.Round
      )

      // 5. Needle Brass Pivot Hub (at bottom center)
      val hubRadius = 26f
      drawCircle(
        color = Color(0xFFC6923B),
        radius = hubRadius,
        center = Offset(pivotX, h - 2f)
      )
      drawCircle(
        color = Color(0xFF281F17),
        radius = hubRadius * 0.5f,
        center = Offset(pivotX, h - 2f)
      )
      drawCircle(
        color = Color(0xFFE5B25D),
        radius = hubRadius * 0.22f,
        center = Offset(pivotX, h - 2f)
      )
    }

    // Top readout pill badges
    Box(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(start = 12.dp, bottom = 8.dp)
        .background(
          color = Color(0xDD241D17),
          shape = RoundedCornerShape(6.dp)
        )
        .border(1.dp, VintageBrassDark, RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
      Text(
        text = if (lux >= 1000f) {
          String.format(java.util.Locale.US, "%.1fk lx", lux / 1000f)
        } else {
          String.format(java.util.Locale.US, "%.0f lx", lux)
        },
        color = VintageIvoryText,
        fontSize = 11.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
      )
    }

    // Viewfinder toggle button at top end
    if (onToggleViewfinder != null) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 8.dp, end = 8.dp)
          .background(Color(0xEE1E1813), RoundedCornerShape(6.dp))
          .border(1.dp, VintageBrassPrimary, RoundedCornerShape(6.dp))
          .clickable { onToggleViewfinder() }
          .padding(horizontal = 8.dp, vertical = 3.dp)
      ) {
        Text(
          text = "VIEWFINDER ➔",
          color = VintageBrassPrimary,
          fontSize = 10.sp,
          fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
          fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
      }
    }

    // Lock / Hold button at bottom end
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 12.dp, bottom = 8.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(
          color = if (isLocked) VintageWitnessRed else Color(0xEE241D17)
        )
        .border(
          1.dp,
          if (isLocked) VintageWitnessRed else VintageBrassPrimary,
          RoundedCornerShape(6.dp)
        )
        .clickable(enabled = onToggleHold != null) { onToggleHold?.invoke() }
        .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
      androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
      ) {
        androidx.compose.material3.Icon(
          imageVector = if (isLocked) androidx.compose.material.icons.Icons.Default.Lock else androidx.compose.material.icons.Icons.Default.LockOpen,
          contentDescription = null,
          tint = if (isLocked) Color.White else VintageBrassPrimary,
          modifier = Modifier.size(12.dp)
        )
        Text(
          text = if (isLocked) "HOLD METER" else "LIVE METER",
          color = if (isLocked) Color.White else VintageBrassBright,
          fontSize = 10.sp,
          fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
          fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
      }
    }
  }
}
