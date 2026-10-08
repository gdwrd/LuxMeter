package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VintageBrassBezel
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageLeatherDark
import com.example.ui.theme.VintageLeatherSurface
import com.example.ui.theme.VintageWitnessRed
import com.example.ui.theme.VintageWoodBorder
import com.example.ui.theme.VintageWoodDark
import com.example.ui.theme.VintageWoodWalnut

@Composable
fun VintageCameraChassis(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(VintageLeatherDark)
  ) {
    // Subtle leather grain texture overlay via Canvas
    Canvas(modifier = Modifier.fillMaxSize()) {
      // Background gradient
      drawRect(
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFF14100E),
            Color(0xFF0D0B0A),
            Color(0xFF16120F)
          )
        )
      )
    }

    content()
  }
}

/**
 * Realistic brushed brass slotted screw head
 */
@Composable
fun BrassScrew(
  modifier: Modifier = Modifier,
  sizeDp: Int = 12,
  slotAngleDeg: Float = 45f
) {
  Box(
    modifier = modifier
      .size(sizeDp.dp)
      .shadow(2.dp, CircleShape)
      .clip(CircleShape)
      .background(
        Brush.radialGradient(
          colors = listOf(
            VintageBrassBright,
            VintageBrassPrimary,
            VintageBrassDark
          )
        )
      )
      .border(0.8.dp, Color(0xFF5E4514), CircleShape)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val r = size.width * 0.38f
      val rad = Math.toRadians(slotAngleDeg.toDouble()).toFloat()
      val dx = r * kotlin.math.cos(rad)
      val dy = r * kotlin.math.sin(rad)

      // Slotted screw groove
      drawLine(
        color = Color(0xFF281C09),
        start = Offset(center.x - dx, center.y - dy),
        end = Offset(center.x + dx, center.y + dy),
        strokeWidth = (size.width * 0.18f).coerceAtLeast(1.5f)
      )
    }
  }
}

/**
 * Top Walnut Wood Inlay plate with brass bezel
 */
@Composable
fun VintageTopPlate(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  trailingContent: @Composable () -> Unit = {}
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(64.dp)
      .shadow(6.dp, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
      .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            VintageWoodDark,
            VintageWoodWalnut,
            Color(0xFF2B180D)
          )
        )
      )
      .border(
        width = 1.8.dp,
        brush = Brush.horizontalGradient(
          colors = listOf(
            VintageBrassDark,
            VintageBrassPrimary,
            VintageBrassDark
          )
        ),
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
      )
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f, fill = false),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        BrassScrew(sizeDp = 10, slotAngleDeg = 30f)

        Column(modifier = Modifier.weight(1f, fill = false)) {
          Text(
            text = title.uppercase(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 1.2.sp,
            color = VintageBrassBright,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )
          Text(
            text = subtitle.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
            color = Color(0xFFBCA683),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        trailingContent()
        BrassScrew(sizeDp = 10, slotAngleDeg = 120f)
      }
    }
  }
}

/**
 * Mechanical circular frame counter window
 * (Like Hasselblad A12 or Leica M film counter)
 */
@Composable
fun MechanicalFrameCounter(
  currentFrame: Int,
  totalFrames: Int,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .shadow(4.dp, RoundedCornerShape(8.dp))
      .clip(RoundedCornerShape(8.dp))
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFF1E1813),
            Color(0xFF140F0C)
          )
        )
      )
      .border(1.5.dp, VintageBrassBezel, RoundedCornerShape(8.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Text(
        text = "EXP",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = VintageWitnessRed,
        fontFamily = FontFamily.Monospace
      )
      Text(
        text = String.format(java.util.Locale.US, "%02d/%02d", currentFrame, totalFrames),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = VintageIvoryText,
        fontFamily = FontFamily.Monospace
      )
    }
  }
}
