package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        // Light sensors report changes, so stable light may never produce another event.
        _readings.value = rawLux
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // No-op
  }
}
