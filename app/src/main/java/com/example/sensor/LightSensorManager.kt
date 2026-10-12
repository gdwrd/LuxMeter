package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class LightSensorManager(context: Context) : SensorEventListener {

  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

  val isHardwareSensorAvailable: Boolean = lightSensor != null

  private val _lux = MutableStateFlow(0f)
  val lux: StateFlow<Float> = _lux.asStateFlow()

  private val _readings = MutableStateFlow<Float?>(null)
  val readings: StateFlow<Float?> = _readings.asStateFlow()

  private val _isLocked = MutableStateFlow(false)
  val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

  private var smoothedLux: Float? = null
  private val smoothingFactor = 0.25f

  fun start(): Boolean {
    if (lightSensor != null && !_isLocked.value) {
      return sensorManager?.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI) == true
    }
    return false
  }

  fun stop() {
    sensorManager?.unregisterListener(this)
  }

  fun toggleLock() {
    setLocked(!_isLocked.value)
  }

  fun setLocked(locked: Boolean) {
    _isLocked.value = locked
    if (locked) stop()
  }

  fun resetReadings() {
    smoothedLux = null
    _readings.value = null
  }

  fun setManualLux(newLux: Float) {
    if (newLux.isFinite() && newLux >= 0f) _lux.value = newLux
  }

  override fun onSensorChanged(event: SensorEvent?) {
    if (_isLocked.value) return
    if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
      val rawLux = event.values.firstOrNull() ?: return
      if (rawLux.isFinite() && rawLux >= 0f) {
        val previous = smoothedLux
        val nextLux = if (previous == null || abs(rawLux - previous) > 200f) {
          rawLux // Fast transition on drastic light change
        } else {
          previous + smoothingFactor * (rawLux - previous)
        }
        smoothedLux = nextLux
        _readings.value = nextLux
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // No-op
  }
}
