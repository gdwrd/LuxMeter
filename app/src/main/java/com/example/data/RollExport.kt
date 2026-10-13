package com.example.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.FilmLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object RollExport {
  fun csv(entries: List<FilmLogEntry>): String {
    val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    return buildString {
      append("Frame,Date UTC,Roll,Film,ISO,Camera,Lens mm,Format,Aperture,Actual seconds,Metered seconds,EV at shooting ISO,Source,Calibration EV,Exposure adjustment stops,Filter stops,Correction,Manual,Notes\r\n")
      for (entry in entries.sortedBy { it.frameNumber }) {
        append(listOf(entry.frameNumber.toString(), text(date.format(Date(entry.timestamp))), text(entry.rollName),
          text(entry.filmStockName), entry.iso.toString(), text(entry.cameraName),
          entry.focalLengthMm.takeIf { it > 0 }?.toString().orEmpty(), text(entry.format), text(entry.apertureText),
          entry.correctedSeconds.toString(), entry.rawSeconds.takeIf { it > 0.0 }?.toString().orEmpty(),
          entry.evValue.takeIf { entry.hasMeterReading }?.toString().orEmpty(), text(entry.meteringSource),
          entry.calibrationEv.toString(), entry.exposureCompensation.toString(), entry.filterStops.toString(),
          text(entry.correctionKind), entry.isManual.toString(), text(entry.notes)).joinToString(","))
        append("\r\n")
      }
    }
  }

  private fun text(value: String): String {
    // Quoting alone does not prevent spreadsheet formulas in photographer-entered text.
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || value.startsWith('\t') || value.startsWith('\r'))
      "'$value" else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
  }

  suspend fun share(context: Context, rollName: String, entries: List<FilmLogEntry>) {
    val file = withContext(Dispatchers.IO) {
      val folder = File(context.cacheDir, "roll-exports").apply { check(mkdirs() || isDirectory) }
      val name = rollName.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(48).ifBlank { "Roll" }
      File.createTempFile("$name-".padEnd(3, '_'), ".csv", folder).apply { writeText(csv(entries), Charsets.UTF_8) }
    }
    withContext(Dispatchers.Main) {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Roll CSV", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(Intent.createChooser(intent, "Share roll").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }
}
