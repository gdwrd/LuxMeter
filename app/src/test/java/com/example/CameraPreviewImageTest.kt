package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.ui.components.renderHeldCameraFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraPreviewImageTest {
  @Test
  fun `monochrome snapshot retains framing and leaves original color available`() {
    val source = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888).apply {
      density = 240
      setPixels(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), 0, 3, 0, 0, 3, 1)
    }

    val monochrome = renderHeldCameraFrame(source, true)

    assertNotSame(source, monochrome)
    assertEquals(3, monochrome.width)
    assertEquals(1, monochrome.height)
    assertEquals(source.density, monochrome.density)
    for (x in 0..2) {
      val pixel = monochrome.getPixel(x, 0)
      assertEquals(Color.red(pixel), Color.green(pixel))
      assertEquals(Color.red(pixel), Color.blue(pixel))
      assertEquals(255, Color.alpha(pixel))
    }
    assertEquals(54.0, Color.red(monochrome.getPixel(0, 0)).toDouble(), 1.0)
    assertEquals(182.0, Color.red(monochrome.getPixel(1, 0)).toDouble(), 1.0)
    assertEquals(18.0, Color.red(monochrome.getPixel(2, 0)).toDouble(), 1.0)
    assertEquals(Color.RED, source.getPixel(0, 0))
    assertEquals(Color.GREEN, source.getPixel(1, 0))
    assertEquals(Color.BLUE, source.getPixel(2, 0))
    assertSame(source, renderHeldCameraFrame(source, false))
  }
}
