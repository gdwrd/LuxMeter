package com.example.ui.meter

import android.app.Application
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
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

enum class ExposureMode(val label: String, val description: String) {
  APERTURE_PRIORITY("A", "Aperture Priority — turn Aperture, meter sets Shutter"),
  SHUTTER_PRIORITY("S", "Shutter Priority — turn Shutter, meter sets Aperture"),
  MANUAL_MATCH("M", "Manual EV Match — align dial EV with meter needle")
}

class LightMeterViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: FilmRepository
  val sensorManager = LightSensorManager(application)
  val hapticManager = HapticManager(application)

  init {
    val db = AppDatabase.getDatabase(application, viewModelScope)
    repository = FilmRepository(db.filmStockDao(), db.filmLogDao())
    sensorManager.start()
  }

  // Reactive DB streams
  val allStocks: StateFlow<List<FilmStock>> = repository.allFilmStocks
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allLogs: StateFlow<List<FilmLogEntry>> = repository.allLogEntries
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Light meter state
  private val _selectedFormat = MutableStateFlow(CameraFormat.FORMAT_35MM)
  val selectedFormat: StateFlow<CameraFormat> = _selectedFormat.asStateFlow()

  private val _selectedFilmStock = MutableStateFlow<FilmStock?>(null)
  val selectedFilmStock: StateFlow<FilmStock?> = _selectedFilmStock.asStateFlow()

  private val _customIso = MutableStateFlow<Int?>(null)
  val customIso: StateFlow<Int?> = _customIso.asStateFlow()

  val activeIso: Int
    get() = _customIso.value ?: _selectedFilmStock.value?.iso ?: 400

  private val _exposureMode = MutableStateFlow(ExposureMode.APERTURE_PRIORITY)
  val exposureMode: StateFlow<ExposureMode> = _exposureMode.asStateFlow()

  // Standard starting aperture f/5.6 (index 7) and shutter 1/125s (index 6)
  private val _selectedApertureIndex = MutableStateFlow(7) // f/5.6
  val selectedApertureIndex: StateFlow<Int> = _selectedApertureIndex.asStateFlow()

  private val _selectedShutterIndex = MutableStateFlow(6) // 1/125s
  val selectedShutterIndex: StateFlow<Int> = _selectedShutterIndex.asStateFlow()

  private val _calibrationOffsetEv = MutableStateFlow(0.0f)
  val calibrationOffsetEv: StateFlow<Float> = _calibrationOffsetEv.asStateFlow()

  private val _isViewfinderMode = MutableStateFlow(true)
  val isViewfinderMode: StateFlow<Boolean> = _isViewfinderMode.asStateFlow()

  fun setViewfinderMode(enabled: Boolean) {
    _isViewfinderMode.value = enabled
  }

  fun onOpticalEvMeasured(measuredEv: Double) {
    val targetLux = (2.5 * Math.pow(2.0, measuredEv - _calibrationOffsetEv.value)).toFloat()
    sensorManager.setManualLux(targetLux)
    recalculateForMode()
  }

  // Active roll metadata
  private val _currentRollId = MutableStateFlow(UUID.randomUUID().toString())
  val currentRollId: StateFlow<String> = _currentRollId.asStateFlow()

  private val _currentRollName = MutableStateFlow("Roll #1")
  val currentRollName: StateFlow<String> = _currentRollName.asStateFlow()

  private val _activeRollFrameCount = MutableStateFlow(0)
  val activeRollFrameCount: StateFlow<Int> = _activeRollFrameCount.asStateFlow()

  // Dialog / Sheets
  private val _showTimerDialog = MutableStateFlow(false)
  val showTimerDialog: StateFlow<Boolean> = _showTimerDialog.asStateFlow()

  private val _showStockPickerSheet = MutableStateFlow(false)
  val showStockPickerSheet: StateFlow<Boolean> = _showStockPickerSheet.asStateFlow()

  // Depth of field state
  private val _focalLengthMm = MutableStateFlow(50)
  val focalLengthMm: StateFlow<Int> = _focalLengthMm.asStateFlow()

  private val _focusDistanceMeters = MutableStateFlow(2.5)
  val focusDistanceMeters: StateFlow<Double> = _focusDistanceMeters.asStateFlow()

  private val _isDofExpanded = MutableStateFlow(false)
  val isDofExpanded: StateFlow<Boolean> = _isDofExpanded.asStateFlow()

  fun setFocalLength(mm: Int) {
    _focalLengthMm.value = mm
  }

  fun setFocusDistance(meters: Double) {
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
    _focusDistanceMeters.value = ((dof.hyperfocalMeters * 10).roundToInt() / 10.0).coerceAtLeast(0.5)
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

  fun get35mmEquivalentFocalLength(): Double {
    val cropFactor = when (_selectedFormat.value) {
      CameraFormat.FORMAT_35MM -> 1.0
      CameraFormat.MEDIUM_645 -> 0.62
      CameraFormat.MEDIUM_66 -> 0.55
      CameraFormat.MEDIUM_67 -> 0.50
    }
    return _focalLengthMm.value * cropFactor
  }

  fun getCameraZoomRatio(): Float {
    val eqFocal = get35mmEquivalentFocalLength()
    val baseFocal = 26.0 // Standard smartphone primary lens in mm
    return (eqFocal / baseFocal).toFloat().coerceIn(1.0f, 10.0f)
  }

  fun getAngleOfViewDegrees(): Int {
    val eqFocal = get35mmEquivalentFocalLength().coerceAtLeast(10.0)
    val aovRad = 2.0 * kotlin.math.atan(43.27 / (2.0 * eqFocal))
    return Math.toDegrees(aovRad).roundToInt()
  }

  // Quick feedback toast or message
  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  // Computed properties
  fun computeEv100(lux: Float): Double {
    return ExposureCalculator.luxToEv100(lux, _calibrationOffsetEv.value)
  }

  fun computeEvForActiveIso(lux: Float): Double {
    val ev100 = computeEv100(lux)
    return ExposureCalculator.ev100ToEvForIso(ev100, activeIso)
  }

  fun getRawShutterSeconds(): Double {
    return ExposureCalculator.SHUTTER_SECONDS[_selectedShutterIndex.value]
  }

  fun getCorrectedShutterSeconds(): Double {
    val raw = getRawShutterSeconds()
    val stock = _selectedFilmStock.value
    return stock?.calculateCorrectedExposure(raw) ?: raw
  }

  fun isLongExposure(): Boolean {
    return getRawShutterSeconds() >= 1.0
  }

  fun setFormat(format: CameraFormat) {
    _selectedFormat.value = format
    _focalLengthMm.value = DepthOfFieldCalculator.getDefaultFocalLength(format)
  }

  fun setApertureIndex(index: Int) {
    _selectedApertureIndex.value = index.coerceIn(0, ExposureCalculator.APERTURE_VALUES.lastIndex)
    if (_exposureMode.value == ExposureMode.APERTURE_PRIORITY) {
      val evForIso = computeEvForActiveIso(sensorManager.lux.value)
      val newShutterIdx = ExposureCalculator.calculateShutterIndexForAperture(index, evForIso)
      _selectedShutterIndex.value = newShutterIdx
    }
  }

  fun setShutterIndex(index: Int) {
    _selectedShutterIndex.value = index.coerceIn(0, ExposureCalculator.SHUTTER_SECONDS.lastIndex)
    if (_exposureMode.value == ExposureMode.SHUTTER_PRIORITY) {
      val evForIso = computeEvForActiveIso(sensorManager.lux.value)
      val newApertureIdx = ExposureCalculator.calculateApertureIndexForShutter(index, evForIso)
      _selectedApertureIndex.value = newApertureIdx
    }
  }

  fun setFilmStock(stock: FilmStock) {
    _selectedFilmStock.value = stock
    _customIso.value = null
    recalculateForMode()
  }

  fun setCustomIso(iso: Int) {
    _customIso.value = iso
    _selectedFilmStock.value = null
    recalculateForMode()
  }

  fun setExposureMode(mode: ExposureMode) {
    _exposureMode.value = mode
    recalculateForMode()
  }

  fun recalculateForMode() {
    val evForIso = computeEvForActiveIso(sensorManager.lux.value)
    when (_exposureMode.value) {
      ExposureMode.APERTURE_PRIORITY -> {
        val newShutterIdx = ExposureCalculator.calculateShutterIndexForAperture(
          _selectedApertureIndex.value,
          evForIso
        )
        _selectedShutterIndex.value = newShutterIdx
      }
      ExposureMode.SHUTTER_PRIORITY -> {
        val newApertureIdx = ExposureCalculator.calculateApertureIndexForShutter(
          _selectedShutterIndex.value,
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
    // Inverse lux: Lux = 2.5 * 2^EV100
    val targetLux = (2.5 * Math.pow(2.0, presetEv)).toFloat()
    sensorManager.setManualLux(targetLux)
    recalculateForMode()
  }

  fun setApertureDirectly(apertureLabel: String) {
    val idx = ExposureCalculator.APERTURE_LABELS.indexOf(apertureLabel)
    if (idx != -1) {
      _selectedApertureIndex.value = idx
    }
  }

  fun setShutterDirectly(shutterLabel: String) {
    val clean = shutterLabel.trim().removeSuffix("s")
    val idx = ExposureCalculator.SHUTTER_LABELS.indexOfFirst {
      it.equals(shutterLabel, ignoreCase = true) || it.equals(clean, ignoreCase = true) || it.equals("${clean}s", ignoreCase = true)
    }
    if (idx != -1) {
      _selectedShutterIndex.value = idx
    }
  }

  fun applySunny16Preset(apertureLabel: String, shutterLabel: String, iso: Int) {
    setIso(iso)
    setApertureDirectly(apertureLabel)
    setShutterDirectly(shutterLabel)
    _userMessage.value = "Sunny 16 applied: $apertureLabel at $shutterLabel (ISO $iso)"
  }

  fun setIso(iso: Int) {
    _customIso.value = iso
    _selectedFilmStock.value = null
  }

  fun toggleHold() {
    sensorManager.toggleLock()
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
    val nextFrame = _activeRollFrameCount.value + 1
    val format = _selectedFormat.value
    val apertureStr = ExposureCalculator.APERTURE_LABELS[_selectedApertureIndex.value]
    val shutterStr = ExposureCalculator.SHUTTER_LABELS[_selectedShutterIndex.value]
    val stock = _selectedFilmStock.value
    val stockName = stock?.name ?: "Custom (ISO $activeIso)"
    val rawSec = getRawShutterSeconds()
    val corrSec = getCorrectedShutterSeconds()
    val evVal = computeEvForActiveIso(sensorManager.lux.value)

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

    viewModelScope.launch {
      repository.insertLogEntry(entry)
      _activeRollFrameCount.value = nextFrame
      _userMessage.value = "Frame $nextFrame / ${format.standardFramesPerRoll} Logged!"
    }
  }

  fun startNewRoll(rollName: String) {
    _currentRollId.value = UUID.randomUUID().toString()
    _currentRollName.value = rollName
    _activeRollFrameCount.value = 0
    _userMessage.value = "Started new roll: $rollName"
  }

  fun deleteLogEntry(id: Int) {
    viewModelScope.launch {
      repository.deleteLogEntry(id)
    }
  }

  fun deleteRoll(rollId: String) {
    viewModelScope.launch {
      repository.deleteRoll(rollId)
      if (_currentRollId.value == rollId) {
        startNewRoll("New Roll")
      }
    }
  }

  fun addCustomFilmStock(stock: FilmStock) {
    viewModelScope.launch {
      repository.insertStock(stock)
      _userMessage.value = "Added stock: ${stock.name}"
    }
  }

  fun deleteFilmStock(id: Int) {
    viewModelScope.launch {
      repository.deleteStock(id)
    }
  }

  fun setCalibrationOffset(offset: Float) {
    _calibrationOffsetEv.value = offset
    recalculateForMode()
  }

  fun setHapticsEnabled(enabled: Boolean) {
    hapticManager.isHapticsEnabled = enabled
  }

  override fun onCleared() {
    super.onCleared()
    sensorManager.stop()
  }
}
