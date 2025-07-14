package com.example.dhlwear.mobile.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.dhlwear.mobile.MainActivity
import com.example.dhlwear.mobile.R
import com.example.dhlwear.mobile.model.PackageStatus
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

/**
 * Servicio para escuchar y procesar eventos de comunicación desde el dispositivo Wear OS.
 * Maneja actualizaciones de estado y muestra notificaciones cuando sea necesario.
 */
class WearableListenerService : com.google.android.gms.wearable.WearableListenerService() {

    private val NOTIFICATION_CHANNEL_ID = "dhl_wear_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            android.util.Log.d("MobileService", "onDataChanged: recibido evento de datos")
            
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val path = event.dataItem.uri.path
                    android.util.Log.d("MobileService", "Path recibido: $path")
                    
                    if (path == "/dhlwear/status") {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val statusValue = dataMapItem.dataMap.getInt("status")
                        val timestamp = dataMapItem.dataMap.getLong("timestamp")
                        val id = dataMapItem.dataMap.getString("id") ?: "unknown"
                        
                        android.util.Log.d("MobileService", "Datos recibidos: status=$statusValue, timestamp=$timestamp")
                        
                        // Procesa el cambio de estado
                        val status = PackageStatus.Status.fromInt(statusValue)
                        
                        // Muestra una notificación al usuario para estados relevantes
                        when (status) {
                            PackageStatus.Status.IN_TRANSIT -> showNotification(getString(R.string.status_in_transit))
                            PackageStatus.Status.DELIVERED -> showNotification(getString(R.string.status_delivered))
                            else -> {} // No hacemos nada para otros estados
                        }
                        
                        // Enviar mensaje de vuelta para confirmar recepción
                        android.util.Log.d("MobileService", "Enviando confirmación de recepción")
                        sendAcknowledgement()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MobileService", "Error en onDataChanged: ${e.message}")
        } finally {
            // Siempre liberar los recursos
            dataEvents.release()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        try {
            val path = messageEvent.path
            val data = if (messageEvent.data.isNotEmpty()) String(messageEvent.data) else ""
            android.util.Log.d("MobileService", "Mensaje recibido: $path, datos: $data")
            
            when (path) {
                "/dhlwear/gesture_confirmed" -> {
                    // Muestra una notificación de que el paquete ha sido entregado
                    showNotification(getString(R.string.status_delivered))
                    
                    // Enviar un broadcast a la MainActivity para actualizar la UI
                    val intent = Intent("com.example.dhlwear.mobile.STATUS_UPDATED")
                    intent.putExtra("status", PackageStatus.Status.DELIVERED.value)
                    sendBroadcast(intent)
                }
                
                "/dhlwear/request_status" -> {
                    // El reloj solicita una actualización de estado
                    android.util.Log.d("MobileService", "Solicitud de actualización recibida")
                    
                    // Enviar un broadcast a la MainActivity para que reenvíe el estado actual
                    val intent = Intent("com.example.dhlwear.mobile.SEND_STATUS")
                    sendBroadcast(intent)
                    
                    // Además, responder directamente con los datos actuales si los tenemos en cache
                    Thread {
                        try {
                            // Obtener el estado actual de las preferencias
                            val prefs = getSharedPreferences("dhlwear_prefs", Context.MODE_PRIVATE)
                            val currentStatusValue = prefs.getInt("current_status", 0)
                            val currentTimestamp = prefs.getLong("current_timestamp", System.currentTimeMillis())
                            val currentId = prefs.getString("current_id", "unknown") ?: "unknown"
                            
                            android.util.Log.d("MobileService", "Enviando datos almacenados: status=$currentStatusValue, timestamp=$currentTimestamp")
                            
                            // Enviar datos directamente al reloj
                            val sourceNodeId = messageEvent.sourceNodeId
                            
                            // Crear mapa de datos
                            val dataClient = Wearable.getDataClient(this)
                            val request = com.google.android.gms.wearable.PutDataMapRequest.create("/dhlwear/status")
                            request.dataMap.putInt("status", currentStatusValue)
                            request.dataMap.putLong("timestamp", currentTimestamp)
                            request.dataMap.putString("id", currentId)
                            
                            // Enviar al DataLayer
                            val putDataTask = dataClient.putDataItem(request.asPutDataRequest().setUrgent())
                            Tasks.await(putDataTask)
                            android.util.Log.d("MobileService", "Datos enviados al DataLayer")
                            
                            // Enviar mensaje de confirmación
                            val sendTask = Wearable.getMessageClient(this).sendMessage(
                                sourceNodeId,
                                "/dhlwear/status_update",
                                "updated".toByteArray()
                            )
                            Tasks.await(sendTask)
                            android.util.Log.d("MobileService", "Mensaje de confirmación enviado")
                        } catch (e: Exception) {
                            android.util.Log.e("MobileService", "Error al responder a la solicitud de estado: ${e.message}")
                        }
                    }.start()
                }
                
                "/dhlwear/ping" -> {
                    // El reloj envía un ping para mantener la conexión activa
                    android.util.Log.d("MobileService", "Ping recibido del reloj")
                    
                    // Responder al ping para confirmar conexión
                    Thread {
                        try {
                            // Obtener el nodo origen
                            val sourceNodeId = messageEvent.sourceNodeId
                            
                            // Responder al mismo nodo
                            val sendTask = Wearable.getMessageClient(this).sendMessage(
                                sourceNodeId,
                                "/dhlwear/pong",
                                "pong".toByteArray()
                            )
                            Tasks.await(sendTask)
                            android.util.Log.d("MobileService", "Pong enviado a $sourceNodeId")
                        } catch (e: Exception) {
                            android.util.Log.e("MobileService", "Error al responder al ping: ${e.message}")
                        }
                    }.start()
                }
                
                "/dhlwear/receipt_confirmation" -> {
                    // El reloj confirma que recibió correctamente una actualización
                    android.util.Log.d("MobileService", "Confirmación de recepción recibida")
                    
                    // Podemos actualizar algún estado interno o enviar una notificación si es necesario
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MobileService", "Error en onMessageReceived: ${e.message}")
        }
    }
    
    /**
     * Envía un mensaje de confirmación a todos los nodos conectados
     */
    private fun sendAcknowledgement() {
        Thread {
            try {
                // Obtener nodos conectados
                val nodeListTask = Wearable.getNodeClient(this).connectedNodes
                val nodes = Tasks.await(nodeListTask)
                
                if (nodes.isEmpty()) {
                    android.util.Log.w("MobileService", "No hay dispositivos conectados para enviar confirmación")
                    return@Thread
                }
                
                // Enviar mensaje de confirmación a cada nodo
                for (node in nodes) {
                    val sendTask = Wearable.getMessageClient(this).sendMessage(
                        node.id, 
                        "/dhlwear/ack", 
                        byteArrayOf()
                    )
                    Tasks.await(sendTask)
                    android.util.Log.d("MobileService", "Confirmación enviada al nodo: ${node.displayName}")
                }
            } catch (e: Exception) {
                android.util.Log.e("MobileService", "Error al enviar confirmación: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Crea y muestra una notificación con el estado actualizado del paquete.
     */
    private fun showNotification(statusText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Intent para abrir la aplicación cuando se toque la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        // Construir la notificación
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.dhl_logo)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.status_updated, statusText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Mostrar la notificación
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Crea un canal de notificación (requerido para Android 8.0 y superior).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "DHL WEAR Notificaciones"
            val descriptionText = "Notificaciones de estado de paquetes DHL"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Registra el canal con el sistema
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
