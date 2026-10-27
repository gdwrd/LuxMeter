package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

internal fun monochromePreviewPaint() = Paint().apply {
  colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
}

internal fun renderHeldCameraFrame(source: Bitmap, isBlackAndWhite: Boolean): Bitmap {
  if (!isBlackAndWhite) return source
  return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
    it.density = source.density
    Canvas(it).drawBitmap(source, 0f, 0f, monochromePreviewPaint())
  }
}
