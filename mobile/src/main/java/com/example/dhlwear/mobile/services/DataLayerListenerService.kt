package com.example.dhlwear.mobile.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.dhlwear.mobile.model.PackageStatus
import com.google.android.gms.wearable.*

/**
 * Servicio que maneja las comunicaciones entre el dispositivo móvil y el dispositivo Wear OS.
 * Se ejecuta en segundo plano para procesar eventos de comunicación de la capa de datos.
 */
class DataLayerListenerService : Service(), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {

    private val TAG = "DataLayerService"
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): DataLayerListenerService = this@DataLayerListenerService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        // Registrarse para recibir eventos de datos, mensajes y capacidades
        Wearable.getDataClient(this).addListener(this)
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getCapabilityClient(this).addListener(this, "dhl_wear_capability")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Eliminar los listeners cuando se destruye el servicio
        Wearable.getDataClient(this).removeListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getCapabilityClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                
                when (path) {
                    "/dhlwear/status" -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val statusValue = dataMap.getInt("status")
                        val timestamp = dataMap.getLong("timestamp")
                        val id = dataMap.getString("id") ?: "unknown"
                        
                        val packageStatus = PackageStatus(
                            status = PackageStatus.Status.fromInt(statusValue),
                            timestamp = timestamp,
                            id = id
                        )
                        
                        Log.d(TAG, "Recibido cambio de estado: $packageStatus")
                        
                        // Enviar un broadcast local para que lo reciba la actividad
                        val intent = Intent("com.example.dhlwear.STATUS_UPDATED")
                        intent.putExtra("status", statusValue)
                        intent.putExtra("timestamp", timestamp)
                        intent.putExtra("id", id)
                        sendBroadcast(intent)
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/dhlwear/gesture_confirmed" -> {
                Log.d(TAG, "Gesto de confirmación recibido desde Wear")
                
                // Enviar broadcast para notificar a la actividad
                val intent = Intent("com.example.dhlwear.GESTURE_CONFIRMED")
                sendBroadcast(intent)
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        // Notificar cuando cambian las capacidades de los dispositivos conectados
        Log.d(TAG, "Capacidad cambiada: ${capabilityInfo.name}")
    }
}
