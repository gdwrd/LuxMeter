package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.ShotPhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShotPhotoStoreTest {
  private lateinit var context: Context
  private lateinit var store: ShotPhotoStore
  private lateinit var directory: File
  private val originals = mutableListOf<File>()

  @Before fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    directory = File(context.filesDir, "shot-photos")
    directory.deleteRecursively()
    store = ShotPhotoStore(context)
  }

  @After fun tearDown() {
    directory.deleteRecursively()
    originals.forEach { it.delete() }
  }

  @Test fun savesBoundedJpegWithoutRecyclingTheCallersBitmap() = runBlocking {
    val source = Bitmap.createBitmap(1800, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    try {
      val name = store.save(source)
      val saved = store.file(name)!!
      val decoded = BitmapFactory.decodeFile(saved.absolutePath)
      try {
        assertEquals(512, decoded.width)
        assertEquals(341, decoded.height)
        assertTrue(Color.red(decoded.getPixel(256, 170)) > 230)
        assertFalse(source.isRecycled)
        assertEquals(1800, source.width)
        assertEquals(listOf(name), directory.listFiles()!!.map { it.name })
      } finally { decoded.recycle() }
    } finally { source.recycle() }
  }

  @Test fun smallPhotosAreNotEnlargedAndDeletingTwiceIsSafe() = runBlocking {
    val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
    val name = try { store.save(source) } finally { source.recycle() }
    val decoded = BitmapFactory.decodeFile(store.file(name)!!.absolutePath)
    assertEquals(80, decoded.width)
    assertEquals(40, decoded.height)
    decoded.recycle()
    store.delete(name)
    store.delete(name)
    assertNull(store.file(name))
  }

  @Test fun importingNormalizesEveryExifRotationAndMirrorAndStripsMetadata() = runBlocking {
    val expectedCorners = listOf(
      listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
      listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
      listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
      listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
      listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
      listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
      listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
      listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE)
    )
    for (orientation in 1..8) {
      val source = quadrantPhoto()
      ExifInterface(source.absolutePath).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        setAttribute(ExifInterface.TAG_GPS_LATITUDE, "51/1,30/1,0/1")
        setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
        saveAttributes()
      }
      val saved = store.file(store.importPhoto(Uri.fromFile(source)))!!
      val decoded = BitmapFactory.decodeFile(saved.absolutePath)
      try {
        assertEquals(if (orientation >= 5) 120 else 240, decoded.width)
        assertEquals(if (orientation >= 5) 240 else 120, decoded.height)
        val actualCorners = listOf(
          decoded.getPixel(decoded.width / 4, decoded.height / 4),
          decoded.getPixel(decoded.width * 3 / 4, decoded.height / 4),
          decoded.getPixel(decoded.width / 4, decoded.height * 3 / 4),
          decoded.getPixel(decoded.width * 3 / 4, decoded.height * 3 / 4)
        )
        expectedCorners[orientation - 1].zip(actualCorners).forEach { (expected, actual) ->
          assertColorNear("Orientation $orientation", expected, actual)
        }
        assertNull(ExifInterface(saved.absolutePath).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertTrue(source.exists())
      } finally { decoded.recycle() }
    }
    assertTrue(directory.listFiles()!!.all { it.extension == "jpg" })
  }

  @Test fun largeImportsAreSampledAndTheirOriginalRemainsUntouched() = runBlocking {
    val original = temporaryOriginal(".png")
    val bitmap = Bitmap.createBitmap(4096, 2048, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
    try { original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    finally { bitmap.recycle() }
    val originalBytes = original.readBytes()
    val saved = store.file(store.importPhoto(Uri.fromFile(original)))!!
    val decoded = BitmapFactory.decodeFile(saved.absolutePath)
    assertEquals(512, decoded.width)
    assertEquals(256, decoded.height)
    decoded.recycle()
    assertArrayEquals(originalBytes, original.readBytes())
    assertTrue(directory.listFiles()!!.all { it.extension == "jpg" })
  }

  @Test fun invalidImagesAndFailedWritesLeaveNoPartialPhoto() {
    val invalid = temporaryOriginal(".jpg").apply { writeText("Not a photograph") }
    assertThrows(IOException::class.java) { runBlocking { store.importPhoto(Uri.fromFile(invalid)) } }
    assertTrue(directory.listFiles().orEmpty().isEmpty())
    directory.delete()
    directory.writeText("Storage is obstructed")
    val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    try {
      assertThrows(IOException::class.java) { runBlocking { store.save(bitmap) } }
      assertFalse(bitmap.isRecycled)
      assertEquals("Storage is obstructed", directory.readText())
    } finally { bitmap.recycle() }
  }

  @Test fun filenamesCannotReachOutsideThePhotoDirectory() = runBlocking {
    val outside = temporaryOriginal(".jpg").apply { writeText("Keep this file") }
    for (name in listOf(outside.absolutePath, "../${outside.name}", "..", "invalid.jpg", "${UUID.randomUUID()}.tmp")) {
      assertNull(store.file(name))
      store.delete(name)
    }
    assertNull(store.file(null))
    assertEquals("Keep this file", outside.readText())
  }

  @Test fun orphanSweepKeepsReferencesRecentWritesAndUnrelatedFiles() = runBlocking {
    directory.mkdirs()
    val oldTime = System.currentTimeMillis() - 25 * 60 * 60 * 1000L
    fun photo(extension: String, old: Boolean) = File(directory, "${UUID.randomUUID()}.$extension").apply {
      writeText("Photo")
      if (old) assertTrue(setLastModified(oldTime))
    }
    val referenced = photo("jpg", old = true)
    val recent = photo("jpg", old = false)
    val orphan = photo("jpg", old = true)
    val abandonedCopy = photo("source", old = true)
    val abandonedWrite = photo("tmp", old = true)
    val unrelated = File(directory, "notes.txt").apply { writeText("Keep"); setLastModified(oldTime) }
    store.cleanOrphans(setOf(referenced.name))
    assertTrue(referenced.exists())
    assertTrue(recent.exists())
    assertTrue(unrelated.exists())
    assertFalse(orphan.exists())
    assertFalse(abandonedCopy.exists())
    assertFalse(abandonedWrite.exists())
  }

  private fun temporaryOriginal(suffix: String): File = File.createTempFile("original-", suffix, context.cacheDir).also { originals += it }

  private fun quadrantPhoto(): File {
    val bitmap = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { index, color ->
      paint.color = color
      val left = (index % 2) * 120f
      val top = (index / 2) * 60f
      canvas.drawRect(left, top, left + 120f, top + 60f, paint)
    }
    return temporaryOriginal(".jpg").also { original ->
      try { original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) } }
      finally { bitmap.recycle() }
    }
  }

  private fun assertColorNear(message: String, expected: Int, actual: Int) {
    assertEquals(message, Color.red(expected).toDouble(), Color.red(actual).toDouble(), 12.0)
    assertEquals(message, Color.green(expected).toDouble(), Color.green(actual).toDouble(), 12.0)
    assertEquals(message, Color.blue(expected).toDouble(), Color.blue(actual).toDouble(), 12.0)
  }
}
