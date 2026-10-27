package com.example.ui.meter

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ShotPhotoStore
import com.example.data.model.CameraFormat
import com.example.data.model.CameraSetup
import com.example.data.model.FilmRoll
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.io.File
import kotlin.math.log2
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ExposureMode(val label: String, val description: String) {
  APERTURE_PRIORITY("A", "Aperture Priority — turn Aperture, meter sets Shutter"),
  SHUTTER_PRIORITY("S", "Shutter Priority — turn Shutter, meter sets Aperture"),
  MANUAL_MATCH("M", "Manual EV Match — align dial EV with meter needle")
}

private const val MIN_DIAL_TOLERANCE_STOPS = 0.6

enum class MeasurementSource { CAMERA, SENSOR, SUNNY_16 }
enum class ReadingStatus { WAITING, LIVE, HELD, UNAVAILABLE }

class LightMeterViewModel @JvmOverloads constructor(
  application: Application,
  repositoryOverride: FilmRepository? = null
) : AndroidViewModel(application) {

  private val preferences = application.getSharedPreferences("meter_setup", Context.MODE_PRIVATE)
  private val deviceCalibration = application.getSharedPreferences("meter_device_calibration", Context.MODE_PRIVATE)
  private val repository = repositoryOverride ?: AppDatabase.getDatabase(application).repository()
  private val photos = ShotPhotoStore(application)
  private val rollWrites = Mutex()
  // Latest unsaved setup per roll; a burst of dial changes shares one queued database write.
  private val pendingRollSetups = mutableMapOf<String, FilmRoll>()
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
  private val _manualSavedEntry = MutableStateFlow<FilmLogEntry?>(null)
  val manualSavedEntry = _manualSavedEntry.asStateFlow()
  private val _createdRollId = MutableStateFlow<String?>(null)
  val createdRollId = _createdRollId.asStateFlow()
  private val _isSaving = MutableStateFlow(false)
  val isSaving = _isSaving.asStateFlow()
  private val _isAddingFilm = MutableStateFlow(false)
  val isAddingFilm = _isAddingFilm.asStateFlow()
  private val _filmAddedId = MutableStateFlow<Int?>(null)
  val filmAddedId = _filmAddedId.asStateFlow()
  private val _setupReady = MutableStateFlow(false)
  val setupReady = _setupReady.asStateFlow()
  private val _currentCameraSetup = MutableStateFlow<CameraSetup?>(null)
  val currentCameraSetup = _currentCameraSetup.asStateFlow()
  private val _currentRoll = MutableStateFlow<FilmRoll?>(null)
  val currentRoll = _currentRoll.asStateFlow()
  private val _isSavingCameraSetup = MutableStateFlow(false)
  val isSavingCameraSetup = _isSavingCameraSetup.asStateFlow()
  private val _cameraSetupSavedId = MutableStateFlow<Int?>(null)
  val cameraSetupSavedId = _cameraSetupSavedId.asStateFlow()
  private val _exposureCompensation = MutableStateFlow(preferences.getFloat("exposure_compensation", 0f)
    .toDouble().takeIf { it.isFinite() && it in -3.0..3.0 } ?: 0.0)
  val exposureCompensation = _exposureCompensation.asStateFlow()
  private val _filterStops = MutableStateFlow(preferences.getFloat("filter_stops", 0f)
    .toDouble().takeIf { it.isFinite() && it in 0.0..16.0 } ?: 0.0)
  val filterStops = _filterStops.asStateFlow()

  // Reactive DB streams
  val allStocks: StateFlow<List<FilmStock>> = repository.allFilmStocks
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allLogs: StateFlow<List<FilmLogEntry>> = repository.allLogEntries
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val allCameraSetups: StateFlow<List<CameraSetup>> = repository.allCameraSetups
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val allRolls: StateFlow<List<FilmRoll>> = repository.allRolls
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val availableApertures: List<Double>
    get() = _currentCameraSetup.value?.let { camera ->
      (ExposureCalculator.APERTURE_VALUES.filter { it in camera.minAperture..camera.maxAperture } +
        listOf(camera.minAperture, camera.maxAperture)).distinct().sorted()
    } ?: ExposureCalculator.APERTURE_VALUES
  val availableShutterSeconds: List<Double>
    get() = _currentCameraSetup.value?.let { ExposureCalculator.parseShutterSpeeds(it.shutterSpeeds) }
      ?: ExposureCalculator.SHUTTER_SECONDS
  val apertureLabels: List<String> get() = availableApertures.map(ExposureCalculator::formatAperture)
  val shutterLabels: List<String> get() = availableShutterSeconds.map {
    val standard = ExposureCalculator.SHUTTER_SECONDS.indexOf(it)
    if (standard >= 0) ExposureCalculator.SHUTTER_LABELS[standard] else ExposureCalculator.formatSeconds(it)
  }

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

  private val _pushStops = MutableStateFlow<Int?>(
    if (preferences.contains("push_stops")) preferences.getInt("push_stops", -1).takeIf { it in 0..4 }
    else if (preferences.contains("iso")) null else 0
  )
  val pushStops: StateFlow<Int?> = _pushStops.asStateFlow()

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

  private val _calibrationOffsetEv = MutableStateFlow(deviceCalibration.getFloat("camera", deviceCalibration.getFloat("calibration", 0f))
    .takeIf { it.isFinite() }?.coerceIn(-3f, 3f) ?: 0f)
  val calibrationOffsetEv: StateFlow<Float> = _calibrationOffsetEv.asStateFlow()
  private val _calibrationOffsets = MutableStateFlow(mapOf(
    MeasurementSource.CAMERA to calibrationOffsetFor(MeasurementSource.CAMERA),
    MeasurementSource.SENSOR to calibrationOffsetFor(MeasurementSource.SENSOR)))
  val calibrationOffsets = _calibrationOffsets.asStateFlow()

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
    val currentAp = availableApertures[_selectedApertureIndex.value]
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
    val currentAp = availableApertures[_selectedApertureIndex.value]
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
        val adoptingLegacyRoll = !preferences.getBoolean("rolls_v2", false)
        val persistedRoll = repository.getRoll(_currentRollId.value)
        val cameraId = persistedRoll?.cameraSetupId ?: preferences.getInt("camera_id", -1).takeIf { it >= 0 }
        _currentCameraSetup.value = cameraId?.let { repository.getCameraSetup(it) }
        restoreExposureValues()
        _selectedFilmStock.value = (if (adoptingLegacyRoll) preferences.getInt("stock_id", -1).takeIf { it >= 0 }
          else persistedRoll?.filmStockId ?: preferences.getInt("stock_id", -1).takeIf { it >= 0 })
          ?.let { repository.getFilmStock(it) }
        if (persistedRoll != null && !adoptingLegacyRoll) {
          _currentRollName.value = persistedRoll.name
          _customIso.value = persistedRoll.iso
          _pushStops.value = persistedRoll.pushStops
          _selectedFormat.value = CameraFormat.entries.firstOrNull { it.name == persistedRoll.format } ?: _selectedFormat.value
          _focalLengthMm.value = persistedRoll.focalLengthMm
        }
        _activeRollFrameCount.value = maxOf(persistedRoll?.lastFrameNumber ?: 0, preferences.getInt("roll_frame_count", 0),
          repository.getLatestEntryForRoll(_currentRollId.value)?.frameNumber ?: 0)
        _currentRoll.value = (if (adoptingLegacyRoll) rollFromSetup().copy(
          frameCapacity = maxOf(_selectedFormat.value.standardFramesPerRoll, _activeRollFrameCount.value),
          createdAt = persistedRoll?.createdAt ?: System.currentTimeMillis())
          else persistedRoll ?: rollFromSetup()).copy(lastFrameNumber = _activeRollFrameCount.value,
          isFinished = if (!adoptingLegacyRoll) persistedRoll?.isFinished ?: false else false)
        // Version 1 kept the active roll only in preferences; adopt it without changing its frames.
        repository.saveRoll(_currentRoll.value!!)
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
    viewModelScope.launch {
      try {
        photos.cleanOrphans(repository.allLogEntries.first().mapNotNull { it.photoFileName }.toSet())
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        // Orphan cleanup retries next launch; it must not prevent logging a shot.
      }
    }
  }

  private fun persistSetup() {
    if (!_setupReady.value) return
    preferences.edit()
      .putInt("stock_id", _selectedFilmStock.value?.id ?: -1)
      .putInt("iso", activeIso)
      .putInt("push_stops", _pushStops.value ?: -1)
      .putString("format", _selectedFormat.value.name)
      .putInt("focal_length", _focalLengthMm.value)
      .putString("mode", _exposureMode.value.name)
      .putInt("aperture", _selectedApertureIndex.value)
      .putInt("shutter", _selectedShutterIndex.value)
      .putString("aperture_value", availableApertures[_selectedApertureIndex.value].toString())
      .putString("shutter_seconds", availableShutterSeconds[_selectedShutterIndex.value].toString())
      .putInt("camera_id", _currentCameraSetup.value?.id ?: -1)
      .putFloat("exposure_compensation", _exposureCompensation.value.toFloat())
      .putFloat("filter_stops", _filterStops.value.toFloat())
      .putString("roll_id", _currentRollId.value)
      .putString("roll_name", _currentRollName.value)
      .putInt("roll_frame_count", _activeRollFrameCount.value)
      .putBoolean("haptics", hapticManager.isHapticsEnabled)
      .putBoolean("rolls_v2", true)
      .apply()
    val roll = _currentRoll.value?.let { current -> rollFromSetup(current) } ?: return
    _currentRoll.value = roll
    val alreadyQueued = pendingRollSetups.containsKey(roll.id)
    pendingRollSetups[roll.id] = roll
    if (alreadyQueued) return
    viewModelScope.launch {
      try {
        rollWrites.withLock {
          val snapshot = pendingRollSetups.remove(roll.id) ?: return@withLock
          val latest = _currentRoll.value?.takeIf { it.id == roll.id } ?: snapshot
          repository.saveRoll(latest)
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not save your roll setup. Try again before closing the app."
      }
    }
  }

  private fun rollFromSetup(existing: FilmRoll? = null): FilmRoll {
    // A new meter setup must not relabel a roll that already contains photographs.
    if (existing != null && (existing.isFinished || existing.lastFrameNumber > 0 && !setupMatchesRoll(existing))) return existing
    val hasFrames = existing != null && existing.lastFrameNumber > 0
    return FilmRoll(
    id = _currentRollId.value, name = _currentRollName.value,
    filmStockId = _selectedFilmStock.value?.id,
    filmStockName = _selectedFilmStock.value?.name ?: existing?.filmStockName.orEmpty(),
    filmType = _selectedFilmStock.value?.type ?: existing?.filmType,
    iso = if (hasFrames) existing!!.iso else activeIso,
    boxIso = if (hasFrames) existing?.boxIso else _selectedFilmStock.value?.iso,
    pushStops = if (hasFrames) existing?.pushStops else _pushStops.value,
    format = _selectedFormat.value.name,
    cameraSetupId = _currentCameraSetup.value?.id, focalLengthMm = _focalLengthMm.value,
    frameCapacity = existing?.frameCapacity ?: _selectedFormat.value.standardFramesPerRoll,
    lastFrameNumber = _activeRollFrameCount.value, isFinished = existing?.isFinished ?: false,
    createdAt = existing?.createdAt ?: System.currentTimeMillis())
  }

  private fun setupMatchesRoll(roll: FilmRoll): Boolean =
    (roll.cameraSetupId == null || roll.cameraSetupId == _currentCameraSetup.value?.id) &&
      roll.format == _selectedFormat.value.name &&
      (roll.filmStockId == null || _selectedFilmStock.value == null || roll.filmStockId == _selectedFilmStock.value?.id)

  fun getCurrentRollGuidance(): String? {
    val roll = _currentRoll.value ?: return null
    return when {
      roll.isFinished -> "This roll is finished. Continue it or start a new roll to save frames."
      roll.lastFrameNumber > 0 && !setupMatchesRoll(roll) -> "Choose or start a roll for this camera and film before saving."
      roll.lastFrameNumber >= roll.frameCapacity -> "This roll is full. Start a new roll or update its capacity."
      else -> null
    }
  }

  fun canSaveToCurrentRoll(): Boolean = _setupReady.value && _currentRoll.value != null && getCurrentRollGuidance() == null

  private fun restoreExposureValues() {
    val aperture = preferences.getString("aperture_value", null)?.toDoubleOrNull()
      ?: ExposureCalculator.APERTURE_VALUES[preferences.getInt("aperture", 7).coerceIn(0, ExposureCalculator.APERTURE_VALUES.lastIndex)]
    val shutter = preferences.getString("shutter_seconds", null)?.toDoubleOrNull()
      ?: ExposureCalculator.SHUTTER_SECONDS[preferences.getInt("shutter", 6).coerceIn(0, ExposureCalculator.SHUTTER_SECONDS.lastIndex)]
    _selectedApertureIndex.value = nearest(availableApertures, aperture)
    _selectedShutterIndex.value = nearest(availableShutterSeconds, shutter)
  }

  private fun nearest(values: List<Double>, value: Double): Int = values.indices.minBy {
    abs(log2(values[it]) - log2(value.takeIf { number -> number.isFinite() && number > 0.0 } ?: values.first()))
  }

  private fun invalidateReading() {
    rawEv100.value = null
    _hasReading.value = false
    _readingStatus.value = ReadingStatus.WAITING
    _readingMessage.value = null
    _savedEntry.value = null
  }

  fun invalidateCameraReading() {
    if (_measurementSource.value == MeasurementSource.CAMERA && !sensorManager.isLocked.value) invalidateReading()
  }

  private fun acceptReading(ev100: Double, measuredLux: Float? = null) {
    if ((!meteringActive && _measurementSource.value != MeasurementSource.SUNNY_16) ||
      sensorManager.isLocked.value || !ev100.isFinite()) return
    rawEv100.value = ev100
    _hasReading.value = true
    measuredLux?.let(sensorManager::setManualLux)
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
    _calibrationOffsetEv.value = calibrationOffsetFor(source)
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
    return ExposureCalculator.ev100ToEvForIso(ev100, activeIso) - _exposureCompensation.value - _filterStops.value
  }

  fun getBaseMeteredSeconds(): Double? {
    val ev = computeEv100()
    if (!ev.isFinite()) return null
    val aperture = availableApertures[_selectedApertureIndex.value]
    return (aperture * aperture / 2.0.pow(ExposureCalculator.ev100ToEvForIso(ev, activeIso)))
      .takeIf { it.isFinite() && it > 0.0 }
  }

  fun getFilteredMeteredSeconds(): Double? = if (_exposureMode.value == ExposureMode.APERTURE_PRIORITY) {
    getBaseMeteredSeconds()?.let { it * 2.0.pow(_exposureCompensation.value + _filterStops.value) }
      ?.takeIf { it.isFinite() && it > 0.0 && it <= ExposureCalculator.MAX_EXPOSURE_SECONDS }
  } else getRawShutterSeconds()

  fun getRawShutterSeconds(): Double? {
    val selected = availableShutterSeconds[_selectedShutterIndex.value]
    val stock = _selectedFilmStock.value
    return if (_exposureMode.value == ExposureMode.APERTURE_PRIORITY) {
      val target = getFilteredMeteredSeconds() ?: return null
      val desired = if (stock == null) target else stock.calculateCorrectedExposure(target)
      when {
        desired == null -> target
        _currentCameraSetup.value?.allowsBulb != false && desired >= 1.0 -> target
        stock == null -> selected
        else -> stock.calculateMeteredExposure(selected)
      }
    } else if (stock == null) selected
      else stock.calculateMeteredExposure(selected)
  }

  fun getCorrectedShutterSeconds(): Double? {
    val raw = getRawShutterSeconds() ?: return null
    val stock = _selectedFilmStock.value
    val selected = availableShutterSeconds[_selectedShutterIndex.value]
    val actual = if (_exposureMode.value == ExposureMode.APERTURE_PRIORITY) {
      val target = getFilteredMeteredSeconds() ?: return null
      val desired = (if (stock == null) target else stock.calculateCorrectedExposure(target)) ?: return null
      if (_currentCameraSetup.value?.allowsBulb != false && desired >= 1.0) desired else selected
    } else selected
    if (_exposureMode.value != ExposureMode.MANUAL_MATCH && !isWithinDialTolerance()) return null
    return actual
  }

  /** Accepts rounding up to half the gap to the next dial value toward the target, and never less than 0.6 stop. */
  private fun dialToleranceStops(delta: Double): Double {
    val gap = when (_exposureMode.value) {
      ExposureMode.APERTURE_PRIORITY -> {
        // A positive delta means too little exposure: the target lies toward a longer shutter.
        val values = availableShutterSeconds
        val index = _selectedShutterIndex.value
        values.getOrNull(if (delta > 0) index + 1 else index - 1)?.let { abs(log2(it) - log2(values[index])) }
      }
      ExposureMode.SHUTTER_PRIORITY -> {
        val values = availableApertures
        val index = _selectedApertureIndex.value
        values.getOrNull(if (delta > 0) index - 1 else index + 1)?.let { 2 * abs(log2(it) - log2(values[index])) }
      }
      ExposureMode.MANUAL_MATCH -> null
    }
    return maxOf(MIN_DIAL_TOLERANCE_STOPS, (gap ?: 0.0) / 2 + 0.01)
  }

  fun exposureToleranceStops(): Double = getEvDelta()?.let(::dialToleranceStops) ?: MIN_DIAL_TOLERANCE_STOPS

  private fun isWithinDialTolerance(): Boolean = getEvDelta()?.let { abs(it) <= dialToleranceStops(it) } ?: true

  private fun closestActualShutter(idealMetered: Double): Int? {
    val stock = _selectedFilmStock.value
    var best: Int? = null
    var smallestError = Double.POSITIVE_INFINITY
    for ((index, actual) in availableShutterSeconds.withIndex()) {
      val metered = (if (stock == null) actual else stock.calculateMeteredExposure(actual)) ?: continue
      val error = abs(log2(metered) - log2(idealMetered))
      if (error < smallestError) { best = index; smallestError = error }
    }
    return best
  }

  fun getEvDelta(): Double? {
    val seconds = getRawShutterSeconds() ?: return null
    val ev = computeEvForActiveIso()
    return if (ev.isFinite()) ExposureCalculator.calculateEvDelta(_selectedApertureIndex.value, seconds, ev, availableApertures) else null
  }

  fun getExposureGuidance(): String? {
    val raw = getRawShutterSeconds()
    val film = _selectedFilmStock.value?.reciprocityGuidance(raw ?: availableShutterSeconds[_selectedShutterIndex.value])
    val camera = _currentCameraSetup.value
    val limit = when {
      raw != null && !isWithinDialTolerance() && _exposureMode.value != ExposureMode.MANUAL_MATCH ->
        if (camera == null) "Outside the selectable exposure range. Change aperture, shutter or filter."
        else "Outside ${camera.name}'s exposure range. Change aperture or shutter, or use a filter."
      camera != null && raw != null && getCorrectedShutterSeconds() == null &&
        (_selectedFilmStock.value == null || _selectedFilmStock.value?.calculateCorrectedExposure(raw) != null) ->
        "This duration is not on ${camera.name}'s shutter dial. Enable Bulb in the camera setup or choose another aperture."
      _exposureMode.value == ExposureMode.APERTURE_PRIORITY && raw == null ->
        if (getFilteredMeteredSeconds() == null) "Exposure is outside the supported one-day range."
        else "No shutter setting supports this film profile. Choose another aperture or camera setting."
      else -> null
    }
    return listOfNotNull(limit, film).takeIf { it.isNotEmpty() }?.joinToString("\n")
  }

  fun setExposureCompensation(stops: Double) {
    if (_isSaving.value || !stops.isFinite() || stops !in -3.0..3.0) return
    _exposureCompensation.value = (stops * 3).roundToInt() / 3.0
    recalculateForMode()
    persistSetup()
  }

  fun setFilterStops(stops: Double) {
    if (_isSaving.value || !stops.isFinite() || stops !in 0.0..16.0) return
    _filterStops.value = stops
    recalculateForMode()
    persistSetup()
  }

  fun resetExposureAdjustments() {
    if (_isSaving.value) return
    _exposureCompensation.value = 0.0
    _filterStops.value = 0.0
    recalculateForMode()
    persistSetup()
  }

  fun isLongExposure(): Boolean {
    return (getCorrectedShutterSeconds() ?: 0.0) >= 1.0
  }

  fun setFormat(format: CameraFormat) {
    if (sensorManager.isLocked.value || _isSaving.value || format == _selectedFormat.value) return
    _selectedFormat.value = format
    _focalLengthMm.value = DepthOfFieldCalculator.getDefaultFocalLength(format)
    if (_activeRollFrameCount.value == 0) _currentRoll.value = _currentRoll.value?.copy(frameCapacity = format.standardFramesPerRoll)
    if (_measurementSource.value == MeasurementSource.CAMERA) invalidateReading()
    persistSetup()
  }

  fun setApertureIndex(index: Int) {
    if (_isSaving.value) return
    _selectedApertureIndex.value = index.coerceIn(0, availableApertures.lastIndex)
    if (_exposureMode.value == ExposureMode.APERTURE_PRIORITY) {
      recalculateForMode()
    }
    persistSetup()
  }

  fun setShutterIndex(index: Int) {
    if (_isSaving.value) return
    _selectedShutterIndex.value = index.coerceIn(0, availableShutterSeconds.lastIndex)
    if (_exposureMode.value == ExposureMode.SHUTTER_PRIORITY) {
      recalculateForMode()
    }
    persistSetup()
  }

  fun setFilmStock(stock: FilmStock) {
    applyFilmSettings(stock, stock.iso, 0)
  }

  fun needsFilmSettingsConfirmation(stock: FilmStock?, iso: Int, pushStops: Int?): Boolean {
    val roll = _currentRoll.value ?: return false
    return !roll.isFinished && roll.lastFrameNumber > 0 && filmMatchesRoll(roll, stock) &&
      (roll.iso != iso || roll.pushStops != pushStops)
  }

  private fun filmMatchesRoll(roll: FilmRoll, stock: FilmStock?): Boolean =
    if (roll.filmStockId != null) roll.filmStockId == stock?.id
    else stock == null || roll.filmStockName == stock.name

  fun applyFilmSettings(stock: FilmStock?, iso: Int, pushStops: Int?, confirmRollChange: Boolean = false): Boolean {
    if (!_setupReady.value || _isSaving.value || iso !in 1..102400 ||
      (stock != null && stock.iso <= 0) || (pushStops != null && pushStops !in 0..4) ||
      (pushStops != null && pushStops > 0 && stock?.isoForPush(pushStops) == null)) return false
    if (needsFilmSettingsConfirmation(stock, iso, pushStops) && !confirmRollChange) return false
    _selectedFilmStock.value = stock
    _customIso.value = iso
    _pushStops.value = pushStops
    _currentRoll.value?.takeIf { !it.isFinished && filmMatchesRoll(it, stock) }?.let { roll ->
      // Only this explicit edit may change a populated roll's processing plan.
      _currentRoll.value = roll.copy(iso = iso, boxIso = roll.boxIso ?: stock?.iso, pushStops = pushStops)
    }
    recalculateForMode()
    persistSetup()
    return true
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
    if (wasAperture && mode != ExposureMode.APERTURE_PRIORITY) {
      val seconds = getCorrectedShutterSeconds()
      if (seconds != null) {
        _selectedShutterIndex.value = availableShutterSeconds.indices.minBy {
          abs(log2(availableShutterSeconds[it]) - log2(seconds))
        }
        if (wasAperture && seconds > availableShutterSeconds.last()) {
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
        val target = getFilteredMeteredSeconds()
        val stock = _selectedFilmStock.value
        val desired = target?.let { if (stock == null) it else stock.calculateCorrectedExposure(it) }
        _selectedShutterIndex.value = when {
          target == null -> ExposureCalculator.calculateShutterIndexForAperture(
            _selectedApertureIndex.value, evForIso, availableApertures, availableShutterSeconds)
          desired == null -> nearest(availableShutterSeconds, target)
          _currentCameraSetup.value?.allowsBulb != false && desired >= 1.0 -> nearest(availableShutterSeconds, desired)
          else -> closestActualShutter(target) ?: nearest(availableShutterSeconds, desired)
        }
      }
      ExposureMode.SHUTTER_PRIORITY -> {
        val seconds = getRawShutterSeconds() ?: return
        val newApertureIdx = ExposureCalculator.calculateApertureIndexForShutterSeconds(
          seconds,
          evForIso, availableApertures
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
    val value = apertureLabel.removePrefix("f/").toDoubleOrNull() ?: return
    val idx = availableApertures.indexOfFirst { abs(it - value) < 1e-9 }
    if (idx != -1) setApertureIndex(idx)
  }

  fun setShutterDirectly(shutterLabel: String) {
    if (_isSaving.value) return
    val value = ExposureCalculator.parseShutterSpeeds(shutterLabel)?.singleOrNull() ?: return
    val idx = availableShutterSeconds.indexOfFirst { abs(it - value) < 1e-9 }
    if (idx != -1) setShutterIndex(idx)
  }

  /** [ruleSeconds] is the rule's unrounded time (1/ISO); without it the dial label's time sets the estimate. */
  fun applySunny16Preset(apertureLabel: String, shutterLabel: String, iso: Int, ruleSeconds: Double? = null) {
    if (_isSaving.value || iso !in 1..102400) return
    val referenceAperture = apertureLabel.removePrefix("f/").toDoubleOrNull()
      ?.takeIf { it.isFinite() && it in 1.0..64.0 } ?: return
    val referenceSeconds = ExposureCalculator.parseShutterSpeeds(shutterLabel)?.singleOrNull() ?: return
    val estimateSeconds = ruleSeconds?.takeIf { it.isFinite() && it > 0.0 } ?: referenceSeconds
    setMeasurementSource(MeasurementSource.SUNNY_16)
    sensorManager.setLocked(false)
    setIso(iso)
    _selectedApertureIndex.value = nearest(availableApertures, referenceAperture)
    _selectedShutterIndex.value = nearest(availableShutterSeconds, referenceSeconds)
    // The weather estimate is independent of which values the camera can dial.
    setPresetEv100(log2(referenceAperture * referenceAperture / estimateSeconds) - log2(iso / 100.0))
    persistSetup()
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

  fun logExposure(notes: String = "", photo: Bitmap? = null) {
    if (!_setupReady.value || _isSaving.value || _savedEntry.value != null ||
      _readingStatus.value != ReadingStatus.HELD || rawEv100.value == null) return
    val rawSec = getRawShutterSeconds() ?: return
    val corrSec = getCorrectedShutterSeconds() ?: return
    saveFrame(availableApertures[_selectedApertureIndex.value], rawSec, corrSec, notes, manual = false,
      photo = photo.takeIf { _measurementSource.value == MeasurementSource.CAMERA })
  }

  fun recordManualExposure(aperture: Double, actualSeconds: Double, notes: String = "", photoUri: Uri? = null) {
    if (!_setupReady.value || _isSaving.value || _savedEntry.value != null ||
      !aperture.isFinite() || aperture !in 1.0..64.0 || !actualSeconds.isFinite() ||
      actualSeconds <= 0.0 || actualSeconds > ExposureCalculator.MAX_EXPOSURE_SECONDS) return
    val measured = computeEv100().takeIf { it.isFinite() && _readingStatus.value == ReadingStatus.HELD }?.let {
      (aperture * aperture / 2.0.pow(ExposureCalculator.ev100ToEvForIso(it, activeIso) -
        _exposureCompensation.value - _filterStops.value)).takeIf { seconds ->
          seconds.isFinite() && seconds > 0.0 && seconds <= ExposureCalculator.MAX_EXPOSURE_SECONDS }
    } ?: 0.0
    saveFrame(aperture, measured, actualSeconds, notes, manual = true, photoUri = photoUri)
  }

  fun beginNextManualFrame(): Boolean {
    if (_isSaving.value || _savedEntry.value?.isManual != true || !canSaveToCurrentRoll()) return false
    _savedEntry.value = null
    if (sensorManager.isLocked.value) toggleHold()
    return true
  }

  fun recordManualExposureForRoll(
    rollId: String, aperture: Double, actualSeconds: Double, notes: String = "", photoUri: Uri? = null
  ) {
    if (!_setupReady.value || _isSaving.value || _manualSavedEntry.value != null ||
      !aperture.isFinite() || aperture !in 1.0..64.0 || !actualSeconds.isFinite() ||
      actualSeconds <= 0.0 || actualSeconds > ExposureCalculator.MAX_EXPOSURE_SECONDS || notes.length > 5000) return
    _isSaving.value = true
    viewModelScope.launch {
      var photoName: String? = null
      var committed = false
      var photoFailed = false
      try {
        rollWrites.withLock {
          val roll = repository.getRoll(rollId)
          if (roll == null) { _userMessage.value = "This roll no longer exists."; return@withLock }
          if (roll.isFinished) { _userMessage.value = "Reopen this roll before adding a shot."; return@withLock }
          val lastFrame = maxOf(roll.lastFrameNumber, repository.getLatestEntryForRoll(rollId)?.frameNumber ?: 0)
          if (lastFrame >= roll.frameCapacity) { _userMessage.value = "This roll is full. Update its frame capacity before adding a shot."; return@withLock }
          val camera = roll.cameraSetupId?.let { repository.getCameraSetup(it) }
          if (photoUri != null) {
            try { photoName = photos.importPhoto(photoUri) }
            catch (error: Exception) {
              if (error is CancellationException) throw error
              photoFailed = true
            }
          }
          val entry = FilmLogEntry(
            rollId = roll.id, rollName = roll.name, frameNumber = lastFrame + 1,
            filmStockName = roll.filmStockName.ifBlank { "Custom (ISO ${roll.iso})" }, iso = roll.iso,
            boxIso = roll.boxIso, format = CameraFormat.entries.firstOrNull { it.name == roll.format }?.displayName ?: roll.format,
            apertureText = ExposureCalculator.formatAperture(aperture), shutterText = ExposureCalculator.formatSeconds(actualSeconds),
            rawSeconds = 0.0, correctedSeconds = actualSeconds, evValue = 0.0, luxValue = 0f,
            notes = notes, cameraName = camera?.name.orEmpty(), focalLengthMm = roll.focalLengthMm,
            correctionKind = "Manual", isManual = true, hasMeterReading = false, photoFileName = photoName
          )
          val updated = roll.copy(lastFrameNumber = entry.frameNumber)
          val id = repository.insertFrame(entry, updated).toInt()
          committed = true
          updateCurrentRollProgress(updated)
          _manualSavedEntry.value = entry.copy(id = id)
        }
        if (photoFailed && committed) _userMessage.value = "Shot saved without a photo. Open the shot to add it again."
      } catch (error: Exception) {
        if (!committed) deleteUnusedPhoto(photoName)
        if (error is CancellationException) throw error
        _userMessage.value = "Shot was not saved. Your details are kept; try again."
      } finally { _isSaving.value = false }
    }
  }

  fun clearManualSavedReceipt() { if (!_isSaving.value) _manualSavedEntry.value = null }

  fun undoManualSavedExposure() {
    val receipt = _manualSavedEntry.value ?: return
    if (_isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        rollWrites.withLock {
          val entry = repository.getLogEntry(receipt.id)
          val roll = repository.getRoll(receipt.rollId)
          if (entry == null || roll == null) { _manualSavedEntry.value = null; return@withLock }
          val latest = repository.getLatestEntryForRoll(roll.id)
          val count = if (latest?.id == entry.id && roll.lastFrameNumber == entry.frameNumber)
            roll.lastFrameNumber - 1 else roll.lastFrameNumber
          val updated = roll.copy(lastFrameNumber = count)
          repository.deleteFrameAndUpdateRoll(entry.id, updated)
          updateCurrentRollProgress(updated)
          _manualSavedEntry.value = null
          deleteUnusedPhoto(entry.photoFileName)
          _userMessage.value = "Frame ${entry.frameNumber} removed."
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not undo the saved shot. Try again."
      } finally { _isSaving.value = false }
    }
  }

  private fun updateCurrentRollProgress(roll: FilmRoll) {
    if (_currentRollId.value != roll.id) return
    _currentRoll.value = roll
    _activeRollFrameCount.value = roll.lastFrameNumber
    preferences.edit().putInt("roll_frame_count", roll.lastFrameNumber).apply()
  }

  fun photoFile(name: String?): File? = photos.file(name)

  private suspend fun deleteUnusedPhoto(name: String?) {
    if (name == null) return
    try { photos.delete(name) }
    catch (error: Exception) {
      if (error is CancellationException) throw error
      // Failed file cleanup is retried by the orphan sweep on a later launch.
    }
  }

  private fun saveFrame(
    aperture: Double, rawSec: Double, corrSec: Double, notes: String, manual: Boolean,
    photo: Bitmap? = null, photoUri: Uri? = null
  ) {
    val roll = _currentRoll.value ?: return
    getCurrentRollGuidance()?.let { _userMessage.value = it; return }
    if (notes.length > 5000) { _userMessage.value = "Keep frame notes within 5,000 characters."; return }
    if (roll.isFinished) { _userMessage.value = "Continue this roll or start a new one before saving."; return }
    val nextFrame = _activeRollFrameCount.value + 1
    if (nextFrame > roll.frameCapacity) { _userMessage.value = "This roll is full. Start a new roll or update its frame capacity."; return }
    val stock = _selectedFilmStock.value
    val ev100 = if (manual && _readingStatus.value != ReadingStatus.HELD) Double.NaN else computeEv100()
    val entry = FilmLogEntry(
      rollId = _currentRollId.value,
      rollName = _currentRollName.value,
      frameNumber = nextFrame,
      filmStockName = stock?.name ?: roll.filmStockName.ifBlank { "Custom (ISO $activeIso)" },
      iso = activeIso,
      boxIso = stock?.iso ?: roll.boxIso,
      format = _selectedFormat.value.displayName,
      apertureText = ExposureCalculator.formatAperture(aperture),
      shutterText = if (manual || _exposureMode.value == ExposureMode.APERTURE_PRIORITY) ExposureCalculator.formatSeconds(corrSec)
        else shutterLabels[_selectedShutterIndex.value],
      rawSeconds = rawSec,
      correctedSeconds = corrSec,
      evValue = if (ev100.isFinite()) ExposureCalculator.ev100ToEvForIso(ev100, activeIso) else 0.0,
      // Only the light sensor measures lux; other sources record none rather than a value derived from EV.
      luxValue = if (_measurementSource.value == MeasurementSource.SENSOR && ev100.isFinite()) sensorManager.lux.value else 0f,
      notes = notes,
      cameraName = _currentCameraSetup.value?.name.orEmpty(), focalLengthMm = _focalLengthMm.value,
      meteringSource = _measurementSource.value.name, exposureCompensation = _exposureCompensation.value,
      filterStops = _filterStops.value, calibrationEv = if (_measurementSource.value == MeasurementSource.SUNNY_16) 0f else _calibrationOffsetEv.value,
      correctionKind = if (manual) "Manual" else stock?.correctionLabel ?: "No film profile",
      isManual = manual, hasMeterReading = ev100.isFinite()
    )

    _isSaving.value = true
    viewModelScope.launch {
      var photoName: String? = null
      var committed = false
      var photoFailed = !manual && entry.meteringSource == MeasurementSource.CAMERA.name && photo == null
      try {
        try {
          photoName = if (photoUri != null) photos.importPhoto(photoUri) else photo?.let { photos.save(it) }
        } catch (error: Exception) {
          if (error is CancellationException) throw error
          photoFailed = true
        }
        rollWrites.withLock {
          val updated = rollFromSetup(_currentRoll.value).copy(lastFrameNumber = nextFrame)
          val withPhoto = entry.copy(photoFileName = photoName)
          val id = repository.insertFrame(withPhoto, updated).toInt()
          committed = true
          _activeRollFrameCount.value = nextFrame
          _currentRoll.value = updated
          _savedEntry.value = withPhoto.copy(id = id)
        }
        persistSetup()
        if (photoFailed) _userMessage.value = "Frame saved without a photo. Open the frame to add it again."
      } catch (error: Exception) {
        if (!committed) deleteUnusedPhoto(photoName)
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
        rollWrites.withLock {
          val count = if (entry.frameNumber == _activeRollFrameCount.value) _activeRollFrameCount.value - 1 else _activeRollFrameCount.value
          val updated = rollFromSetup(_currentRoll.value).copy(lastFrameNumber = count)
          repository.deleteFrameAndUpdateRoll(entry.id, updated)
          updateCurrentRollProgress(updated)
          _savedEntry.value = null
          deleteUnusedPhoto(entry.photoFileName)
        }
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

  fun createRoll(
    name: String, capacity: Int, film: FilmStock?, camera: CameraSetup?, format: CameraFormat,
    focalLengthMm: Int, iso: Int, pushStops: Int?
  ) {
    if (!_setupReady.value || _isSaving.value || _createdRollId.value != null ||
      name.trim().length !in 1..100 || capacity !in 1..1000 ||
      focalLengthMm !in 10..1000 || iso !in 1..102400 || (pushStops != null && pushStops !in 0..4) ||
      (camera != null && camera.format != format.name) ||
      (film != null && film.iso <= 0) ||
      (pushStops != null && pushStops > 0 && film?.isoForPush(pushStops) == null)) return
    _isSaving.value = true
    _createdRollId.value = null
    viewModelScope.launch {
      try {
        rollWrites.withLock {
          if (film != null && repository.getFilmStock(film.id) == null) {
            _userMessage.value = "This film no longer exists. Choose another film."; return@withLock
          }
          if (camera != null && repository.getCameraSetup(camera.id) == null) {
            _userMessage.value = "This camera setup no longer exists. Choose another camera."; return@withLock
          }
          val roll = FilmRoll(
            id = UUID.randomUUID().toString(), name = name.trim(), filmStockId = film?.id,
            filmStockName = film?.name ?: "Unlisted film", filmType = film?.type, iso = iso,
            boxIso = film?.iso, pushStops = pushStops, format = format.name,
            cameraSetupId = camera?.id, focalLengthMm = focalLengthMm, frameCapacity = capacity
          )
          repository.saveRoll(roll)
          _createdRollId.value = roll.id
          _userMessage.value = "Created ${roll.name}. Add a shot to start its log."
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not create the roll. Your choices are kept; try again."
      } finally { _isSaving.value = false }
    }
  }

  fun clearCreatedRollReceipt() { if (!_isSaving.value) _createdRollId.value = null }

  fun startNewRoll(rollName: String, frameCapacity: Int = _selectedFormat.value.standardFramesPerRoll, pushStops: Int = 0) {
    if (!_setupReady.value || _isSaving.value || frameCapacity !in 1..1000 || rollName.trim().length !in 1..100 ||
      pushStops !in 0..4) return
    // A hand-set EI carries over to an unpushed roll; a pushed EI belongs to that roll's development, so reset it.
    val keepChosenEi = pushStops == 0 && (_pushStops.value ?: 0) == 0
    val iso = if (keepChosenEi) activeIso else _selectedFilmStock.value?.isoForPush(pushStops)
      ?: if (pushStops == 0) activeIso else return
    _currentRollId.value = UUID.randomUUID().toString()
    _currentRollName.value = rollName.trim().ifEmpty { "New roll" }
    _activeRollFrameCount.value = 0
    _savedEntry.value = null
    _customIso.value = iso
    _pushStops.value = pushStops
    _currentRoll.value = rollFromSetup().copy(frameCapacity = frameCapacity)
    _exposureCompensation.value = 0.0
    _filterStops.value = 0.0
    recalculateForMode()
    persistSetup()
    _userMessage.value = "Started new roll: $rollName"
  }

  fun continueRoll(id: String) {
    if (!_setupReady.value || _isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        rollWrites.withLock {
          val roll = repository.getRoll(id) ?: return@withLock
          val camera = roll.cameraSetupId?.let { repository.getCameraSetup(it) }
          val stock = roll.filmStockId?.let { repository.getFilmStock(it) }
          val latestFrame = repository.getLatestEntryForRoll(id)?.frameNumber ?: 0
          val continued = roll.copy(isFinished = false, lastFrameNumber = maxOf(roll.lastFrameNumber, latestFrame))
          repository.saveRoll(continued)
          applyCameraSetup(camera)
          _selectedFilmStock.value = stock
          _customIso.value = roll.iso
          _pushStops.value = roll.pushStops
          _selectedFormat.value = CameraFormat.entries.firstOrNull { it.name == roll.format } ?: CameraFormat.FORMAT_35MM
          _focalLengthMm.value = roll.focalLengthMm.coerceIn(10, 1000)
          _currentRollId.value = roll.id
          _currentRollName.value = roll.name
          _activeRollFrameCount.value = continued.lastFrameNumber
          _currentRoll.value = continued
          _savedEntry.value = null
          _exposureCompensation.value = 0.0
          _filterStops.value = 0.0
          sensorManager.setLocked(false)
          invalidateReading()
          if (meteringActive && _measurementSource.value == MeasurementSource.SENSOR) startSensor()
          persistSetup()
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not continue this roll. Try again."
      } finally { _isSaving.value = false }
    }
  }

  fun finishCurrentRoll() {
    val roll = _currentRoll.value ?: return
    if (_isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        rollWrites.withLock {
          val finished = rollFromSetup(_currentRoll.value).copy(isFinished = true)
          repository.saveRoll(finished)
          _currentRoll.value = finished
          _savedEntry.value = null
          persistSetup()
        }
        _userMessage.value = "Finished ${roll.name}. Your frames are kept."
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not finish the roll. Try again."
      } finally { _isSaving.value = false }
    }
  }

  fun setCurrentFrame(lastFrameNumber: Int) = updateRollProgress(lastFrameNumber = lastFrameNumber)
  fun setCurrentRollFrameCapacity(capacity: Int) = updateRollProgress(capacity = capacity)

  private fun updateRollProgress(lastFrameNumber: Int? = null, capacity: Int? = null) {
    val roll = _currentRoll.value ?: return
    if (_isSaving.value || roll.isFinished || (capacity != null && capacity !in 1..1000) ||
      (lastFrameNumber != null && lastFrameNumber !in 0..(capacity ?: roll.frameCapacity))) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        rollWrites.withLock {
          val highest = repository.getLatestEntryForRoll(roll.id)?.frameNumber ?: 0
          val count = lastFrameNumber ?: _activeRollFrameCount.value
          if (count < highest || (capacity ?: roll.frameCapacity) < count) {
            _userMessage.value = "The frame count and capacity must include your saved frames."
            return@withLock
          }
          val updated = rollFromSetup(_currentRoll.value).copy(lastFrameNumber = count, frameCapacity = capacity ?: roll.frameCapacity)
          repository.saveRoll(updated)
          _activeRollFrameCount.value = count
          _currentRoll.value = updated
          persistSetup()
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not update the roll. Try again."
      } finally { _isSaving.value = false }
    }
  }

  fun updateLogNotes(id: Int, notes: String) {
    if (_isSaving.value || notes.length > 5000) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        repository.updateLogNotes(id, notes)
        if (_savedEntry.value?.id == id) _savedEntry.value = _savedEntry.value?.copy(notes = notes)
        if (_manualSavedEntry.value?.id == id) _manualSavedEntry.value = _manualSavedEntry.value?.copy(notes = notes)
        _userMessage.value = "Frame note saved."
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not save the note. Try again."
      } finally { _isSaving.value = false }
    }
  }

  fun updateLogPhoto(id: Int, uri: Uri?) {
    if (_isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      var newName: String? = null
      var committed = false
      try {
        rollWrites.withLock {
          val entry = repository.getLogEntry(id)
          if (entry == null) { _userMessage.value = "This shot no longer exists."; return@withLock }
          newName = uri?.let { photos.importPhoto(it) }
          repository.updateLogPhoto(id, newName)
          committed = true
          if (_savedEntry.value?.id == id) _savedEntry.value = _savedEntry.value?.copy(photoFileName = newName)
          if (_manualSavedEntry.value?.id == id) _manualSavedEntry.value = _manualSavedEntry.value?.copy(photoFileName = newName)
          deleteUnusedPhoto(entry.photoFileName)
          _userMessage.value = if (uri == null) "Photo removed." else "Photo saved."
        }
      } catch (error: Exception) {
        if (!committed) deleteUnusedPhoto(newName)
        if (error is CancellationException) throw error
        _userMessage.value = "Could not change the photo. The previous photo is kept."
      } finally { _isSaving.value = false }
    }
  }

  private fun validCameraSetup(camera: CameraSetup): Boolean = camera.name.trim().length in 1..100 &&
    CameraFormat.entries.any { it.name == camera.format } && camera.focalLengthMm in 10..1000 &&
    camera.minAperture.isFinite() && camera.maxAperture.isFinite() && camera.minAperture in 1.0..64.0 &&
    camera.maxAperture in camera.minAperture..64.0 && ExposureCalculator.parseShutterSpeeds(camera.shutterSpeeds) != null

  private fun applyCameraSetup(camera: CameraSetup?) {
    val aperture = availableApertures[_selectedApertureIndex.value]
    val shutter = availableShutterSeconds[_selectedShutterIndex.value]
    _currentCameraSetup.value = camera?.takeIf(::validCameraSetup)
    _selectedApertureIndex.value = nearest(availableApertures, aperture)
    _selectedShutterIndex.value = nearest(availableShutterSeconds, shutter)
    _currentCameraSetup.value?.let {
      if (_activeRollFrameCount.value == 0 && _selectedFormat.value.name != it.format)
        _currentRoll.value = _currentRoll.value?.copy(frameCapacity = CameraFormat.valueOf(it.format).standardFramesPerRoll)
      _selectedFormat.value = CameraFormat.valueOf(it.format)
      _focalLengthMm.value = it.focalLengthMm
    }
    if (_measurementSource.value == MeasurementSource.CAMERA) {
      sensorManager.setLocked(false)
      invalidateReading()
    } else recalculateForMode()
  }

  fun selectCameraSetup(id: Int?) {
    if (!_setupReady.value || _isSaving.value || _isSavingCameraSetup.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        val camera = id?.let { repository.getCameraSetup(it) }
        if (id != null && camera == null) { _userMessage.value = "Camera setup no longer exists."; return@launch }
        applyCameraSetup(camera)
        _exposureCompensation.value = 0.0
        _filterStops.value = 0.0
        recalculateForMode()
        persistSetup()
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not load this camera setup. Try again."
      } finally { _isSaving.value = false }
    }
  }

  fun saveCameraSetup(camera: CameraSetup) {
    if (!_setupReady.value || _isSaving.value || _isSavingCameraSetup.value || !validCameraSetup(camera)) return
    _isSavingCameraSetup.value = true
    _isSaving.value = true
    _cameraSetupSavedId.value = null
    viewModelScope.launch {
      try {
        val id = repository.saveCameraSetup(camera.copy(name = camera.name.trim())).toInt()
        applyCameraSetup(camera.copy(id = id, name = camera.name.trim()))
        _exposureCompensation.value = 0.0
        _filterStops.value = 0.0
        recalculateForMode()
        persistSetup()
        _cameraSetupSavedId.value = id
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Camera was not saved. Try again."
      } finally { _isSavingCameraSetup.value = false; _isSaving.value = false }
    }
  }

  fun clearCameraSetupSavedReceipt() { _cameraSetupSavedId.value = null }

  fun deleteLogEntry(id: Int) {
    if (_isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        rollWrites.withLock {
          val entry = repository.getLogEntry(id)
          repository.deleteLogEntry(id)
          if (_savedEntry.value?.id == id) _savedEntry.value = null
          if (_manualSavedEntry.value?.id == id) _manualSavedEntry.value = null
          deleteUnusedPhoto(entry?.photoFileName)
        }
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
        rollWrites.withLock {
          val photoNames = repository.getEntriesForRoll(rollId).first().mapNotNull { it.photoFileName }
          repository.deleteRoll(rollId)
          if (_manualSavedEntry.value?.rollId == rollId) _manualSavedEntry.value = null
          if (_currentRollId.value == rollId) {
            _isSaving.value = false
            startNewRoll("New Roll")
            _isSaving.value = true
          }
          photoNames.forEach { deleteUnusedPhoto(it) }
        }
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
    if (!stock.isCustom || stock.name.isBlank() || stock.iso !in 1..12800 ||
      stock.type !in listOf("Color Negative", "Black & White", "Color Slide", "Tungsten Cine") ||
      (stock.hasReciprocityProfile && (!stock.reciprocityFactor.isFinite() || stock.reciprocityFactor < 1.0 ||
        ExposureCalculator.SHUTTER_SECONDS.max().pow(stock.reciprocityFactor) > ExposureCalculator.MAX_EXPOSURE_SECONDS))) {
      _userMessage.value = "Enter a film name, valid ISO and type, and a supported correction profile."
      return
    }
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
    if (_isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        // An unfinished roll with frames would lose its film profile and meter without correction.
        if (repository.allRolls.first().any { it.filmStockId == id && !it.isFinished && it.lastFrameNumber > 0 }) {
          _userMessage.value = "This film is loaded in an unfinished roll. Finish that roll before deleting the film."
          return@launch
        }
        repository.deleteStock(id)
        if (_selectedFilmStock.value?.id == id) {
          _selectedFilmStock.value = null
          recalculateForMode()
          persistSetup()
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userMessage.value = "Could not delete the film. Try again."
      } finally { _isSaving.value = false }
    }
  }

  fun calibrationOffsetFor(source: MeasurementSource): Float {
    if (source == MeasurementSource.SUNNY_16) return 0f
    val fallback = if (source == MeasurementSource.CAMERA) deviceCalibration.getFloat("calibration", 0f) else 0f
    return deviceCalibration.getFloat(if (source == MeasurementSource.CAMERA) "camera" else "sensor", fallback)
      .takeIf { it.isFinite() }?.coerceIn(-3f, 3f) ?: 0f
  }

  fun proposeCalibrationOffset(referenceAperture: Double, referenceSeconds: Double, referenceIso: Int): Float? {
    val raw = rawEv100.value ?: return null
    if (_measurementSource.value == MeasurementSource.SUNNY_16 ||
      _readingStatus.value != ReadingStatus.HELD || !referenceAperture.isFinite() || referenceAperture !in 1.0..64.0 ||
      !referenceSeconds.isFinite() || referenceSeconds <= 0.0 || referenceSeconds > ExposureCalculator.MAX_EXPOSURE_SECONDS ||
      referenceIso !in 1..102400) return null
    val referenceEv = log2(referenceAperture * referenceAperture / referenceSeconds) - log2(referenceIso / 100.0)
    return (referenceEv - raw).toFloat().takeIf { it.isFinite() && it in -3f..3f }
  }

  fun setCalibrationOffset(offset: Float, source: MeasurementSource = _measurementSource.value.let {
    if (it == MeasurementSource.SUNNY_16) MeasurementSource.CAMERA else it
  }) {
    if (_isSaving.value || !offset.isFinite() || source == MeasurementSource.SUNNY_16) return
    val value = offset.coerceIn(-3f, 3f)
    deviceCalibration.edit().apply {
      putFloat(if (source == MeasurementSource.CAMERA) "camera" else "sensor", value)
      if (source == MeasurementSource.CAMERA) putFloat("calibration", value)
    }.apply()
    _calibrationOffsets.value = _calibrationOffsets.value + (source to value)
    _calibrationOffsetEv.value = calibrationOffsetFor(_measurementSource.value)
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
