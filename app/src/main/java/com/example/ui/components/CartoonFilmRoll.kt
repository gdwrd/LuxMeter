package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Paint
import android.graphics.Typeface
import com.example.data.model.FilmStock

@Composable
fun CartoonFilmRoll(
  stock: FilmStock,
  modifier: Modifier = Modifier,
  size: Dp = 80.dp,
  isSelected: Boolean = false
) {
  val scale by animateFloatAsState(
    targetValue = if (isSelected) 1.08f else 1.0f,
    animationSpec = tween(200),
    label = "film_scale"
  )

  Box(
    modifier = modifier.size(size)
  ) {
    Canvas(modifier = Modifier.size(size)) {
      val w = this.size.width * scale
      val h = this.size.height * scale
      val startX = (this.size.width - w) / 2f
      val startY = (this.size.height - h) / 2f

      drawCartoonCanister(
        stock = stock,
        startX = startX,
        startY = startY,
        w = w,
        h = h,
        isSelected = isSelected
      )
    }
  }
}

private fun DrawScope.drawCartoonCanister(
  stock: FilmStock,
  startX: Float,
  startY: Float,
  w: Float,
  h: Float,
  isSelected: Boolean
) {
  // Outline style
  val outlineColor = Color(0xFF171310)
  val outlineWidth = (w * 0.035f).coerceAtLeast(2.5f)

  // Determine canister theme colors
  val (primaryColor, accentColor, textColor, textBanner) = when (stock.badgeStyle) {
    "PORTRA" -> QuadColor(
      Color(0xFFF9D13B), // Kodak Yellow
      Color(0xFFE25232), // Warm Red
      Color(0xFF1E1712),
      "PORTRA"
    )
    "TRI_X" -> QuadColor(
      Color(0xFFF4C430), // Classic Kodak Yellow
      Color(0xFF1E1E1E), // Black band
      Color(0xFFFFFFFF),
      "TRI-X"
    )
    "HP5" -> QuadColor(
      Color(0xFF2B2B2B), // Ilford Dark Charcoal
      Color(0xFFE8E8E8), // White / Silver band
      Color(0xFFE8E8E8),
      "HP5+"
    )
    "GOLD" -> QuadColor(
      Color(0xFFEAB308), // Rich Gold
      Color(0xFFDC2626), // Kodak Red
      Color(0xFF1E1712),
      "GOLD"
    )
    "CINESTILL" -> QuadColor(
      Color(0xFF8B1226), // Halation Maroon
      Color(0xFF00E5FF), // Neon Cyan accent
      Color(0xFFFFFFFF),
      "800T"
    )
    "FUJI_VELVIA" -> QuadColor(
      Color(0xFF1B6A34), // Fuji Emerald Green
      Color(0xFFD81B60), // Velvia Magenta
      Color(0xFFFFFFFF),
      "VELVIA"
    )
    "FUJI_SUPERIA" -> QuadColor(
      Color(0xFF107C41), // Fuji Green
      Color(0xFFE6E6E6), // White stripe
      Color(0xFFFFFFFF),
      "SUPERIA"
    )
    "EKTAR" -> QuadColor(
      Color(0xFF1E3A8A), // Royal Blue
      Color(0xFFDC2626), // Red Stripe
      Color(0xFFFFFFFF),
      "EKTAR"
    )
    "DELTA" -> QuadColor(
      Color(0xFF1F2937), // Ilford Delta Slate
      Color(0xFF9CA3AF), // Silver
      Color(0xFFFFFFFF),
      "DELTA"
    )
    else -> QuadColor(
      Color(0xFFB8860B), // Vintage Brass
      Color(0xFF382315), // Walnut Wood
      Color(0xFFECE4D0),
      "FILM"
    )
  }

  // Geometry calculations
  val canW = w * 0.58f
  val canH = h * 0.76f
  val canX = startX + w * 0.12f
  val canY = startY + h * 0.12f

  // 1. Spool Spindle top tip (black plastic core)
  val spindleW = canW * 0.22f
  val spindleH = h * 0.08f
  val spindleX = canX + (canW - spindleW) / 2f
  val spindleY = canY - spindleH * 0.8f

  drawRoundRect(
    color = Color(0xFF22201E),
    topLeft = Offset(spindleX, spindleY),
    size = Size(spindleW, spindleH),
    cornerRadius = CornerRadius(4f, 4f)
  )
  drawRoundRect(
    color = outlineColor,
    topLeft = Offset(spindleX, spindleY),
    size = Size(spindleW, spindleH),
    cornerRadius = CornerRadius(4f, 4f),
    style = Stroke(outlineWidth * 0.8f)
  )

  // 2. Film Leader Tongue (coming out of velvet lip on right)
  val tonguePath = Path().apply {
    val lipX = canX + canW * 0.82f
    val lipY = canY + canH * 0.35f
    val tongueW = w * 0.28f
    val tongueH = canH * 0.32f

    moveTo(lipX, lipY)
    lineTo(lipX + tongueW, lipY + tongueH * 0.15f)
    lineTo(lipX + tongueW * 0.85f, lipY + tongueH)
    lineTo(lipX, lipY + tongueH)
    close()
  }
  // Dark brown / orange amber emulsion of the film strip
  drawPath(
    path = tonguePath,
    color = Color(0xFF3A281A)
  )
  drawPath(
    path = tonguePath,
    color = outlineColor,
    style = Stroke(outlineWidth * 0.9f)
  )

  // Sprocket holes on film tongue
  val sprocketW = w * 0.038f
  val sprocketH = h * 0.05f
  for (i in 0..2) {
    val sx = canX + canW * 0.88f + (i * w * 0.065f)
    val sy = canY + canH * 0.42f
    drawRoundRect(
      color = Color(0xFF140F0C),
      topLeft = Offset(sx, sy),
      size = Size(sprocketW, sprocketH),
      cornerRadius = CornerRadius(2f, 2f)
    )
  }

  // 3. Main Canister Body (cylinder)
  // Base shadow
  drawRoundRect(
    color = Color(0x33000000),
    topLeft = Offset(canX + 3f, canY + 4f),
    size = Size(canW, canH),
    cornerRadius = CornerRadius(canW * 0.16f, canW * 0.16f)
  )

  // Background body gradient
  drawRoundRect(
    brush = Brush.horizontalGradient(
      colors = listOf(
        primaryColor,
        primaryColor.copy(alpha = 0.95f),
        Color.White.copy(alpha = 0.4f),
        primaryColor,
        primaryColor.copy(alpha = 0.75f)
      ),
      startX = canX,
      endX = canX + canW
    ),
    topLeft = Offset(canX, canY),
    size = Size(canW, canH),
    cornerRadius = CornerRadius(canW * 0.15f, canW * 0.15f)
  )

  // Accent band (middle label sticker)
  val bandH = canH * 0.44f
  val bandY = canY + canH * 0.28f
  drawRoundRect(
    color = accentColor,
    topLeft = Offset(canX, bandY),
    size = Size(canW, bandH)
  )

  // Top and bottom metallic crimp caps
  val capH = canH * 0.11f
  val capColor = Brush.horizontalGradient(
    colors = listOf(Color(0xFF888480), Color(0xFFE0DCD6), Color(0xFF706C68)),
    startX = canX,
    endX = canX + canW
  )
  // Top cap
  drawRoundRect(
    brush = capColor,
    topLeft = Offset(canX - w * 0.015f, canY - capH * 0.2f),
    size = Size(canW + w * 0.03f, capH),
    cornerRadius = CornerRadius(6f, 6f)
  )
  drawRoundRect(
    color = outlineColor,
    topLeft = Offset(canX - w * 0.015f, canY - capH * 0.2f),
    size = Size(canW + w * 0.03f, capH),
    cornerRadius = CornerRadius(6f, 6f),
    style = Stroke(outlineWidth * 0.9f)
  )
  // Bottom cap
  drawRoundRect(
    brush = capColor,
    topLeft = Offset(canX - w * 0.015f, canY + canH - capH * 0.8f),
    size = Size(canW + w * 0.03f, capH),
    cornerRadius = CornerRadius(6f, 6f)
  )
  drawRoundRect(
    color = outlineColor,
    topLeft = Offset(canX - w * 0.015f, canY + canH - capH * 0.8f),
    size = Size(canW + w * 0.03f, capH),
    cornerRadius = CornerRadius(6f, 6f),
    style = Stroke(outlineWidth * 0.9f)
  )

  // Canister outline
  drawRoundRect(
    color = outlineColor,
    topLeft = Offset(canX, canY),
    size = Size(canW, canH),
    cornerRadius = CornerRadius(canW * 0.15f, canW * 0.15f),
    style = Stroke(outlineWidth)
  )

  // Text labels on canister (Brand / ISO / Name)
  drawContext.canvas.nativeCanvas.apply {
    val paint = Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // Top Brand Name
    paint.textSize = canW * 0.19f
    paint.color = android.graphics.Color.DKGRAY
    drawText(stock.brand.uppercase(), canX + canW * 0.5f, canY + canH * 0.22f, paint)

    // Center Stock Name on Accent Band
    paint.textSize = canW * 0.23f
    paint.color = if (accentColor == Color(0xFFE8E8E8) || accentColor == Color(0xFFE6E6E6)) {
      android.graphics.Color.BLACK
    } else {
      android.graphics.Color.WHITE
    }
    drawText(textBanner, canX + canW * 0.5f, bandY + bandH * 0.62f, paint)

    // Bottom ISO Badge
    paint.textSize = canW * 0.22f
    paint.color = android.graphics.Color.BLACK
    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    drawText("${stock.iso}", canX + canW * 0.5f, canY + canH * 0.88f, paint)
  }

  // Selection halo indicator
  if (isSelected) {
    drawRoundRect(
      color = Color(0xFFD4AF37),
      topLeft = Offset(canX - 5f, canY - 6f),
      size = Size(canW + 10f, canH + 12f),
      cornerRadius = CornerRadius(canW * 0.2f, canW * 0.2f),
      style = Stroke(width = outlineWidth * 1.3f)
    )
  }
}

private data class QuadColor(
  val primary: Color,
  val accent: Color,
  val text: Color,
  val banner: String
)
