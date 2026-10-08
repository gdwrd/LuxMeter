package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CameraFormat
import com.example.sensor.DepthOfFieldCalculator
import com.example.sensor.DofResult
import com.example.sensor.HapticManager
import com.example.ui.theme.DialKnurlColor
import com.example.ui.theme.VintageBrassBezel
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryDark
import com.example.ui.theme.VintageIvoryMuted
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageLeatherDark
import com.example.ui.theme.VintageLeatherSurface
import com.example.ui.theme.VintageWitnessAmber
import com.example.ui.theme.VintageWitnessRed
import com.example.ui.theme.VintageWoodWalnut
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DepthOfFieldScale(
  format: CameraFormat,
  focalLengthMm: Int,
  apertureValue: Double,
  apertureLabel: String,
  focusDistanceMeters: Double,
  dofResult: DofResult,
  hapticManager: HapticManager,
  onFocalLengthChanged: (Int) -> Unit,
  onFocusDistanceChanged: (Double) -> Unit,
  onSetToHyperfocal: () -> Unit,
  modifier: Modifier = Modifier
) {
  val view = LocalView.current
  val commonFocals = DepthOfFieldCalculator.getCommonFocalLengths(format)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .shadow(8.dp, RoundedCornerShape(16.dp))
      .clip(RoundedCornerShape(16.dp))
      .background(VintageLeatherSurface)
      .border(1.8.dp, VintageBrassBezel, RoundedCornerShape(16.dp))
      .padding(14.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        BrassScrew(sizeDp = 10)
        Column {
          Text(
            text = "LENS DEPTH OF FIELD",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 1.2.sp,
            color = VintageBrassBright
          )
          Text(
            text = "${format.displayName} Gate • $apertureLabel • ${DepthOfFieldCalculator.getApertureEquivalenceSummary(apertureValue, format)}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = if (format != CameraFormat.FORMAT_35MM) VintageWitnessAmber else VintageIvoryMuted
          )
          Text(
            text = DepthOfFieldCalculator.getApertureRecommendation(apertureValue, format),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryMuted
          )
        }
      }

      // Hyperfocal Shortcut Button
      Button(
        onClick = {
          hapticManager.performDialClick(view)
          onSetToHyperfocal()
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = VintageBrassPrimary,
          contentColor = Color.Black
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
      ) {
        Icon(Icons.Default.AllInclusive, contentDescription = null, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "HYPERFOCAL",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 1. Focal Length Selector Row
    Text(
      text = "LENS FOCAL LENGTH:",
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace,
      color = VintageBrassPrimary
    )
    Spacer(modifier = Modifier.height(4.dp))

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(commonFocals) { fMm ->
        val isSelected = fMm == focalLengthMm
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) VintageBrassPrimary else Color(0xFF261D15))
            .border(
              0.8.dp,
              if (isSelected) VintageBrassBright else VintageBrassDark,
              RoundedCornerShape(6.dp)
            )
            .clickable {
              hapticManager.performDialClick(view)
              onFocalLengthChanged(fMm)
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "${fMm}mm",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) Color.Black else VintageIvoryText
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 2. Vintage Mechanical Lens Barrel Graphic
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(100.dp)
        .shadow(6.dp, RoundedCornerShape(10.dp))
        .clip(RoundedCornerShape(10.dp))
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xFF14100D),
              DialKnurlColor,
              Color(0xFF1F1813),
              Color(0xFF120E0B)
            )
          )
        )
        .border(1.2.dp, VintageBrassDark, RoundedCornerShape(10.dp))
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f

        // Top Knurled Rim
        val ribCount = 36
        val ribW = w / ribCount
        for (i in 0..ribCount) {
          val rx = i * ribW
          drawLine(
            color = Color(0x33B8860B),
            start = Offset(rx, 0f),
            end = Offset(rx, 6f),
            strokeWidth = 1.5f
          )
        }

        // Mechanical In-Focus Zone Indicator Bar
        // Logarithmic visual mapping across the barrel
        val minScaleDist = 0.5
        val maxScaleDist = 25.0

        fun distToX(dist: Double): Float {
          val clamped = dist.coerceIn(minScaleDist, maxScaleDist)
          val norm = (kotlin.math.log10(clamped) - kotlin.math.log10(minScaleDist)) /
            (kotlin.math.log10(maxScaleDist) - kotlin.math.log10(minScaleDist))
          return (w * 0.1f) + (norm.toFloat() * (w * 0.8f))
        }

        val nearX = distToX(dofResult.nearMeters)
        val farX = if (dofResult.isFarInfinity) (w * 0.92f) else distToX(dofResult.farMeters)

        // Draw Amber DOF Zone Bar
        val barY = h * 0.42f
        val barH = 12f
        drawRoundRect(
          color = VintageWitnessAmber.copy(alpha = 0.25f),
          topLeft = Offset(nearX, barY),
          size = Size((farX - nearX).coerceAtLeast(8f), barH),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
        drawRoundRect(
          color = VintageWitnessAmber,
          topLeft = Offset(nearX, barY),
          size = Size((farX - nearX).coerceAtLeast(8f), barH),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
          style = Stroke(width = 1.5f)
        )

        // Distance markings along the ring
        val standardDistances = listOf(0.7, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0, 20.0)
        standardDistances.forEach { d ->
          val dx = distToX(d)
          val isCurrent = abs(d - focusDistanceMeters) < 0.2
          // Meter graduation tick
          drawLine(
            color = if (isCurrent) VintageBrassBright else VintageIvoryMuted.copy(alpha = 0.5f),
            start = Offset(dx, 8f),
            end = Offset(dx, 16f),
            strokeWidth = if (isCurrent) 2.5f else 1.2f
          )
        }

        drawContext.canvas.nativeCanvas.apply {
          val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
              android.graphics.Typeface.MONOSPACE,
              android.graphics.Typeface.BOLD
            )
          }

          standardDistances.forEach { d ->
            val dx = distToX(d)
            val isCurrent = abs(d - focusDistanceMeters) < 0.2

            // Numbers in meters (White)
            textPaint.textSize = if (isCurrent) 22f else 17f
            textPaint.color = if (isCurrent) {
              android.graphics.Color.WHITE
            } else {
              android.graphics.Color.argb(160, 236, 228, 208)
            }
            val label = if (d >= 20.0) "∞" else "${d}m".replace(".0", "")
            drawText(label, dx, 34f, textPaint)

            // Numbers in feet (Vintage Zeiss/Leica Amber Gold)
            textPaint.textSize = 14f
            textPaint.color = android.graphics.Color.argb(170, 255, 179, 0)
            val ft = (d * 3.28).roundToInt()
            val ftLabel = if (d >= 20.0) "∞" else "${ft}ft"
            drawText(ftLabel, dx, h - 8f, textPaint)
          }

          // Center DOF Witness Aperture Marks
          // Like 16  8  4 | 4  8  16
          val dofPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
              android.graphics.Typeface.MONOSPACE,
              android.graphics.Typeface.BOLD
            )
            textSize = 15f
            color = android.graphics.Color.parseColor("#B8860B")
          }
          drawText("22", centerX - 54f, h * 0.76f, dofPaint)
          drawText("16", centerX - 36f, h * 0.76f, dofPaint)
          drawText("8", centerX - 18f, h * 0.76f, dofPaint)
          drawText("8", centerX + 18f, h * 0.76f, dofPaint)
          drawText("16", centerX + 36f, h * 0.76f, dofPaint)
          drawText("22", centerX + 54f, h * 0.76f, dofPaint)
        }

        // Center Focus Witness Line (Red Diamond)
        val focusX = distToX(focusDistanceMeters)
        drawLine(
          color = VintageWitnessRed,
          start = Offset(focusX, 6f),
          end = Offset(focusX, h - 6f),
          strokeWidth = 2.5f
        )
        drawCircle(
          color = VintageWitnessRed,
          radius = 3.5f,
          center = Offset(focusX, h * 0.5f)
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 3. Tactile Focus Distance Slider & Value
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "FOCUS DISTANCE:",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = VintageBrassPrimary
      )

      Box(
        modifier = Modifier
          .background(Color(0xFF261D15), RoundedCornerShape(4.dp))
          .border(1.dp, VintageBrassDark, RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 2.dp)
      ) {
        Text(
          text = "${DepthOfFieldCalculator.formatDistance(focusDistanceMeters)} (${DepthOfFieldCalculator.metersToFeet(focusDistanceMeters)})",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          color = VintageBrassBright
        )
      }
    }

    Slider(
      value = focusDistanceMeters.toFloat(),
      onValueChange = { newVal ->
        val step = (newVal * 10f).roundToInt() / 10.0
        onFocusDistanceChanged(step.coerceAtLeast(0.5))
      },
      valueRange = 0.5f..15.0f,
      colors = SliderDefaults.colors(
        thumbColor = VintageBrassBright,
        activeTrackColor = VintageBrassPrimary,
        inactiveTrackColor = Color(0xFF382A1D)
      )
    )

    // Quick distance preset buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      val presets = listOf(
        "Portrait (1.2m)" to 1.2,
        "Medium (2.5m)" to 2.5,
        "Street (4m)" to 4.0,
        "Infinity (∞)" to 25.0
      )
      presets.forEach { (lbl, dVal) ->
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF221A14))
            .border(0.8.dp, VintageBrassDark.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable {
              hapticManager.performDialClick(view)
              onFocusDistanceChanged(dVal)
            }
            .padding(vertical = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = lbl,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryMuted,
            maxLines = 1
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. In-Focus Summary Display Cards
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Near Limit
      Box(
        modifier = Modifier
          .weight(1f)
          .background(Color(0xFF241C16), RoundedCornerShape(8.dp))
          .border(1.dp, VintageBrassDark, RoundedCornerShape(8.dp))
          .padding(8.dp)
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "NEAR LIMIT",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageWitnessAmber
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = DepthOfFieldCalculator.formatDistance(dofResult.nearMeters),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryText
          )
          Text(
            text = DepthOfFieldCalculator.metersToFeet(dofResult.nearMeters),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryMuted
          )
        }
      }

      // Far Limit
      Box(
        modifier = Modifier
          .weight(1f)
          .background(Color(0xFF241C16), RoundedCornerShape(8.dp))
          .border(1.dp, VintageBrassDark, RoundedCornerShape(8.dp))
          .padding(8.dp)
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "FAR LIMIT",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageWitnessAmber
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = if (dofResult.isFarInfinity) "∞" else DepthOfFieldCalculator.formatDistance(dofResult.farMeters),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryText
          )
          Text(
            text = if (dofResult.isFarInfinity) "∞" else DepthOfFieldCalculator.metersToFeet(dofResult.farMeters),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryMuted
          )
        }
      }

      // Total In-Focus Depth
      Box(
        modifier = Modifier
          .weight(1f)
          .background(Color(0xFF241C16), RoundedCornerShape(8.dp))
          .border(1.dp, VintageBrassPrimary, RoundedCornerShape(8.dp))
          .padding(8.dp)
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "TOTAL DEPTH",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageBrassBright
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = if (dofResult.isFarInfinity) "To ∞" else DepthOfFieldCalculator.formatDistance(dofResult.totalDepthMeters),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = VintageBrassBright
          )
          Text(
            text = "Hyper: ${DepthOfFieldCalculator.formatDistance(dofResult.hyperfocalMeters)}",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageWitnessAmber
          )
        }
      }
    }
  }
}
