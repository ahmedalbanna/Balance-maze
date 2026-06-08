package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Tracks the device core tilt with precision using Android gravity/accelerometer
 * and responsiveness using the gyroscope fused with a complementary filter.
 */
class TiltSensorTracker(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _pitch = MutableStateFlow(0f) // rotation axis X
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _roll = MutableStateFlow(0f)  // rotation axis Y
    val roll: StateFlow<Float> = _roll.asStateFlow()

    private var timestamp: Long = 0
    private var isCalibrated = false

    private var filterPitch = 0f
    private var filterRoll = 0f
    private val alpha = 0.90f // Complementary factor: high gyro preference

    fun startTracking() {
        isCalibrated = false
        timestamp = 0L
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                // Pitch & Roll estimate from gravity vector
                val accPitch = atan2(-ay, sqrt(ax * ax + az * az))
                val accRoll = atan2(ax, az)

                if (!isCalibrated) {
                    filterPitch = accPitch
                    filterRoll = accRoll
                    isCalibrated = true
                } else {
                    // Complementary corrective blend
                    filterPitch = (1f - alpha) * accPitch + alpha * filterPitch
                    filterRoll = (1f - alpha) * accRoll + alpha * filterRoll
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                if (timestamp != 0L) {
                    val dt = (event.timestamp - timestamp) * 1.0e-9f // convert nanoseconds to seconds
                    val gyroX = event.values[0] // angular velocity around X
                    val gyroY = event.values[1] // angular velocity around Y

                    if (isCalibrated) {
                        // Integrate gyroscope rate
                        filterPitch += gyroX * dt
                        filterRoll += gyroY * dt
                    }
                }
                timestamp = event.timestamp
            }
        }

        // Restrict maximum physics impact to make rolling controllable (approx 35 degrees)
        val limit = 0.61f
        _pitch.value = filterPitch.coerceIn(-limit, limit)
        _roll.value = filterRoll.coerceIn(-limit, limit)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
