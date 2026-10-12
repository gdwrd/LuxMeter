package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import com.example.sensor.LightSensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LightSensorManagerTest {
  @Test
  fun `a single on-change event immediately reports the new stable light level`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sensors = shadowOf(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    val light = ShadowSensor.newInstance(Sensor.TYPE_LIGHT)
    sensors.addSensor(light)
    val manager = LightSensorManager(context)
    try {
      assertTrue(manager.start())
      sensors.sendSensorEventToListeners(SensorEventBuilder.newBuilder()
        .setSensor(light).setValues(floatArrayOf(100f)).build())
      assertEquals(100f, manager.readings.value!!, 0f)

      sensors.sendSensorEventToListeners(SensorEventBuilder.newBuilder()
        .setSensor(light).setValues(floatArrayOf(10f)).build())
      assertEquals(10f, manager.readings.value!!, 0f)
    } finally {
      manager.stop()
    }
  }
}
