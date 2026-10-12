package com.example.ui.meter

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.CameraFormat
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmStock
import com.example.data.repository.FilmRepository
import com.example.sensor.DepthOfFieldCalculator
import com.example.sensor.DofResult
import com.example.sensor.ExposureCalculator
import com.example.sensor.HapticManager
import com.example.sensor.LightSensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.log2
import kotlin.math.abs

enum class ExposureMode(val label: String, val description: String) {
  APERTURE_PRIORITY("A", "Aperture Priority — turn Aperture, meter sets Shutter"),
  SHUTTER_PRIORITY("S", "Shutter Priority — turn Shutter, meter sets Aperture"),
  MANUAL_MATCH("M", "Manual EV Match — align dial EV with meter needle")
}

enum class MeasurementSource { CAMERA, SENSOR, SUNNY_16 }
enum class ReadingStatus { WAITING, LIVE, HELD, UNAVAILABLE }

class LightMeterViewModel @JvmOverloads constructor(
  application: Application,
  repositoryOverride: FilmRepository? = null
) : AndroidViewModel(application) {

  private val preferences = application.getSharedPreferences("meter_setup", Context.MODE_PRIVATE)
  private val deviceCalibration = application.getSharedPreferences("meter_device_calibration", Context.MODE_PRIVATE)
  private val repository = repositoryOverride ?: AppDatabase.getDatabase(application).let {
    FilmRepository(it.filmStockDao(), it.filmLogDao())
  }
  val sensorManager = LightSensorManager(application)
  val hapticManager = HapticManager(application)
  private var meteringActive = true
  private val rawEv100 = MutableStateFlow<Double?>(null)
  val measuredEv100 = rawEv100.asStateFlow()

  private val _measurementSource = MutableStateFlow(MeasurementSource.CAMERA)
  val measurementSource = _measurementSource.asStateFlow()
  private val _readingStatus = MutableStateFlow(ReadingStatus.WAITING)
  val readingStatus = _readingStatus.asStateFlow()
  private val _hasReading = MutableStateFlow(false)
  val hasReading = _hasReading.asStateFlow()
  private val _readingMessage = MutableStateFlow<String?>(null)
  val readingMessage = _readingMessage.asStateFlow()
  private val _savedEntry = MutableStateFlow<FilmLogEntry?>(null)
  val savedEntry = _savedEntry.asStateFlow()
  private val _isSaving = MutableStateFlow(false)
  val isSaving = _isSaving.asStateFlow()
  private val _isAddingFilm = MutableStateFlow(false)
  val isAddingFilm = _isAddingFilm.asStateFlow()
  private val _filmAddedId = MutableStateFlow<Int?>(null)
  val filmAddedId = _filmAddedId.asStateFlow()
  private val _setupReady = MutableStateFlow(false)
  val setupReady = _setupReady.asStateFlow()

  // Reactive DB streams
  val allStocks: StateFlow<List<FilmStock>> = repository.allFilmStocks
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allLogs: StateFlow<List<FilmLogEntry>> = repository.allLogEntries
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Light meter state
  private val _selectedFormat = MutableStateFlow(
    CameraFormat.entries.firstOrNull { it.name == preferences.getString("format", null) }
      ?: CameraFormat.FORMAT_35MM
  )
  val selectedFormat: StateFlow<CameraFormat> = _selectedFormat.asStateFlow()

  private val _selectedFilmStock = MutableStateFlow<FilmStock?>(null)
  val selectedFilmStock: StateFlow<FilmStock?> = _selectedFilmStock.asStateFlow()

  private val _customIso = MutableStateFlow<Int?>(preferences.getInt("iso", 400).coerceIn(1, 102400))
  val customIso: StateFlow<Int?> = _customIso.asStateFlow()

  val activeIso: Int
    get() = _customIso.value ?: _selectedFilmStock.value?.iso ?: 400

  private val _exposureMode = MutableStateFlow(
    ExposureMode.entries.firstOrNull { it.name == preferences.getString("mode", null) }
      ?: ExposureMode.APERTURE_PRIORITY
  )
  val exposureMode: StateFlow<ExposureMode> = _exposureMode.asStateFlow()

  // Standard starting aperture f/5.6 (index 7) and shutter 1/125s (index 6)
  private val _selectedApertureIndex = MutableStateFlow(preferences.getInt("aperture", 7)
    .coerceIn(0, ExposureCalculator.APERTURE_VALUES.lastIndex))
  val selectedApertureIndex: StateFlow<Int> = _selectedApertureIndex.asStateFlow()

  private val _selectedShutterIndex = MutableStateFlow(preferences.getInt("shutter", 6)
    .coerceIn(0, ExposureCalculator.SHUTTER_SECONDS.lastIndex))
  val selectedShutterIndex: StateFlow<Int> = _selectedShutterIndex.asStateFlow()

  private val _calibrationOffsetEv = MutableStateFlow(deviceCalibration.getFloat("calibration", 0f)
    .takeIf { it.isFinite() }?.coerceIn(-3f, 3f) ?: 0f)
  val calibrationOffsetEv: StateFlow<Float> = _calibrationOffsetEv.asStateFlow()

  fun onOpticalEvMeasured(
    measuredEv: Double,
    format: CameraFormat = _selectedFormat.value,
    focalLengthMm: Int = _focalLengthMm.value
  ) {
    if (_measurementSource.value == MeasurementSource.CAMERA &&
      format == _selectedFormat.value && focalLengthMm == _focalLengthMm.value) acceptReading(measuredEv)
  }

  // Active roll metadata
  private val _currentRollId = MutableStateFlow(preferences.getString("roll_id", null) ?: UUID.randomUUID().toString())
  val currentRollId: StateFlow<String> = _currentRollId.asStateFlow()

  private val _currentRollName = MutableStateFlow(preferences.getString("roll_name", null) ?: "Roll #1")
  val currentRollName: StateFlow<String> = _currentRollName.asStateFlow()

  private val _activeRollFrameCount = MutableStateFlow(0)
  val activeRollFrameCount: StateFlow<Int> = _activeRollFrameCount.asStateFlow()

  // Dialog / Sheets
  private val _showTimerDialog = MutableStateFlow(false)
  val showTimerDialog: StateFlow<Boolean> = _showTimerDialog.asStateFlow()

  private val _showStockPickerSheet = MutableStateFlow(false)
  val showStockPickerSheet: StateFlow<Boolean> = _showStockPickerSheet.asStateFlow()

  // Depth of field state
  private val _focalLengthMm = MutableStateFlow(preferences.getInt("focal_length",
    DepthOfFieldCalculator.getDefaultFocalLength(_selectedFormat.value)).coerceIn(10, 1000))
  val focalLengthMm: StateFlow<Int> = _focalLengthMm.asStateFlow()

  private val _focusDistanceMeters = MutableStateFlow(2.5)
  val focusDistanceMeters: StateFlow<Double> = _focusDistanceMeters.asStateFlow()

  private val _isDofExpanded = MutableStateFlow(false)
  val isDofExpanded: StateFlow<Boolean> = _isDofExpanded.asStateFlow()

  fun setFocalLength(mm: Int) {
    if (sensorManager.isLocked.value || _isSaving.value || mm !in 10..1000 || mm == _focalLengthMm.value) return
    _focalLengthMm.value = mm
    if (_measurementSource.value == MeasurementSource.CAMERA) invalidateReading()
    persistSetup()
  }

  fun setFocusDistance(meters: Double) {
    if (!meters.isFinite()) return
    _focusDistanceMeters.value = meters.coerceAtLeast(0.3)
  }

  fun toggleDofExpanded() {
    _isDofExpanded.value = !_isDofExpanded.value
  }

  fun setFocusToHyperfocal() {
    val currentAp = ExposureCalculator.APERTURE_VALUES[_selectedApertureIndex.value]
    val dof = DepthOfFieldCalculator.calculate(
      _focalLengthMm.value.toDouble(),
      currentAp,
      _focusDistanceMeters.value,
      _selectedFormat.value
    )
    _focusDistanceMeters.value = dof.hyperfocalMeters
    _userMessage.value = "Focus set to Hyperfocal: ${DepthOfFieldCalculator.formatDistance(dof.hyperfocalMeters)}"
  }

  fun getDofResult(): DofResult {
    val currentAp = ExposureCalculator.APERTURE_VALUES[_selectedApertureIndex.value]
    return DepthOfFieldCalculator.calculate(
      _focalLengthMm.value.toDouble(),
      currentAp,
      _focusDistanceMeters.value,
      _selectedFormat.value
    )
  }

  // Quick feedback toast or message
  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  init {
    hapticManager.isHapticsEnabled = preferences.getBoolean("haptics", true)
    // The old backed-up offset cannot be identified as belonging to this phone.
    if (preferences.contains("calibration")) {
      if (preferences.getFloat("calibration", 0f) != 0f) {
        _userMessage.value = "Calibration reset for this phone. Compare with a reference meter to recalibrate."
      }
      preferences.edit().remove("calibration").apply()
    }
    viewModelScope.launch {
      try {
        _selectedFilmStock.value = preferences.getInt("stock_id", -1)
          .takeIf { it >= 0 }?.let { repository.getFilmStock(it) }
        _activeRollFrameCount.value = maxOf(preferences.getInt("roll_frame_count", 0),
          repository.getLatestEntryForRoll(_currentRollId.value)?.frameNumber ?: 0)
        _setupReady.value = true
        persistSetup()
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not load your roll. Reopen the app before saving a frame."
      }
    }
    viewModelScope.launch {
      sensorManager.readings.collect { lux ->
        if (lux != null) onSensorLuxMeasured(lux)
      }
    }
  }

  private fun persistSetup() {
    if (!_setupReady.value) return
    preferences.edit()
      .putInt("stock_id", _selectedFilmStock.value?.id ?: -1)
      .putInt("iso", activeIso)
      .putString("format", _selectedFormat.value.name)
      .putInt("focal_length", _focalLengthMm.value)
      .putString("mode", _exposureMode.value.name)
      .putInt("aperture", _selectedApertureIndex.value)
      .putInt("shutter", _selectedShutterIndex.value)
      .putString("roll_id", _currentRollId.value)
      .putString("roll_name", _currentRollName.value)
      .putInt("roll_frame_count", _activeRollFrameCount.value)
      .putBoolean("haptics", hapticManager.isHapticsEnabled)
      .apply()
  }

  private fun invalidateReading() {
    rawEv100.value = null
    _hasReading.value = false
    _readingStatus.value = ReadingStatus.WAITING
    _readingMessage.value = null
    _savedEntry.value = null
  }

  private fun acceptReading(ev100: Double, measuredLux: Float? = null) {
    if ((!meteringActive && _measurementSource.value != MeasurementSource.SUNNY_16) ||
      sensorManager.isLocked.value || !ev100.isFinite()) return
    rawEv100.value = ev100
    _hasReading.value = true
    sensorManager.setManualLux(measuredLux ?: (2.5 * Math.pow(2.0, ev100)).toFloat())
    recalculateForMode()
    _readingMessage.value = null
    _readingStatus.value = ReadingStatus.LIVE
  }

  internal fun onSensorLuxMeasured(lux: Float) {
    if (_measurementSource.value != MeasurementSource.SENSOR || !lux.isFinite() || lux < 0f) return
    if (lux == 0f) {
      if (!sensorManager.isLocked.value && meteringActive) {
        invalidateReading()
        _readingStatus.value = ReadingStatus.UNAVAILABLE
        _readingMessage.value = "Light is below this sensor's measuring range."
      }
      return
    }
    acceptReading(ExposureCalculator.luxToEv100(lux), lux)
  }

  fun setMeasurementSource(source: MeasurementSource) {
    if (_isSaving.value || source == _measurementSource.value) return
    sensorManager.stop()
    sensorManager.setLocked(false)
    sensorManager.resetReadings()
    _measurementSource.value = source
    invalidateReading()
    if (meteringActive && source == MeasurementSource.SENSOR) startSensor()
  }

  private fun startSensor() {
    if (!sensorManager.start()) {
      _readingStatus.value = ReadingStatus.UNAVAILABLE
      _readingMessage.value = "Light sensor unavailable. Use the camera or Sunny 16."
    }
  }

  fun setCameraStatus(message: String?, measuring: Boolean = false) {
    if (_measurementSource.value != MeasurementSource.CAMERA || sensorManager.isLocked.value || !meteringActive) return
    if (message != null) {
      if (!measuring) invalidateReading()
      _readingStatus.value = if (measuring) ReadingStatus.WAITING else ReadingStatus.UNAVAILABLE
      _readingMessage.value = message
    } else if (_readingStatus.value != ReadingStatus.LIVE) {
      _readingStatus.value = ReadingStatus.WAITING
      _readingMessage.value = null
    }
  }

  fun pauseMetering() {
    meteringActive = false
    sensorManager.stop()
    if (!sensorManager.isLocked.value && _measurementSource.value != MeasurementSource.SUNNY_16) invalidateReading()
  }

  fun resumeMetering() {
    meteringActive = true
    if (!sensorManager.isLocked.value && _measurementSource.value == MeasurementSource.SENSOR) {
      sensorManager.resetReadings()
      invalidateReading()
      startSensor()
    }
  }

  // Computed properties
  fun computeEv100(): Double {
    val offset = if (_measurementSource.value == MeasurementSource.SUNNY_16) 0f else _calibrationOffsetEv.value
    return (rawEv100.value ?: Double.NaN) + offset
  }

  fun computeEvForActiveIso(): Double {
    val ev100 = computeEv100()
    if (!ev100.isFinite()) return Double.NaN
    return ExposureCalculator.ev100ToEvForIso(ev100, activeIso)
  }

  fun getRawShutterSeconds(): Double? {
    val selected = ExposureCalculator.SHUTTER_SECONDS[_selectedShutterIndex.value]
    val stock = _selectedFilmStock.value
    return if (_exposureMode.value == ExposureMode.APERTURE_PRIORITY || stock == null) selected
      else stock.calculateMeteredExposure(selected)
  }

  fun getCorrectedShutterSeconds(): Double? {
    val raw = getRawShutterSeconds() ?: return null
    val stock = _selectedFilmStock.value
    return if (_exposureMode.value != ExposureMode.APERTURE_PRIORITY) ExposureCalculator.SHUTTER_SECONDS[_selectedShutterIndex.value]
      else if (stock == null) raw else stock.calculateCorrectedExposure(raw)
  }

  fun getEvDelta(): Double? {
    val seconds = getRawShutterSeconds() ?: return null
    val ev = computeEvForActiveIso()
    return if (ev.isFinite()) ExposureCalculator.calculateEvDelta(_selectedApertureIndex.value, seconds, ev) else null
  }

  fun getExposureGuidance(): String? = _selectedFilmStock.value?.reciprocityGuidance(
    getRawShutterSeconds() ?: ExposureCalculator.SHUTTER_SECONDS[_selectedShutterIndex.value])

  fun isLongExposure(): Boolean {
    return (getCorrectedShutterSeconds() ?: 0.0) >= 1.0
  }

  fun setFormat(format: CameraFormat) {
    if (sensorManager.isLocked.value || _isSaving.value || format == _selectedFormat.value) return
    _selectedFormat.value = format
    _focalLengthMm.value = DepthOfFieldCalculator.getDefaultFocalLength(format)
    if (_measurementSource.value == MeasurementSource.CAMERA) invalidateReading()
    persistSetup()
  }

  fun setApertureIndex(index: Int) {
    if (_isSaving.value) return
    _selectedApertureIndex.value = index.coerceIn(0, ExposureCalculator.APERTURE_VALUES.lastIndex)
    if (_exposureMode.value == ExposureMode.APERTURE_PRIORITY) {
      recalculateForMode()
    }
    persistSetup()
  }

  fun setShutterIndex(index: Int) {
    if (_isSaving.value) return
    _selectedShutterIndex.value = index.coerceIn(0, ExposureCalculator.SHUTTER_SECONDS.lastIndex)
    if (_exposureMode.value == ExposureMode.SHUTTER_PRIORITY) {
      recalculateForMode()
    }
    persistSetup()
  }

  fun setFilmStock(stock: FilmStock) {
    if (_isSaving.value || stock.iso <= 0) return
    _selectedFilmStock.value = stock
    _customIso.value = stock.iso
    recalculateForMode()
    persistSetup()
  }

  fun setCustomIso(iso: Int) {
    if (_isSaving.value || iso !in 1..102400) return
    _customIso.value = iso
    recalculateForMode()
    persistSetup()
  }

  fun setExposureMode(mode: ExposureMode) {
    if (_isSaving.value || mode == _exposureMode.value) return
    val wasAperture = _exposureMode.value == ExposureMode.APERTURE_PRIORITY
    if (wasAperture != (mode == ExposureMode.APERTURE_PRIORITY)) {
      val seconds = if (wasAperture) getCorrectedShutterSeconds() else getRawShutterSeconds()
      if (seconds != null) {
        _selectedShutterIndex.value = ExposureCalculator.SHUTTER_SECONDS.indices.minBy {
          abs(log2(ExposureCalculator.SHUTTER_SECONDS[it]) - log2(seconds))
        }
        if (wasAperture && seconds > ExposureCalculator.SHUTTER_SECONDS.last()) {
          _userMessage.value = "This exposure exceeds the selectable shutter range. Use aperture priority and the timer."
        }
      }
    }
    _exposureMode.value = mode
    recalculateForMode()
    persistSetup()
  }

  fun recalculateForMode() {
    if (rawEv100.value == null) return
    val evForIso = computeEvForActiveIso()
    when (_exposureMode.value) {
      ExposureMode.APERTURE_PRIORITY -> {
        val newShutterIdx = ExposureCalculator.calculateShutterIndexForAperture(
          _selectedApertureIndex.value,
          evForIso
        )
        _selectedShutterIndex.value = newShutterIdx
      }
      ExposureMode.SHUTTER_PRIORITY -> {
        val seconds = getRawShutterSeconds() ?: return
        val newApertureIdx = ExposureCalculator.calculateApertureIndexForShutterSeconds(
          seconds,
          evForIso
        )
        _selectedApertureIndex.value = newApertureIdx
      }
      ExposureMode.MANUAL_MATCH -> {
        // Manual match leaves dials as user set them
      }
    }
  }

  fun setPresetEv100(presetEv: Double) {
    if (_isSaving.value || !presetEv.isFinite() || presetEv !in -4.0..20.0) return
    setMeasurementSource(MeasurementSource.SUNNY_16)
    sensorManager.setLocked(false)
    _savedEntry.value = null
    acceptReading(presetEv)
  }

  fun setApertureDirectly(apertureLabel: String) {
    if (_isSaving.value) return
    val idx = ExposureCalculator.APERTURE_LABELS.indexOf(apertureLabel)
    if (idx != -1) {
      _selectedApertureIndex.value = idx
      persistSetup()
    }
  }

  fun setShutterDirectly(shutterLabel: String) {
    if (_isSaving.value) return
    val clean = shutterLabel.trim().removeSuffix("s")
    val idx = ExposureCalculator.SHUTTER_LABELS.indexOfFirst {
      it.equals(shutterLabel, ignoreCase = true) || it.equals(clean, ignoreCase = true) || it.equals("${clean}s", ignoreCase = true)
    }
    if (idx != -1) {
      _selectedShutterIndex.value = idx
      persistSetup()
    }
  }

  fun applySunny16Preset(apertureLabel: String, shutterLabel: String, iso: Int) {
    if (_isSaving.value || iso !in 1..102400) return
    setMeasurementSource(MeasurementSource.SUNNY_16)
    sensorManager.setLocked(false)
    setIso(iso)
    setApertureDirectly(apertureLabel)
    setShutterDirectly(shutterLabel)
    val aperture = ExposureCalculator.APERTURE_VALUES[_selectedApertureIndex.value]
    val seconds = ExposureCalculator.SHUTTER_SECONDS[_selectedShutterIndex.value]
    setPresetEv100(log2(aperture * aperture / seconds) - log2(iso / 100.0))
    persistSetup()
    _userMessage.value = "Sunny 16 applied: $apertureLabel at $shutterLabel (ISO $iso)"
  }

  fun setIso(iso: Int) {
    setCustomIso(iso)
  }

  fun toggleHold() {
    if (_isSaving.value) return
    if (_readingStatus.value == ReadingStatus.HELD) {
      sensorManager.setLocked(false)
      _savedEntry.value = null
      if (_measurementSource.value == MeasurementSource.SUNNY_16) {
        _readingStatus.value = ReadingStatus.LIVE
      } else {
        invalidateReading()
        if (meteringActive && _measurementSource.value == MeasurementSource.SENSOR) {
          sensorManager.resetReadings()
          startSensor()
        }
      }
    } else if (_readingStatus.value == ReadingStatus.LIVE && rawEv100.value != null) {
      sensorManager.setLocked(true)
      _readingStatus.value = ReadingStatus.HELD
    }
  }

  fun setShowTimerDialog(show: Boolean) {
    _showTimerDialog.value = show
  }

  fun setShowStockPickerSheet(show: Boolean) {
    _showStockPickerSheet.value = show
  }

  fun clearUserMessage() {
    _userMessage.value = null
  }

  fun logExposure(notes: String = "") {
    if (!_setupReady.value || _isSaving.value || _savedEntry.value != null ||
      _readingStatus.value != ReadingStatus.HELD || rawEv100.value == null) return
    val nextFrame = _activeRollFrameCount.value + 1
    val format = _selectedFormat.value
    val apertureStr = ExposureCalculator.APERTURE_LABELS[_selectedApertureIndex.value]
    val shutterStr = ExposureCalculator.SHUTTER_LABELS[_selectedShutterIndex.value]
    val stock = _selectedFilmStock.value
    val stockName = stock?.name ?: "Custom (ISO $activeIso)"
    val rawSec = getRawShutterSeconds() ?: return
    val corrSec = getCorrectedShutterSeconds() ?: return
    val evVal = computeEvForActiveIso()

    val entry = FilmLogEntry(
      rollId = _currentRollId.value,
      rollName = _currentRollName.value,
      frameNumber = nextFrame,
      filmStockName = stockName,
      iso = activeIso,
      format = format.displayName,
      apertureText = apertureStr,
      shutterText = shutterStr,
      rawSeconds = rawSec,
      correctedSeconds = corrSec,
      evValue = evVal,
      luxValue = sensorManager.lux.value,
      notes = notes
    )

    _isSaving.value = true
    viewModelScope.launch {
      try {
        val id = repository.insertLogEntry(entry).toInt()
        _activeRollFrameCount.value = nextFrame
        _savedEntry.value = entry.copy(id = id)
        persistSetup()
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Frame was not saved. Try again."
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun undoLastSavedExposure() {
    val entry = _savedEntry.value ?: return
    if (_isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        repository.deleteLogEntry(entry.id)
        if (entry.frameNumber == _activeRollFrameCount.value) _activeRollFrameCount.value -= 1
        _savedEntry.value = null
        persistSetup()
        _userMessage.value = "Frame ${entry.frameNumber} removed."
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not undo the saved frame. Try again."
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun startNewRoll(rollName: String) {
    if (!_setupReady.value || _isSaving.value) return
    _currentRollId.value = UUID.randomUUID().toString()
    _currentRollName.value = rollName.trim().ifEmpty { "New roll" }
    _activeRollFrameCount.value = 0
    _savedEntry.value = null
    persistSetup()
    _userMessage.value = "Started new roll: $rollName"
  }

  fun deleteLogEntry(id: Int) {
    if (_isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        repository.deleteLogEntry(id)
        if (_savedEntry.value?.id == id) _savedEntry.value = null
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not delete the frame. Try again."
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun deleteRoll(rollId: String) {
    if (_isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        repository.deleteRoll(rollId)
        _isSaving.value = false
        if (_currentRollId.value == rollId) startNewRoll("New Roll")
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not delete the roll. Try again."
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun addCustomFilmStock(stock: FilmStock) {
    if (_isAddingFilm.value) return
    _isAddingFilm.value = true
    _filmAddedId.value = null
    viewModelScope.launch {
      try {
        _filmAddedId.value = repository.insertStock(stock).toInt()
        _userMessage.value = "Added stock: ${stock.name}"
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not add the film. Try again."
      } finally {
        _isAddingFilm.value = false
      }
    }
  }

  fun clearFilmAddedReceipt() { _filmAddedId.value = null }

  fun deleteFilmStock(id: Int) {
    viewModelScope.launch {
      try {
        repository.deleteStock(id)
        if (_selectedFilmStock.value?.id == id) {
          _selectedFilmStock.value = null
          recalculateForMode()
          persistSetup()
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not delete the film. Try again."
      }
    }
  }

  fun setCalibrationOffset(offset: Float) {
    if (_isSaving.value || !offset.isFinite()) return
    _calibrationOffsetEv.value = offset.coerceIn(-3f, 3f)
    deviceCalibration.edit().putFloat("calibration", _calibrationOffsetEv.value).apply()
    recalculateForMode()
    persistSetup()
  }

  fun setHapticsEnabled(enabled: Boolean) {
    hapticManager.isHapticsEnabled = enabled
    persistSetup()
  }

  override fun onCleared() {
    super.onCleared()
    sensorManager.stop()
  }
}
