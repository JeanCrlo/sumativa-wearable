package com.example.dhlwear.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dhlwear.mobile.adapters.MessageAdapter
import com.example.dhlwear.mobile.databinding.ActivityMainBinding
import com.example.dhlwear.mobile.model.Message
import com.example.dhlwear.mobile.model.PackageStatus
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.UUID

/**
 * Actividad principal para la aplicación móvil DHL WEAR.
 * Gestiona la visualización del estado del paquete y la interacción con el usuario.
 */
class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {

    private lateinit var binding: ActivityMainBinding
    private var currentStatus = PackageStatus(PackageStatus.Status.PENDING)
    private var lastConnectedNode: String? = null
    private var connectionChecker: Handler? = null
    private var isConnectionChecking = false
    
    // Variables para la mensajería
    private val messages = mutableListOf<Message>()
    private lateinit var messageAdapter: MessageAdapter
    private val MAX_MESSAGES = 50 // Límite de mensajes para evitar problemas de rendimiento
    
    companion object {
        private const val CHANNEL_ID = "dhl_delivery_notifications"
        private const val CONNECTION_CHECK_INTERVAL = 15000L // 15 segundos
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Crear canal de notificaciones (necesario en Android 8.0 y superior)
        createNotificationChannel()
        
        // Inicializar adaptador de mensajes
        setupMessaging()
        
        // Configurar botones y actualizar UI
        setupButtons()
        updateStatusUI(currentStatus)
        updatePackageDetails()
        
        // Preparar la sección de valoración para animación
        binding.ratingContainer.alpha = 0f
        binding.ratingContainer.translationY = 100f
        binding.ratingContainer.visibility = View.GONE
    }
    
    /**
     * Crea el canal de notificaciones requerido para Android 8.0 y superior
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificaciones de entrega DHL"
            val descriptionText = "Notificaciones sobre cambios en el estado de entrega de paquetes"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            
            // Registrar el canal con el sistema
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d("MainActivity", "Canal de notificaciones creado: $CHANNEL_ID")
        }
    }

    /**
     * Configura la funcionalidad de mensajería
     */
    private fun setupMessaging() {
        // Inicializar adaptador
        messageAdapter = MessageAdapter(this, messages)
        binding.messagesListView.adapter = messageAdapter
        
        // Mostrar mensaje de "no hay mensajes" si la lista está vacía
        updateEmptyMessagesView()
        
        // Configurar evento de envío de mensaje
        binding.sendMessageButton.setOnClickListener {
            val messageText = binding.messageInput.text.toString().trim()
            
            if (messageText.isNotEmpty()) {
                // Enviar mensaje al reloj
                sendMessageToWear(messageText)
                
                // Limpiar campo de entrada
                binding.messageInput.text.clear()
                
                // Ocultar teclado
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.messageInput.windowToken, 0)
                
                // Hacer scroll al final de la lista de mensajes
                binding.messagesListView.setSelection(messages.size - 1)
            }
        }
        
