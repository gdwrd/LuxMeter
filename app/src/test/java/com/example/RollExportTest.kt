package com.example

import com.example.data.RollExport
import com.example.data.model.FilmLogEntry
import org.junit.Assert.*
import org.junit.Test

class RollExportTest {
  private fun entry(frame: Int, notes: String = "") = FilmLogEntry(
    rollId = "roll", rollName = "Summer, \"London\"", frameNumber = frame, filmStockName = "HP5",
    iso = 400, format = "35mm", apertureText = "f/8", shutterText = "1/125",
    rawSeconds = 0.008, correctedSeconds = 0.008, evValue = 13.0, luxValue = 100f,
    notes = notes, timestamp = 0, cameraName = "My camera", focalLengthMm = 50)

  @Test fun `exports ordered frames with escaped notes and unambiguous dates`() {
    val csv = RollExport.csv(listOf(entry(2), entry(1, "Door, \"red\"\nBacklit")))
    assertTrue(csv.startsWith("Frame,Date UTC"))
    assertTrue(csv.indexOf("\r\n1,") < csv.indexOf("\r\n2,"))
    assertTrue(csv.contains("1970-01-01T00:00:00.000Z"))
    assertTrue(csv.contains("\"Summer, \"\"London\"\"\""))
    assertTrue(csv.contains("\"Door, \"\"red\"\"\nBacklit\""))
  }

  @Test fun `user text cannot become a spreadsheet formula and missing readings stay empty`() {
    for (note in listOf("=1+1", " +SUM(A1)", "@command", "-2+1", "\t=1+1")) {
      assertTrue(RollExport.csv(listOf(entry(1, note))).contains("\"'$note\""))
    }
    val csv = RollExport.csv(listOf(entry(1).copy(isManual = true, hasMeterReading = false,
      rawSeconds = 0.0, correctionKind = "Manual")))
    assertTrue(csv.contains("0.008,,,"))
    assertTrue(csv.contains("\"Manual\",true,"))
  }
}
