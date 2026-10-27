package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.FilmStock

@Composable
fun CartoonFilmRoll(
  stock: FilmStock,
  modifier: Modifier = Modifier,
  size: Dp = 80.dp,
  isSelected: Boolean = false
) {
  val selectionScale by animateFloatAsState(
    targetValue = if (isSelected) 1.08f else 1f,
    animationSpec = tween(200),
    label = "film_scale"
  )
  val label = remember(stock.brand, stock.name, stock.iso, stock.type, stock.badgeStyle) { filmLabel(stock) }
  val paint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
  }

  Canvas(modifier = modifier.size(size)) {
    // Leave room for the tilt, spindle, and selected scale at every icon size.
    val unit = this.size.minDimension * 0.0083f * selectionScale
    val iconCenter = center
    withTransform({
      translate(iconCenter.x, iconCenter.y)
      rotate(-8f, pivot = Offset.Zero)
      scale(unit, unit, pivot = Offset.Zero)
      translate(-50f, -50f)
    }) {
      drawFilmCanister(stock, label, paint)
    }
  }
}

private fun DrawScope.drawFilmCanister(stock: FilmStock, label: FilmLabel, paint: Paint) {
  val bodyTopLeft = Offset(12f, 11f)
  val bodySize = Size(59f, 80f)
  val bodyRadius = CornerRadius(9f, 7f)

  drawOval(Color.Black.copy(alpha = 0.06f), Offset(9f, 93f), Size(77f, 7f))
  drawOval(Color.Black.copy(alpha = 0.08f), Offset(15f, 94f), Size(64f, 4f))

  val leader = Path().apply {
    moveTo(62f, 45f)
    lineTo(88f, 40f)
    cubicTo(95f, 38f, 98f, 43f, 96f, 50f)
    lineTo(93f, 68f)
    cubicTo(92f, 74f, 86f, 79f, 79f, 79f)
    lineTo(62f, 80f)
    close()
  }
  drawPath(
    leader,
    Brush.horizontalGradient(
      listOf(Color(0xFF382519), Color(0xFF71513B), Color(0xFF9B7654)),
      startX = 62f,
      endX = 96f
    )
  )
  clipPath(leader) {
    for (index in 0..2) {
      val x = 69f + index * 10f
      for (y in listOf(48f, 71f)) {
        drawRoundRect(
          Color(0xFF201A16),
          Offset(x, y - index * 1.4f),
          Size(2.8f, 4.2f),
          CornerRadius(0.8f, 0.8f)
        )
      }
    }
  }

  drawRoundRect(
    Brush.horizontalGradient(
      listOf(Color(0xFF1A1A1C), Color(0xFF636368), Color(0xFF252527)),
      startX = 34f,
      endX = 49f
    ),
    Offset(34f, 1f), Size(15f, 14f), CornerRadius(3f, 3f)
  )
  drawRoundRect(
    Color.Black.copy(alpha = 0.09f),
    bodyTopLeft + Offset(2f, 3f), bodySize, bodyRadius
  )
  drawRoundRect(
    Brush.horizontalGradient(
      0f to lerp(label.color, Color.Black, 0.27f),
      0.27f to label.color,
      0.47f to lerp(label.color, Color.White, 0.20f),
      0.70f to label.color,
      1f to lerp(label.color, Color.Black, 0.22f),
      startX = 12f,
      endX = 71f
    ),
    bodyTopLeft, bodySize, bodyRadius
  )
  drawRect(label.accent, Offset(12f, 78f), Size(59f, 3.7f))

  drawContext.canvas.nativeCanvas.apply {
    paint.color = label.ink.toArgb()
    for ((text, baseline, textSize) in listOf(
      Triple(stock.brand.uppercase(), 31f, 6.5f),
      Triple(label.family, 43f, 9f),
      Triple(stock.iso.toString(), 65f, 21f)
    )) {
      paint.textSize = textSize
      val textWidth = paint.measureText(text)
      if (textWidth > 48f) paint.textSize *= 48f / textWidth
      drawText(text, 41.5f, baseline, paint)
    }
  }

  val cap = Brush.horizontalGradient(
    listOf(
      Color(0xFF29292C), Color(0xFF77777C), Color(0xFFB3B3B8),
      Color(0xFF626267), Color(0xFF252527)
    ),
    startX = 10f,
    endX = 73f
  )
  for (top in listOf(8f, 85f)) {
    drawRoundRect(
      Color.Black.copy(alpha = 0.16f),
      Offset(10f, top + 1.2f), Size(63f, 10f), CornerRadius(8f, 3.5f)
    )
    drawRoundRect(cap, Offset(10f, top), Size(63f, 10f), CornerRadius(8f, 3.5f))
    drawLine(Color.White.copy(alpha = 0.38f), Offset(16f, top + 0.8f), Offset(67f, top + 0.8f), 0.7f)
    drawLine(Color.Black.copy(alpha = 0.15f), Offset(15f, top + 8.8f), Offset(68f, top + 8.8f), 0.6f)
  }
}

internal data class FilmLabel(val family: String, val color: Color, val ink: Color, val accent: Color)

