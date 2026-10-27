package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

class ShotPhotoStore(context: Context) {
  private val resolver = context.applicationContext.contentResolver
  private val directory = File(context.applicationContext.filesDir, "shot-photos")

  fun file(name: String?): File? = name?.takeIf { PHOTO_NAME.matches(it) }?.let {
    File(directory, it).takeIf { photo -> photo.isFile && photo.canonicalFile.parentFile == directory.canonicalFile }
  }

  suspend fun save(bitmap: Bitmap): String = withContext(Dispatchers.IO) { writeThumbnail(bitmap) }

  suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
    require(uri.scheme == "content" || uri.scheme == "file") { "Choose an image from your device." }
    ensureDirectory()
    val source = File(directory, "${UUID.randomUUID()}.source")
    try {
      // Copy once so bounds, EXIF and pixels always describe the same provider data.
      resolver.openInputStream(uri)?.use { input ->
        source.outputStream().use { output ->
          val buffer = ByteArray(8192)
          var total = 0L
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_IMPORT_BYTES) throw IOException("Choose an image smaller than 64 MB.")
            output.write(buffer, 0, count)
          }
        }
      } ?: throw IOException("The selected photo could not be opened.")

      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(source.absolutePath, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("The selected file is not a readable image.")
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
      val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
      val decoded = BitmapFactory.decodeFile(source.absolutePath, options)
        ?: throw IOException("The selected photo could not be decoded.")
      try {
        val orientation = try {
          ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = orientationMatrix(orientation)
        val upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        try { writeThumbnail(upright) } finally { if (upright !== decoded) upright.recycle() }
      } finally { decoded.recycle() }
    } finally { source.delete() }
  }

  suspend fun delete(name: String?) = withContext(Dispatchers.IO) {
    file(name)?.let { if (!it.delete() && it.exists()) throw IOException("The old photo could not be removed.") }
    Unit
  }

  suspend fun cleanOrphans(referenced: Set<String>) = withContext(Dispatchers.IO) {
    // shortcut: sweep after a day to protect in-flight saves; use a journal if photo drafts become persistent.
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    directory.listFiles()?.forEach { candidate ->
      if (OWNED_NAME.matches(candidate.name) && candidate.name !in referenced && candidate.isFile &&
        candidate.canonicalFile.parentFile == directory.canonicalFile && candidate.lastModified() < cutoff) {
        candidate.delete()
      }
    }
    Unit
  }

  private fun ensureDirectory() {
    if (!directory.mkdirs() && !directory.isDirectory) throw IOException("Photo storage is unavailable.")
  }

  private fun writeThumbnail(bitmap: Bitmap): String {
    require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "The photo is unavailable." }
    ensureDirectory()
    val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(bitmap.width, bitmap.height))
    val small = if (scale < 1.0) Bitmap.createScaledBitmap(bitmap,
      (bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
      else bitmap
    val id = UUID.randomUUID().toString()
    val temporary = File(directory, "$id.tmp")
    val destination = File(directory, "$id.jpg")
    try {
      FileOutputStream(temporary).use { output ->
        if (!small.compress(Bitmap.CompressFormat.JPEG, 80, output)) throw IOException("The photo could not be saved.")
        output.fd.sync()
      }
      if (!temporary.renameTo(destination)) throw IOException("The photo could not be saved.")
      return destination.name
    } finally {
      temporary.delete()
      if (small !== bitmap) small.recycle()
    }
  }

  private fun orientationMatrix(orientation: Int) = Matrix().apply {
    when (orientation) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
      ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
      ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
    }
  }

  private companion object {
    const val MAX_EDGE = 512
    const val MAX_IMPORT_BYTES = 64L * 1024 * 1024
    const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    val PHOTO_NAME = Regex("$UUID_PATTERN\\.jpg")
    val OWNED_NAME = Regex("$UUID_PATTERN\\.(jpg|tmp|source)")
  }
}
