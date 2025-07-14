package com.packtrack.wearos.services

import android.content.Context
import com.google.android.gms.wearable.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.packtrack.wearos.data.DeliveryItem
import com.packtrack.wearos.data.PackageTracking

class MobileConnectionService(private val context: Context) : 
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {
    
    companion object {
        private const val DELIVERY_DATA_PATH = "/delivery_data"
        private const val PACKAGE_DATA_PATH = "/package_data"
        private const val SYNC_REQUEST_PATH = "/sync_request"
        private const val STATUS_UPDATE_PATH = "/status_update"
    }
    
    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(context) }
    
    fun startListening() {
        dataClient.addListener(this)
        messageClient.addListener(this)
    }
    
    fun stopListening() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
    }
    
    suspend fun syncDeliveryData(deliveries: List<DeliveryItem>) {
        try {
            val json = Json.encodeToString(deliveries)
            val putDataRequest = PutDataMapRequest.create(DELIVERY_DATA_PATH).apply {
                dataMap.putString("deliveries", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest()
            
            dataClient.putDataItem(putDataRequest).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun syncPackageData(packages: List<PackageTracking>) {
        try {
            val json = Json.encodeToString(packages)
            val putDataRequest = PutDataMapRequest.create(PACKAGE_DATA_PATH).apply {
                dataMap.putString("packages", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest()
            
            dataClient.putDataItem(putDataRequest).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun sendStatusUpdate(trackingNumber: String, status: String) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            val message = "$trackingNumber:$status"
            nodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    STATUS_UPDATE_PATH,
                    message.toByteArray()
                ).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun requestSyncFromMobile() {
        try {
            val nodes = nodeClient.connectedNodes.await()
            nodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    SYNC_REQUEST_PATH,
                    "sync_request".toByteArray()
                ).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            when (event.type) {
                DataEvent.TYPE_CHANGED -> {
                    when (event.dataItem.uri.path) {
                        DELIVERY_DATA_PATH -> {
                            handleDeliveryDataUpdate(event.dataItem)
                        }
                        PACKAGE_DATA_PATH -> {
                            handlePackageDataUpdate(event.dataItem)
                        }
                    }
                }
            }
        }
    }
    
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            SYNC_REQUEST_PATH -> {
                handleSyncRequest()
            }
            STATUS_UPDATE_PATH -> {
                val message = String(messageEvent.data)
                handleStatusUpdate(message)
            }
        }
    }
    
    private fun handleDeliveryDataUpdate(dataItem: DataItem) {
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
        val deliveriesJson = dataMap.getString("deliveries")
        // Procesar datos de entregas actualizados
        deliveriesJson?.let {
            try {
                val deliveries = Json.decodeFromString<List<DeliveryItem>>(it)
                // Actualizar datos locales
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun handlePackageDataUpdate(dataItem: DataItem) {
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
        val packagesJson = dataMap.getString("packages")
        // Procesar datos de paquetes actualizados
        packagesJson?.let {
            try {
                val packages = Json.decodeFromString<List<PackageTracking>>(it)
                // Actualizar datos locales
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun handleSyncRequest() {
        // Implementar lógica de sincronización
        // Enviar datos actuales al dispositivo móvil
    }
    
    private fun handleStatusUpdate(message: String) {
        val parts = message.split(":")
        if (parts.size == 2) {
            val trackingNumber = parts[0]
            val status = parts[1]
            // Actualizar estado local del paquete
        }
    }
}