internal fun filmLabel(stock: FilmStock): FilmLabel {
  val name = stock.name.uppercase()
  val badge = stock.badgeStyle
  return when {
    "PORTRA" in name || badge == "PORTRA" -> when (stock.iso) {
      160 -> FilmLabel("PORTRA", Color(0xFFE1C68B), Color(0xFF30281A), Color(0xFFC6A25C))
      800 -> FilmLabel("PORTRA", Color(0xFFDDB45F), Color(0xFF30281A), Color(0xFF6C473B))
      else -> FilmLabel("PORTRA", Color(0xFFE4BC67), Color(0xFF30281A), Color(0xFFB44730))
    }
    "GOLD" in name || badge == "GOLD" -> FilmLabel("GOLD", Color(0xFFF2C444), Color(0xFF3D2A0B), Color(0xFFBE4438))
    "ULTRAMAX" in name || badge == "ULTRAMAX" -> FilmLabel("ULTRAMAX", Color(0xFF6987AD), Color(0xFFFFF8ED), Color(0xFFE2B751))
    "COLORPLUS" in name || badge == "COLORPLUS" -> FilmLabel("COLORPLUS", Color(0xFFE6BC56), Color(0xFF49321E), Color(0xFFC8673C))
    "EKTACHROME" in name || badge == "EKTACHROME" -> FilmLabel("E100", Color(0xFF516D95), Color.White, Color(0xFFE3BB6A))
    "EKTAR" in name || badge == "EKTAR" -> FilmLabel("EKTAR", Color(0xFFB54843), Color(0xFFFFF3E6), Color(0xFFE4BD77))
    "TRI-X" in name || badge == "TRI_X" -> FilmLabel("TRI-X", Color(0xFFD8B95B), Color(0xFF30281A), Color(0xFF353337))
    "T-MAX" in name || badge == "T_MAX" -> if (stock.iso == 100) {
      FilmLabel("T-MAX", Color(0xFF45464B), Color(0xFFF5F5F7), Color(0xFFB5A57B))
    } else {
      FilmLabel("T-MAX", Color(0xFF37383D), Color(0xFFF5F5F7), Color(0xFFA18AB0))
    }
    "HP5" in name || badge == "HP5" -> FilmLabel("HP5+", Color(0xFF3A3A3D), Color(0xFFF6F5EF), Color(0xFF71A268))
    "FP4" in name || badge == "FP4" -> FilmLabel("FP4+", Color(0xFF515256), Color(0xFFFAF8F3), Color(0xFF6B99B6))
    "DELTA" in name || badge == "DELTA" -> when (stock.iso) {
      100 -> FilmLabel("DELTA", Color(0xFF59616B), Color(0xFFFAF8F3), Color(0xFF9FA9AF))
      400 -> FilmLabel("DELTA", Color(0xFF3D424C), Color(0xFFFAF8F3), Color(0xFFB7BBC6))
      else -> FilmLabel("DELTA", Color(0xFF454B53), Color(0xFFF7F7F7), Color(0xFFBAAA97))
    }
    "PAN F" in name || badge == "PAN_F" -> FilmLabel("PAN F+", Color(0xFF46464C), Color(0xFFFAF8F3), Color(0xFFC9A879))
    "XP2" in name || badge == "XP2" -> FilmLabel("XP2", Color(0xFF54505A), Color(0xFFFAF8F3), Color(0xFFAF87A1))
    stock.brand.equals("Kentmere", ignoreCase = true) || badge == "KENTMERE" -> if (stock.iso == 100) {
      FilmLabel("PAN", Color(0xFFABB3B7), Color(0xFF27313C), Color(0xFF7C994B))
    } else {
      FilmLabel("PAN", Color(0xFFA5B0BB), Color(0xFF27313C), Color(0xFF577FB4))
    }
    stock.brand.equals("CineStill", ignoreCase = true) || badge == "CINESTILL" -> when {
      "50D" in name -> FilmLabel("50D", Color(0xFFC3CDD2), Color(0xFF344047), Color(0xFFC49776))
      "400D" in name -> FilmLabel("400D", Color(0xFFA6BECD), Color(0xFF253C50), Color(0xFFDFBDA0))
      "800T" in name -> FilmLabel("800T", Color(0xFF862E3C), Color(0xFFFFF7F4), Color(0xFF8AC6D2))
      else -> FilmLabel(if ("BWXX" in name) "BwXX" else "FILM", Color(0xFF45464B), Color.White, Color(0xFFC7C7CB))
    }
    "VELVIA" in name || badge == "FUJI_VELVIA" -> FilmLabel("VELVIA", Color(0xFF538369), Color(0xFFF4FFF7), Color(0xFFA95382))
    "SUPERIA" in name || badge == "FUJI_SUPERIA" -> FilmLabel("SUPERIA", Color(0xFF4A8666), Color(0xFFF4FFF7), Color(0xFFDADCDD))
    "ACROS" in name || badge == "ACROS" -> FilmLabel("ACROS II", Color(0xFFBAC8C0), Color(0xFF25382E), Color(0xFF518369))
    "FOMAPAN" in name || badge == "FOMAPAN" -> FilmLabel("FOMAPAN", Color(0xFF657891), Color(0xFFF9F5EB), Color(0xFFAAB6C6))
    stock.isBlackAndWhite -> FilmLabel(badge.take(10), Color(0xFF45464B), Color(0xFFF5F5F7), Color(0xFFB3B3B8))
    else -> FilmLabel("FILM", Color(0xFFB9A783), Color(0xFF30281A), Color(0xFF75664B))
  }
}
