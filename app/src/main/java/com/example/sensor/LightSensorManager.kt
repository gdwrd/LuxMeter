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

  private val _lux = MutableStateFlow(400f) // default to pleasant room light (EV ~7.3)
  val lux: StateFlow<Float> = _lux.asStateFlow()

  private val _isLocked = MutableStateFlow(false)
  val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

  private var smoothedLux = 400f
  private val smoothingFactor = 0.25f

  fun start() {
    if (lightSensor != null && !_isLocked.value) {
      sensorManager?.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI)
    }
  }

  fun stop() {
    sensorManager?.unregisterListener(this)
  }

  fun toggleLock() {
    val newLock = !_isLocked.value
    _isLocked.value = newLock
    if (newLock) {
      stop()
    } else {
      start()
    }
  }

  fun setManualLux(newLux: Float) {
    smoothedLux = newLux.coerceAtLeast(0.1f)
    _lux.value = smoothedLux
  }

  override fun onSensorChanged(event: SensorEvent?) {
    if (_isLocked.value) return
    if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
      val rawLux = event.values.firstOrNull() ?: return
      if (rawLux > 0) {
        smoothedLux = if (abs(rawLux - smoothedLux) > 200f) {
          rawLux // Fast transition on drastic light change
        } else {
          smoothedLux + smoothingFactor * (rawLux - smoothedLux)
        }
        _lux.value = smoothedLux
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // No-op
  }
}
