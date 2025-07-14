package com.example.dhlwear.wear.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.widget.Toast
import com.example.dhlwear.wear.R
import com.example.dhlwear.wear.model.PackageStatus
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlin.math.abs

/**
 * Servicio que monitorea el acelerómetro para detectar gestos de confirmación de entrega.
 * Permite que la detección de gestos continúe incluso cuando la aplicación está en segundo plano.
 */
class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    // Umbral de aceleración para detectar el gesto
    private val SHAKE_THRESHOLD = 12.0f
    private var lastAcceleration = 0.0f
    
    // Estado del paquete
    private var currentStatus = PackageStatus(PackageStatus.Status.IN_TRANSIT)
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializa el sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        // Registra el sensor
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Extrae el estado actual del paquete si se proporciona
        intent?.let {
            val statusValue = it.getIntExtra("STATUS_VALUE", 0)
            currentStatus = PackageStatus(PackageStatus.Status.fromInt(statusValue))
        }
        
        // Notificamos al usuario que el servicio está activo
        Toast.makeText(this, getString(R.string.gesture_instruction), Toast.LENGTH_SHORT).show()
        
        // El servicio se reinicia automáticamente si se termina
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // No se permite enlazar con este servicio
    }

    // Procesamiento de eventos de sensor
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && 
            currentStatus.status == PackageStatus.Status.IN_TRANSIT) {
            
            // Calcular la aceleración total
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val acceleration = abs(x + y + z - lastAcceleration)
            lastAcceleration = x + y + z
            
            // Si detectamos un giro fuerte, confirmamos la entrega
            if (acceleration > SHAKE_THRESHOLD) {
                Toast.makeText(this, getString(R.string.gesture_detected), Toast.LENGTH_SHORT).show()
                
                // Actualizamos el estado a DELIVERED
                currentStatus = PackageStatus(PackageStatus.Status.DELIVERED)
                
                // Enviamos el estado actualizado al teléfono
                sendStatusToPhone(currentStatus)
                
                // Enviamos un mensaje de confirmación de gesto
                sendGestureConfirmation()
                
                // Detener el servicio ya que hemos confirmado la entrega
                stopSelf()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // No necesitamos manejar cambios de precisión
    }

    // Envía el estado actualizado al teléfono
    private fun sendStatusToPhone(packageStatus: PackageStatus) {
        val dataClient = Wearable.getDataClient(this)
        val request = PutDataMapRequest.create("/dhlwear/status").apply {
            dataMap.putInt("status", packageStatus.status.value)
            dataMap.putLong("timestamp", packageStatus.timestamp)
            dataMap.putString("id", packageStatus.id)
        }.asPutDataRequest().setUrgent()
        
        dataClient.putDataItem(request)
    }

    // Envía un mensaje de confirmación de gesto al teléfono
    private fun sendGestureConfirmation() {
        Wearable.getMessageClient(this).sendMessage(
            "*", // Enviamos a todos los nodos conectados
            "/dhlwear/gesture_confirmed",
            ByteArray(0) // No necesitamos enviar datos adicionales
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Elimina el registro del sensor cuando el servicio se destruye
        sensorManager.unregisterListener(this)
    }
}