        // Auto-scroll cuando el teclado aparece
        binding.messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && messages.isNotEmpty()) {
                binding.messagesListView.postDelayed({
                    binding.messagesListView.setSelection(messages.size - 1)
                }, 200)
            }
        }
    }
    
    /**
     * Actualiza la vista de "no hay mensajes" según sea necesario
     */
    private fun updateEmptyMessagesView() {
        if (messages.isEmpty()) {
            binding.emptyMessagesView.visibility = View.VISIBLE
        } else {
            binding.emptyMessagesView.visibility = View.GONE
        }
    }
    
    /**
     * Añade un mensaje a la lista y actualiza la UI
     */
    private fun addMessage(message: Message) {
        // Limitar el tamaño de la lista para evitar problemas de memoria
        if (messages.size >= MAX_MESSAGES) {
            messages.removeAt(0)
        }
        
        // Añadir mensaje a la lista
        messages.add(message)
        
        // Actualizar adaptador
        runOnUiThread {
            messageAdapter.notifyDataSetChanged()
            // Hacer scroll al último mensaje
            binding.messagesListView.setSelection(messages.size - 1)
            // Actualizar vista de mensajes vacíos
            updateEmptyMessagesView()
        }
    }
    
    /**
     * Envía un mensaje de texto a los relojes conectados
     */
    private fun sendMessageToWear(text: String) {
        val message = Message(text)
        
        // Añadir a nuestra lista local
        addMessage(message)
        
        // Buscar dispositivos conectados
        findWearableDevices { nodes ->
            if (nodes.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this, "No hay relojes conectados para enviar el mensaje", Toast.LENGTH_SHORT).show()
                }
                return@findWearableDevices
            }
            
            // Formatear el mensaje: texto|timestamp|nodoOrigen
            val messageData = "${message.text}|${message.timestamp}|${Wearable.getNodeClient(this).localNode}".toByteArray()
            
            // Enviar a todos los nodos
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(
                    node.id,
                    "/dhlwear/message",
                    messageData
                ).addOnSuccessListener {
                    Log.d("MainActivity", "Mensaje enviado a ${node.displayName}")
                }.addOnFailureListener { e ->
                    Log.e("MainActivity", "Error enviando mensaje a ${node.displayName}: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Actualiza los detalles del paquete en la UI
     */
    private fun updatePackageDetails() {
        binding.packageIdTextView.text = currentStatus.id
        // Estos valores son de ejemplo, en una aplicación real vendrían de una API
        binding.originTextView.text = "Madrid, España"
        binding.destinationTextView.text = "Barcelona, España"
        binding.courierTextView.text = "Carlos Rodríguez"
    }
    
    private fun setupButtons() {
        // Botón para conectar con el dispositivo Wear OS
        binding.connectButton.setOnClickListener {
            binding.connectionStatusTextView.text = getString(R.string.connecting)
            binding.connectionStatusTextView.setTextColor(getColor(R.color.status_in_transit))
            
            // Intentar conectar con el reloj
            binding.connectButton.isEnabled = false
            binding.connectButton.text = "Conectando..."
            
            // Buscar nodos conectados (relojes)
            findWearableDevices { connectedNodes ->
                runOnUiThread {
                    if (connectedNodes.isNotEmpty()) {
                        binding.connectionStatusTextView.text = getString(R.string.connected)
                        binding.connectionStatusTextView.setTextColor(getColor(R.color.status_delivered))
                        binding.connectButton.text = "Sincronizar"
                        binding.connectButton.isEnabled = true
                        sendInitialStatus(connectedNodes)
                        
                        // Mostramos instrucciones claras
                        Toast.makeText(this, "${connectedNodes.size} relojes conectados. Estado sincronizado.", Toast.LENGTH_LONG).show()
                    } else {
                        binding.connectionStatusTextView.text = getString(R.string.disconnected)
                        binding.connectionStatusTextView.setTextColor(getColor(R.color.status_pending))
                        binding.connectButton.text = "Intentar nuevamente"
                        binding.connectButton.isEnabled = true
                        
                        Toast.makeText(this, "No se encontraron relojes conectados. Verifique que el reloj esté emparejado y la aplicación instalada.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        // Botón para actualizar el estado del paquete
        binding.updateStatusButton.setOnClickListener {
            val nextStatus = when (currentStatus.status) {
                PackageStatus.Status.PENDING -> PackageStatus.Status.IN_TRANSIT
                PackageStatus.Status.IN_TRANSIT -> PackageStatus.Status.DELIVERED
                PackageStatus.Status.DELIVERED -> PackageStatus.Status.PENDING
            }
            
            currentStatus = PackageStatus(nextStatus)
            updateStatusUI(currentStatus)
            
            // Enviar al reloj
            sendStatusToWear(currentStatus)
            
            // Mensaje claro sobre lo que sucede
            val actionMsg = when (nextStatus) {
                PackageStatus.Status.PENDING -> "Paquete marcado como PENDIENTE. El repartidor será notificado."
                PackageStatus.Status.IN_TRANSIT -> "Paquete EN TRÁNSITO. Notificando al repartidor para iniciar la entrega."
                PackageStatus.Status.DELIVERED -> "Paquete marcado como ENTREGADO. El sistema notificará al destinatario."
            }
            Toast.makeText(this, actionMsg, Toast.LENGTH_SHORT).show()
        }

        // Botón para confirmar recepción
        binding.confirmReceiptButton.setOnClickListener {
            binding.confirmReceiptButton.visibility = View.GONE
            binding.ratingContainer.visibility = View.VISIBLE
        }

        // Botón para enviar valoración
        binding.submitRatingButton.setOnClickListener {
            val rating = binding.ratingBar.rating
            Toast.makeText(this, "Gracias por tu valoración: $rating estrellas", Toast.LENGTH_SHORT).show()
            binding.ratingContainer.visibility = View.GONE
        }
    }

    private fun updateStatusUI(packageStatus: PackageStatus) {
        val statusText: String
        val statusColor: Int
        
        when (packageStatus.status) {
            PackageStatus.Status.PENDING -> {
                statusText = getString(R.string.status_pending)
                statusColor = getColor(R.color.status_pending)
                // Actualizar texto del botón para el siguiente paso lógico
                binding.updateStatusButton.text = "Iniciar entrega"
                // Ocultar botón de confirmación cuando el paquete está pendiente
                binding.confirmReceiptButton.visibility = View.GONE
            }
            PackageStatus.Status.IN_TRANSIT -> {
                statusText = getString(R.string.status_in_transit)
                statusColor = getColor(R.color.status_in_transit)
                // Actualizar texto del botón para el siguiente paso lógico
                binding.updateStatusButton.text = "Marcar como entregado"
                // Ocultar botón de confirmación cuando el paquete está en camino
                binding.confirmReceiptButton.visibility = View.GONE
            }
            PackageStatus.Status.DELIVERED -> {
                statusText = getString(R.string.status_delivered)
                statusColor = getColor(R.color.status_delivered)
                // Actualizar texto del botón para el siguiente paso lógico
                binding.updateStatusButton.text = "Reiniciar seguimiento"
                // Mostrar botón de confirmación cuando el paquete está entregado
                binding.confirmReceiptButton.visibility = View.VISIBLE
            }
        }
        
        // Actualizar estado visual
        binding.statusTextView.text = statusText
        binding.statusTextView.setTextColor(statusColor)
        
        // Actualizar fondo con color relacionado al estado
        binding.statusTextView.setBackgroundColor(getColor(R.color.light_gray))
        
        // Actualiza la fecha y hora de la última actualización
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        binding.lastUpdateTextView.text = "Última actualización: ${dateFormat.format(Date(packageStatus.timestamp))}"
    }
    
    // Busca dispositivos Wear OS conectados
    private fun findWearableDevices(callback: (List<Node>) -> Unit) {
        val nodeClient = Wearable.getNodeClient(this)
        
        // Ejecutamos en un hilo secundario para no bloquear la UI
        Thread {
            try {
                // Buscar nodos conectados
                val connectedNodesTask = nodeClient.connectedNodes
                val nodes = Tasks.await(connectedNodesTask, 5, TimeUnit.SECONDS)
                callback(nodes)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error buscando dispositivos Wear: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "Error al conectar con Wear OS: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                callback(emptyList())
            }
        }.start()
    }
    
    // Envía el estado inicial a los dispositivos Wear OS conectados
    private fun sendInitialStatus(nodes: List<Node> = emptyList()) {
        // Envía datos mediante DataLayer (para sincronización persistente)
        val dataClient = Wearable.getDataClient(this)
        val request = PutDataMapRequest.create("/dhlwear/status").apply {
            dataMap.putInt("status", currentStatus.status.value)
            dataMap.putLong("timestamp", currentStatus.timestamp)
            dataMap.putString("id", currentStatus.id)
        }.asPutDataRequest().setUrgent()
        
        dataClient.putDataItem(request)
            .addOnSuccessListener { 
                Log.d("MainActivity", "DataItem enviado correctamente")
                Toast.makeText(this, "Estado sincronizado con ${nodes.size} relojes", Toast.LENGTH_SHORT).show() 
            }
            .addOnFailureListener { e -> 
                Log.e("MainActivity", "Error al enviar DataItem: ${e.message}")
                Toast.makeText(this, "Error al sincronizar estado: ${e.message}", Toast.LENGTH_SHORT).show() 
            }
            
        // También enviamos un mensaje directo para actualización inmediata
        if (nodes.isNotEmpty()) {
            val messageClient = Wearable.getMessageClient(this)
            nodes.forEach { node ->
                messageClient.sendMessage(
                    node.id, 
                    "/dhlwear/refresh",
                    byteArrayOf() // No necesitamos datos adicionales, sólo forzar actualización
                ).addOnSuccessListener {
                    Log.d("MainActivity", "Mensaje de refresco enviado a ${node.displayName}")
                }.addOnFailureListener { e ->
                    Log.e("MainActivity", "Error enviando mensaje a ${node.displayName}: ${e.message}")
                }
            }
        }
    }
    
    // Envía el estado actualizado a los relojes conectados
    private fun sendStatusToWear(packageStatus: PackageStatus) {
        // Primero buscamos los nodos conectados
        findWearableDevices { nodes ->
            if (nodes.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this, "No hay relojes conectados para sincronizar", Toast.LENGTH_SHORT).show()
                    binding.connectionStatusTextView.text = getString(R.string.disconnected)
                }
                return@findWearableDevices
            }
            
            // Enviamos datos por DataLayer
            val dataClient = Wearable.getDataClient(this)
            val request = PutDataMapRequest.create("/dhlwear/status").apply {
                dataMap.putInt("status", packageStatus.status.value)
                dataMap.putLong("timestamp", packageStatus.timestamp)
                dataMap.putString("id", packageStatus.id)
            }.asPutDataRequest().setUrgent()
            
            dataClient.putDataItem(request)
                .addOnSuccessListener { 
                    runOnUiThread {
                        Toast.makeText(this, "Estado actualizado en ${nodes.size} relojes", Toast.LENGTH_SHORT).show()
                        binding.connectionStatusTextView.text = getString(R.string.sync_complete)
                    }
                }
                .addOnFailureListener { e -> 
                    runOnUiThread {
                        Toast.makeText(this, "Error al sincronizar: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.connectionStatusTextView.text = getString(R.string.sync_failed)
                    }
                }
                
            // Enviamos mensajes directos para actualización inmediata
            val messageClient = Wearable.getMessageClient(this)
            nodes.forEach { node ->
                messageClient.sendMessage(
                    node.id, 
                    "/dhlwear/refresh",
                    byteArrayOf() // No necesitamos datos adicionales
                )
            }
        }
    }

    // Registra los listeners para la comunicación con Wear OS
    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getCapabilityClient(this).addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        
        // Iniciar verificación periódica de conexión
        startConnectionChecking()
    }

    // Elimina los listeners cuando la actividad está en segundo plano
    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getCapabilityClient(this).removeListener(this)
        
        // Detener verificación periódica de conexión
        stopConnectionChecking()
    }
    
    /**
     * Detecta cambios en las capacidades de los dispositivos conectados
     */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val nodes = capabilityInfo.nodes
        
        if (nodes.isNotEmpty()) {
            // Guardar el último nodo conectado para facilitar la comunicación
            val connectedNode = nodes.first()
            lastConnectedNode = connectedNode.id
            
            runOnUiThread {
                binding.watchConnectionStatus.setImageResource(R.drawable.ic_watch_connected)
                binding.watchConnectionStatus.setColorFilter(getColor(R.color.status_delivered))
                binding.connectionStatusTextView.text = getString(R.string.connected)
                binding.connectionStatusTextView.setTextColor(getColor(R.color.status_delivered))
            }
            
            // Enviar estado actual al reloj conectado
            sendStatusToWear(currentStatus)
        } else {
            lastConnectedNode = null
            runOnUiThread {
                binding.watchConnectionStatus.setImageResource(R.drawable.ic_watch_disconnected)
                binding.watchConnectionStatus.setColorFilter(getColor(R.color.status_pending))
                binding.connectionStatusTextView.text = getString(R.string.disconnected)
                binding.connectionStatusTextView.setTextColor(getColor(R.color.status_pending))
            }
        }
    }
    
    /**
     * Inicia la verificación periódica de la conexión con el reloj
     */
    private fun startConnectionChecking() {
        if (isConnectionChecking) return
        
        isConnectionChecking = true
        connectionChecker = Handler(Looper.getMainLooper())
        
        val pingRunnable = object : Runnable {
            override fun run() {
                // Buscar dispositivos conectados y enviar ping
                findWearableDevices { nodes ->
                    if (nodes.isNotEmpty()) {
                        for (node in nodes) {
                            sendPingToWear(node.id)
                        }
                    } else {
                        // Si no hay dispositivos, actualizar UI
                        runOnUiThread {
                            binding.watchConnectionStatus.setImageResource(R.drawable.ic_watch_disconnected)
                            binding.watchConnectionStatus.setColorFilter(getColor(R.color.status_pending))
                            binding.connectionStatusTextView.text = getString(R.string.disconnected)
                            binding.connectionStatusTextView.setTextColor(getColor(R.color.status_pending))
                        }
                    }
                }
                
                // Programar la siguiente verificación
                connectionChecker?.postDelayed(this, CONNECTION_CHECK_INTERVAL)
            }
        }
        
        // Iniciar la primera verificación
        connectionChecker?.post(pingRunnable)
    }
    
    /**
     * Detiene la verificación periódica de la conexión con el reloj
     */
    private fun stopConnectionChecking() {
        isConnectionChecking = false
        connectionChecker?.removeCallbacksAndMessages(null)
        connectionChecker = null
    }
    
    /**
     * Envía un ping al dispositivo Wear OS para verificar la conexión
     */
    private fun sendPingToWear(nodeId: String) {
        Thread {
            try {
                val pingData = "ping:${System.currentTimeMillis()}".toByteArray()
                Wearable.getMessageClient(this).sendMessage(
                    nodeId,
                    "/dhlwear/ping",
                    pingData
                )
                Log.d("MainActivity", "Ping enviado a $nodeId")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al enviar ping: ${e.message}")
            }
        }.start()
    }

    // Recibe actualizaciones de datos desde el reloj
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED && 
                    event.dataItem.uri.path?.equals("/dhlwear/status") == true) {
                    
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val statusValue = dataMapItem.dataMap.getInt("status")
                    val timestamp = dataMapItem.dataMap.getLong("timestamp")
                    val id = dataMapItem.dataMap.getString("id") ?: "unknown"
                    
                    currentStatus = PackageStatus(
                        status = PackageStatus.Status.fromInt(statusValue),
                        timestamp = timestamp,
                        id = id
                    )
                    
                    runOnUiThread { updateStatusUI(currentStatus) }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al procesar datos: ${e.message}")
        } finally {
            dataEvents.release() // Importante: liberar los recursos
        }
    }

    // Recibe mensajes desde el reloj
    override fun onMessageReceived(messageEvent: MessageEvent) {
        try {
            Log.d("MainActivity", "Mensaje recibido: ${messageEvent.path} desde ${messageEvent.sourceNodeId}")
            
            when (messageEvent.path) {
                "/dhlwear/message" -> {
                    // Procesar mensaje de texto recibido con formato texto|timestamp|nodoOrigen
                    val messageStr = String(messageEvent.data)
                    val parts = messageStr.split("|") 
                    
                    if (parts.size >= 2) {
                        val text = parts[0]
                        val timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                        
                        // Crear y añadir mensaje a la lista
                        val message = Message(
                            text = text,
                            timestamp = timestamp,
                            isFromWatch = true,
                            senderNodeId = messageEvent.sourceNodeId
                        )
                        
                        addMessage(message)
                        
                        // Enviar confirmación de recepción
                        val confirmData = "received:${System.currentTimeMillis()}".toByteArray()
                        Wearable.getMessageClient(this).sendMessage(
                            messageEvent.sourceNodeId,
                            "/dhlwear/message_received",
                            confirmData
                        )
                    }
                }
                
                "/dhlwear/gesture_confirmed" -> {
                    // Si recibimos confirmación del gesto desde el reloj, extraemos la información
                    val messageData = String(messageEvent.data)
                    Log.d("MainActivity", "Datos de confirmación recibidos: $messageData")
                    
                    // Añadir mensaje informativo
                    addMessage(Message("Se detectó un gesto de confirmación en el reloj", isFromWatch = true))
                    
                    // Actualizar el estado a DELIVERED y enviar confirmación de recepción
                    currentStatus = PackageStatus(PackageStatus.Status.DELIVERED)
                    
                    // Enviar confirmación de recepción al reloj
                    Thread {
                        try {
                            val confirmMessage = "received:${System.currentTimeMillis()}".toByteArray()
                            Wearable.getMessageClient(this).sendMessage(
                                messageEvent.sourceNodeId,
                                "/dhlwear/confirmation_received",
                                confirmMessage
                            )
                            Log.d("MainActivity", "Confirmación enviada al reloj")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error al enviar confirmación: ${e.message}")
                        }
                    }.start()
                    
                    // Actualizar la UI con animación y efectos visuales
                    runOnUiThread { 
                        // Mostrar un indicador de actualización
                        binding.deliveryStatusContainer.alpha = 0.5f
                        binding.progressIndicator.visibility = View.VISIBLE
                        
                        // Animación para actualizar el estado
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Actualizar la UI
                            updateStatusUI(currentStatus)
                            
                            // Animar el cambio
                            binding.deliveryStatusContainer.animate()
                                .alpha(1.0f)
                                .setDuration(500)
                                .start()
                            binding.progressIndicator.visibility = View.GONE
                            
                            // Mostrar notificación
                            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle("¡Entrega confirmada!")
                                .setContentText("El paquete ha sido entregado mediante confirmación por gesto")
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setAutoCancel(true)
                                .build()
                                
                            NotificationManagerCompat.from(this).notify(1, notification)
                            
                            // Toast más informativo
                            Toast.makeText(
                                this, 
                                "Entrega confirmada por el repartidor mediante gesto", 
                                Toast.LENGTH_LONG
                            ).show()
                            
                            // Mostrar sección de valoración
                        binding.ratingContainer.visibility = View.VISIBLE
                        binding.ratingContainer.animate()
                            .translationY(0f)
                            .alpha(1.0f)
                            .setDuration(800)
                            .start()
                        }, 500) // Delay para mejor efecto visual
                    }
                }
                
                "/dhlwear/ping" -> {
                    // Responder al ping para mantener la conexión activa
                    val sourceNode = messageEvent.sourceNodeId
                    val pingData = String(messageEvent.data)
                    Log.d("MainActivity", "Ping recibido desde $sourceNode: $pingData")
                    
                    // Enviar respuesta pong
                    Thread {
                        try {
                            val pongData = "pong:${System.currentTimeMillis()}".toByteArray()
                            Wearable.getMessageClient(this).sendMessage(
                                sourceNode,
                                "/dhlwear/pong",
                                pongData
                            )
                            Log.d("MainActivity", "Pong enviado a $sourceNode")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error al enviar pong: ${e.message}")
                        }
                    }.start()
                    
                    // Actualizar indicador de conexión en la UI
                    runOnUiThread {
                        binding.watchConnectionStatus.setImageResource(R.drawable.ic_watch_connected)
                        binding.watchConnectionStatus.setColorFilter(getColor(R.color.status_delivered))
                        binding.connectionStatusTextView.text = getString(R.string.connected)
                        binding.connectionStatusTextView.setTextColor(getColor(R.color.status_delivered))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al procesar mensaje: ${e.message}")
        }
    }
}
