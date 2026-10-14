package com.example

import com.example.data.RollExport
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmRoll
import com.example.data.model.processingLabel
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

  @Test fun `exports box speed and shooting EI snapshots with current roll processing`() {
    val frame = entry(1).copy(iso = 800, boxIso = 400)
    val roll = FilmRoll(id = frame.rollId, name = frame.rollName, iso = 1600, boxIso = 100, pushStops = 2)
    val csv = RollExport.csv(listOf(frame), roll)
    assertTrue(csv.lineSequence().first().contains("Film,Shooting EI,Camera"))
    assertTrue(csv.lineSequence().first().endsWith("Notes,Box ISO,Processing requested"))
    assertTrue(csv.contains(",\"HP5\",800,"))
    assertTrue(csv.endsWith(",400,\"Push +2 stops\"\r\n"))
    assertTrue(RollExport.csv(listOf(frame), roll.copy(pushStops = 1)).endsWith(",400,\"Push +1 stop\"\r\n"))
    assertTrue(RollExport.csv(listOf(frame), roll.copy(pushStops = 0)).endsWith(",400,\"Normal processing\"\r\n"))
  }

  @Test fun `legacy exports do not infer box speed or processing from shooting EI`() {
    val frame = entry(1).copy(iso = 1600)
    val roll = FilmRoll(id = frame.rollId, name = frame.rollName, filmStockName = "HP5 400", iso = 1600)
    assertTrue(RollExport.csv(listOf(frame), roll).endsWith(",,\"Processing not specified\"\r\n"))
    assertTrue(RollExport.csv(listOf(frame)).endsWith(",,\"Processing not specified\"\r\n"))
    assertEquals("Processing not specified", processingLabel(-1))
    assertEquals("Processing not specified", processingLabel(5))
  }
}
