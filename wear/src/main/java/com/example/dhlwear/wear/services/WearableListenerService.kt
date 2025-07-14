package com.example.dhlwear.wear.services

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.dhlwear.wear.MainActivity
import com.example.dhlwear.wear.model.PackageStatus
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

/**
 * Servicio para escuchar y procesar eventos de comunicación desde el teléfono móvil.
 * Maneja actualizaciones de estado y las propaga a la actividad principal.
 */
class WearableListenerService : com.google.android.gms.wearable.WearableListenerService() {

    private val TAG = "WearWearableService"
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WearableListenerService creado")
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            Log.d(TAG, "onDataChanged: recibido nuevo evento de datos")
            
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val path = event.dataItem.uri.path
                    Log.d(TAG, "Path recibido: $path")
                    
                    if (path == "/dhlwear/status") {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val statusValue = dataMapItem.dataMap.getInt("status")
                        val timestamp = dataMapItem.dataMap.getLong("timestamp")
                        val id = dataMapItem.dataMap.getString("id") ?: "unknown"
                        
                        Log.d(TAG, "Datos recibidos: status=$statusValue, timestamp=$timestamp, id=$id")
                        
                        // Crea un nuevo objeto de estado
                        val packageStatus = PackageStatus(
                            status = PackageStatus.Status.fromInt(statusValue),
                            timestamp = timestamp,
                            id = id
                        )
                        
                        // Enviar un intent a la actividad principal
                        updateMainActivity(statusValue, timestamp, id)
                        
                        // Muestra un Toast con el nuevo estado
                        showToast("Estado actualizado: ${packageStatus.status}")
                        
                        // Enviar confirmación de recepción al teléfono
                        sendReceiptConfirmation(id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en onDataChanged: ${e.message}")
        } finally {
            // Siempre liberar los recursos
            dataEvents.release()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        try {
            val path = messageEvent.path
            val data = if (messageEvent.data.isNotEmpty()) String(messageEvent.data) else ""
            Log.d(TAG, "onMessageReceived: $path, data: $data")
            
            when (path) {
                "/dhlwear/refresh" -> {
                    // Solicitar una actualización de datos completa al DataLayer
                    Log.d(TAG, "Mensaje de refresco recibido, solicitando datos actuales")
                    showToast("Sincronizando con teléfono...")
                    
                    // Notificar a la actividad para actualizar la UI
                    Intent("com.example.dhlwear.wear.STATUS_UPDATED").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("ACTION", "REQUEST_UPDATE")
                        startActivity(this)
                    }
                }
                
                "/dhlwear/connect" -> {
                    // Inicia la actividad principal al recibir un mensaje de conexión
                    Log.d(TAG, "Mensaje de conexión recibido")
                    showToast("Teléfono conectado")
                    
                    updateConnectionStatus(true)
                    
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(this)
                    }
                }
                
                "/dhlwear/ack" -> {
                    // Confirmación recibida del teléfono
                    Log.d(TAG, "Confirmación recibida del teléfono")
                    updateConnectionStatus(true)
                }
                
                "/dhlwear/update_status" -> {
                    // Solicitud de actualización directa desde el teléfono
                    Log.d(TAG, "Solicitud de actualización de estado recibida")
                    showToast("Actualizando estado...")
                    
                    // Buscar datos actualizados en el DataLayer
                    queryDataLayer()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en onMessageReceived: ${e.message}")
        }
    }
    
    /**
     * Envia un intent para actualizar la actividad principal con el nuevo estado
     */
    private fun updateMainActivity(statusValue: Int, timestamp: Long, id: String) {
        val intent = Intent("com.example.dhlwear.wear.STATUS_UPDATED").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("STATUS_VALUE", statusValue)
            putExtra("TIMESTAMP", timestamp)
            putExtra("ID", id)
            putExtra("ACTION", "UPDATE_STATUS")
        }
        
        try {
            startActivity(intent)
            Log.d(TAG, "Intent enviado para actualizar MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar intent a MainActivity: ${e.message}")
        }
    }
    
    /**
     * Actualiza el estado de conexión en la actividad principal
     */
    private fun updateConnectionStatus(connected: Boolean) {
        val intent = Intent("com.example.dhlwear.wear.STATUS_UPDATED").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("ACTION", "CONNECTION_STATUS")
            putExtra("CONNECTED", connected)
        }
        
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar estado de conexión: ${e.message}")
        }
    }
    
    /**
     * Envía confirmación de recepción al teléfono
     */
    private fun sendReceiptConfirmation(id: String) {
        Thread {
            try {
                val nodeListTask = Wearable.getNodeClient(this).connectedNodes
                val nodes = Tasks.await(nodeListTask)
                
                if (nodes.isEmpty()) {
                    Log.d(TAG, "No hay nodos conectados para enviar confirmación")
                    return@Thread
                }
                
                for (node in nodes) {
                    val sendTask = Wearable.getMessageClient(this).sendMessage(
                        node.id,
                        "/dhlwear/receipt_confirmation",
                        id.toByteArray()
                    )
                    Tasks.await(sendTask)
                    Log.d(TAG, "Confirmación enviada al nodo: ${node.id}")
                }
                
                // Actualizar estado de conexión
                updateConnectionStatus(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar confirmación: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Consulta el DataLayer para obtener los últimos datos
     */
    private fun queryDataLayer() {
        Thread {
            try {
                val dataItemTask = Wearable.getDataClient(this).getDataItems(
                    android.net.Uri.Builder()
                        .scheme("wear")
                        .path("/dhlwear/status")
                        .build()
                )
                
                val dataItems = Tasks.await(dataItemTask)
                if (dataItems.count > 0) {
                    val dataItem = dataItems[0]
                    val dataMapItem = DataMapItem.fromDataItem(dataItem)
                    val statusValue = dataMapItem.dataMap.getInt("status")
                    val timestamp = dataMapItem.dataMap.getLong("timestamp")
                    val id = dataMapItem.dataMap.getString("id") ?: "unknown"
                    
                    updateMainActivity(statusValue, timestamp, id)
                } else {
                    Log.d(TAG, "No se encontraron datos en el DataLayer")
                    showToast("No se encontraron datos actualizados")
                }
                
                dataItems.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error al consultar DataLayer: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Muestra un toast en el hilo principal
     */
    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
