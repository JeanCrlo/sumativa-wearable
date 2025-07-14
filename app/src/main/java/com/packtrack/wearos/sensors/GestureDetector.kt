package com.packtrack.wearos.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

class GestureDetector : SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    private val _gestureDetected = MutableStateFlow<String?>(null)
    val gestureDetected: StateFlow<String?> = _gestureDetected
    
    private var lastAcceleration = FloatArray(3)
    private var lastGyroscope = FloatArray(3)
    private var gestureCallback: ((String) -> Unit)? = null
    private var lastGestureTime = 0L
    
    fun initialize(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
    
    fun startListening(callback: (String) -> Unit) {
        gestureCallback = callback
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager?.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
    }
    
    fun stopListening() {
        sensorManager?.unregisterListener(this)
        gestureCallback = null
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    handleAccelerometerData(it.values)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    handleGyroscopeData(it.values)
                }
            }
        }
    }
    
    private fun handleAccelerometerData(values: FloatArray) {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        
        // Detectar movimiento brusco (shake)
        val deltaX = abs(x - lastAcceleration[0])
        val deltaY = abs(y - lastAcceleration[1])
        val deltaZ = abs(z - lastAcceleration[2])
        
        if (deltaX > 12 || deltaY > 12 || deltaZ > 12) {
            detectGesture("SHAKE")
        }
        
        lastAcceleration = values.clone()
    }
    
    private fun handleGyroscopeData(values: FloatArray) {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        
        // Detectar giro de muñeca
        if (abs(z) > 2.0f && abs(x) < 1.0f && abs(y) < 1.0f) {
            detectGesture("WRIST_TWIST")
        }
        
        lastGyroscope = values.clone()
    }
    
    private fun detectGesture(gesture: String) {
        val currentTime = System.currentTimeMillis()
        // Evitar detecciones múltiples muy rápidas
        if (currentTime - lastGestureTime > 1000) {
            _gestureDetected.value = gesture
            gestureCallback?.invoke(gesture)
            lastGestureTime = currentTime
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No implementado
    }
}
