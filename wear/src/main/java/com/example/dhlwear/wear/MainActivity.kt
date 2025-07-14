package com.example.dhlwear.wear

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.example.dhlwear.wear.databinding.ActivityMainBinding
import com.example.dhlwear.wear.model.PackageStatus
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Actividad principal para la aplicación Wear OS DHL WEAR.
 * Gestiona la visualización del estado del paquete, detección de gestos y mensajería.
 */
class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener, 
                    MessageClient.OnMessageReceivedListener, SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private var currentStatus = PackageStatus(PackageStatus.Status.PENDING)
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    // Constantes
    private val SHAKE_THRESHOLD = 10.0f
    private val TAG = "WearMainActivity"
    
    // Variables de estado
    private var lastAcceleration = 0.0f
    private var gestureModeActive = false
    private var isConnected = false
    private var connectionCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val CONNECTION_CHECK_INTERVAL = 30000L // 30 segundos
    private var connectionRetryAttempt = 0
    private val MAX_RETRY_ATTEMPTS = 5
    private val BASE_RETRY_DELAY = 5000L // 5 segundos
    private val MAX_RETRY_DELAY = 30000L // 30 segundos
    
    // Variables para la funcionalidad de mensajería
    private val messagesList = mutableListOf<String>()
    private val MAX_MESSAGES = 10
    private var connectedNodes = listOf<Node>() // Lista de dispositivos conectados
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var scrollAnimator: ValueAnimator? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.d(TAG, "onCreate: inicializando la aplicación del reloj")
        
        // Inicializa el sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        // Procesar intent inicial si existe
        intent?.let { handleIntent(it) }
        
        // Configurar la UI inicial
        setupButtons()
        setupMessagingInterface()
        updateStatusUI(currentStatus)
        binding.connectionStatusTextView.text = getString(R.string.connecting)
        
        // Iniciar el proceso de conexión con el teléfono y descubrimiento de dispositivos
        connectToPhone()
        discoverConnectedNodes()
    }
    
    /**
     * Procesa intents que llegan a la actividad
     */
    private fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent: ${intent.action}")
        try {
            if (intent.action == "com.example.dhlwear.wear.STATUS_UPDATE") {
                val statusValue = intent.getIntExtra("status", 0)
                val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
                val id = intent.getStringExtra("id") ?: "unknown"
                
                currentStatus = PackageStatus(
                    status = PackageStatus.Status.fromInt(statusValue),
                    timestamp = timestamp,
                    id = id
                )
                
                updateStatusUI(currentStatus)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando intent: ${e.message}")
        }
    }
    
    /**
     * Actualiza la interfaz de usuario con el estado actual del paquete
     */
    private fun updateStatusUI(status: PackageStatus) {
        Log.d(TAG, "updateStatusUI: Actualizando UI con estado ${status.status}")
        
        // Actualizar color de estado y texto
        val statusText = when (status.status) {
            PackageStatus.Status.PENDING -> getString(R.string.status_pending)
            PackageStatus.Status.IN_TRANSIT -> getString(R.string.status_in_transit)
            PackageStatus.Status.DELIVERED -> getString(R.string.status_delivered)
        }
        
        binding.statusTextView.text = statusText
        
        val colorRes = when (status.status) {
            PackageStatus.Status.PENDING -> R.color.status_pending
            PackageStatus.Status.IN_TRANSIT -> R.color.status_in_transit
            PackageStatus.Status.DELIVERED -> R.color.status_delivered
        }
        
        // Actualizar el color de fondo de la tarjeta de estado
        binding.statusCardView.setCardBackgroundColor(ContextCompat.getColor(this, colorRes))
        
        // Actualizar descripción de acción según el estado
        val actionDesc = when (status.status) {
            PackageStatus.Status.PENDING -> "Esperando recogida"
            PackageStatus.Status.IN_TRANSIT -> "En proceso de entrega"
            PackageStatus.Status.DELIVERED -> "Entregado correctamente"
        }
        binding.actionDescription.text = actionDesc
        
        // Actualizar visibilidad de la instrucción de gesto
        if (status.status == PackageStatus.Status.IN_TRANSIT) {
            binding.gestureInstructionTextView.visibility = View.VISIBLE
        } else {
            binding.gestureInstructionTextView.visibility = View.GONE
        }
    }
    
    /**
     * Fuerza una actualización de la conexión BLE para mejorar el descubrimiento
     */
    private fun forceBleRefresh() {
        try {
            // Usar capacidades como mecanismo para forzar una actualización de BLE
            val capabilityClient = Wearable.getCapabilityClient(this)
            Tasks.await(
                capabilityClient.getAllCapabilities(CapabilityClient.FILTER_ALL),
                500, // Timeout corto
                TimeUnit.MILLISECONDS
            )
            Log.d(TAG, "forceBleRefresh: Actualización BLE solicitada")
        } catch (e: Exception) {
            // Ignorar excepciones, es solo un intento de optimización
            Log.d(TAG, "forceBleRefresh: ${e.message}")
        }
    }

    private fun setupButtons() {
        binding.updateStatusButton.setOnClickListener {
            // Cambia al siguiente estado en la secuencia
            val nextStatus = when (currentStatus.status) {
                PackageStatus.Status.PENDING -> PackageStatus.Status.IN_TRANSIT
                PackageStatus.Status.IN_TRANSIT -> PackageStatus.Status.DELIVERED
                PackageStatus.Status.DELIVERED -> PackageStatus.Status.PENDING
            }
            
            // Actualizar el estado actual
            currentStatus = PackageStatus(nextStatus)
            
            // Actualizar la UI primero
            updateStatusUI(currentStatus)
            
            // Envía el nuevo estado al teléfono y muestra confirmación
            binding.connectionStatusTextView.text = getString(R.string.syncing)
            sendStatusToPhone()
            
            // Si el estado es IN_TRANSIT, activa la detección de gestos
            gestureModeActive = (nextStatus == PackageStatus.Status.IN_TRANSIT)
            binding.gestureInstructionTextView.visibility = 
                if (gestureModeActive) View.VISIBLE else View.GONE
                
            // Mensaje más claro sobre la acción realizada
            val actionMsg = when (nextStatus) {
                PackageStatus.Status.IN_TRANSIT -> "Paquete marcado EN TRÁNSITO"
                PackageStatus.Status.DELIVERED -> "Paquete marcado como ENTREGADO"
                PackageStatus.Status.PENDING -> "Paquete marcado como PENDIENTE"
            }
            Toast.makeText(this, actionMsg, Toast.LENGTH_SHORT).show()
        }
        
        // Configurar el botón de desplazamiento hacia abajo
        binding.scrollDownIndicator.setOnClickListener {
            smoothScrollToMessagingSection()
        }
        
        // Configurar el botón de envío de mensajes
        binding.sendMessageButton.setOnClickListener {
            val message = binding.messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                // Limpiar el campo de texto y ocultar teclado
                binding.messageInput.setText("")
                hideKeyboard(binding.messageInput)
                
                // Enviar el mensaje a los dispositivos conectados
                sendMessageToConnectedDevices(message)
            }
        }
        
        // Configurar el botón de scroll hacia la sección de mensajería
        binding.scrollDownIndicator.setOnClickListener {
            smoothScrollToMessagingSection()
        }
    }
    
    private fun setupMessagingInterface() {
        // Configurar el TextView de mensajes
        binding.messagesListView.movementMethod = ScrollingMovementMethod()
        
        // Configurar el EditText para enviar al presionar Enter
        binding.messageInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                // Obtener el texto del campo
                val message = binding.messageInput.text.toString().trim()
                if (message.isNotEmpty()) {
                    // Limpiar el campo y ocultar teclado
                    binding.messageInput.setText("")
                    hideKeyboard(v)
                    
                    // Enviar el mensaje a los dispositivos conectados
                    sendMessageToConnectedDevices(message)
                }
                true
            } else {
                false
            }
        }
    }
    
    private fun updateMessagesListView() {
        runOnUiThread {
            if (messagesList.isEmpty()) {
                binding.messagesListView.text = getString(R.string.no_messages)
                binding.messagesListView.gravity = android.view.Gravity.CENTER
            } else {
                binding.messagesListView.text = messagesList.joinToString("\n")
                binding.messagesListView.gravity = android.view.Gravity.START
            }
        }
    }
    
    private fun addMessageToList(message: String) {
        // Añadir con timestamp
        val timestamp = dateFormat.format(Date())
        val formattedMessage = "[$timestamp] $message"
        
        // Limitar el tamaño de la lista
        if (messagesList.size >= MAX_MESSAGES) {
            messagesList.removeAt(0)
        }
        
        messagesList.add(formattedMessage)
        updateMessagesListView()
    }
    
    private fun sendMessageToConnectedDevices(message: String) {
        if (connectedNodes.isEmpty()) {
            // Si no hay dispositivos conocidos, actualizamos la lista primero
            Toast.makeText(this, getString(R.string.discovering_devices), Toast.LENGTH_SHORT).show()
            discoverConnectedNodes {
                // Luego intentamos enviar el mensaje
                sendMessageToAvailableNodes(message)
            }
        } else {
            // Si ya tenemos la lista de nodos, enviamos directamente
            sendMessageToAvailableNodes(message)
        }
    }
    
    private fun discoverConnectedNodes(onComplete: (() -> Unit)? = null) {
        Thread {
            try {
                val nodeClient = Wearable.getNodeClient(applicationContext)
                val connectedNodesTask = nodeClient.connectedNodes
                val nodes = Tasks.await(connectedNodesTask, 5, TimeUnit.SECONDS)
                
                // Actualizar la lista de nodos en el hilo principal
                runOnUiThread {
                    if (nodes.isEmpty()) {
                        Log.d(TAG, "No se encontraron dispositivos conectados")
                        Toast.makeText(this, getString(R.string.no_connected_devices), Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d(TAG, "Nodos conectados: ${nodes.size}")
                        connectedNodes = nodes
                        val nodeNames = nodes.joinToString(", ") { it.displayName }
                        Toast.makeText(this, getString(R.string.connected_to, nodeNames), Toast.LENGTH_SHORT).show()
                    }
                    
                    // Si hay una función de callback, la ejecutamos
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al descubrir nodos: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.error_finding_devices), Toast.LENGTH_SHORT).show()
                    onComplete?.invoke() // Llamamos al callback incluso si hay error
                }
            }
        }.start()
    }
    
    private fun sendMessageToAvailableNodes(message: String) {
        if (connectedNodes.isEmpty()) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.no_connected_devices), Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        Thread {
            var successCount = 0
            val timestamp = System.currentTimeMillis()
            val nodeClient = Wearable.getNodeClient(applicationContext)
            val localNodeTask = nodeClient.localNode
            
            try {
                // Obtener el nodo local para incluirlo como origen
                val localNode = Tasks.await(localNodeTask)
                
                // Formatear el mensaje con timestamp y nodo origen
                // formato: texto|timestamp|nodoOrigen
                val formattedMessage = "$message|$timestamp|${localNode.id}"
                
                // Enviar a cada nodo conectado
                for (node in connectedNodes) {
                    try {
                        Wearable.getMessageClient(this).sendMessage(
                            node.id,
                            "/dhlwear/message",
                            formattedMessage.toByteArray()
                        )
                        Log.d(TAG, "Mensaje enviado a ${node.displayName}")
                        successCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al enviar mensaje a ${node.displayName}: ${e.message}")
                    }
                }
                
                // Mostrar confirmación en UI
                runOnUiThread {
                    val resultMessage = when {
                        successCount == 0 -> getString(R.string.message_send_failed)
                        successCount < connectedNodes.size -> getString(R.string.message_sent_partially)
                        else -> getString(R.string.message_sent_successfully)
                    }
                    
                    Toast.makeText(this, resultMessage, Toast.LENGTH_SHORT).show()
                    
                    // Añadir mensaje a nuestra propia lista
                    if (successCount > 0) {
                        addMessageToList(getString(R.string.you) + ": $message")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error general al enviar mensaje: ${e.message}")
                
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.message_send_error), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun smoothScrollToMessagingSection() {
        Log.d(TAG, "smoothScrollToMessagingSection: Desplazando a sección de mensajería")
        val targetView = binding.messagingSection
        
        // Calcular la posición Y de destino
        val targetY = targetView.y.toInt()
        
        // Si ya hay un animator en marcha, lo cancelamos
        scrollAnimator?.cancel()
        
        // Animar el scroll suavemente
        val scrollView = binding.scrollView
        val startScrollY = scrollView.scrollY
        
        scrollAnimator = ValueAnimator.ofInt(startScrollY, targetY).apply {
            duration = 500 // milisegundos
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                scrollView.scrollTo(0, value)
            }
            start()
        }
    }
    
    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    /**
     * Recibe mensajes del teléfono y otros dispositivos conectados
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val data = String(messageEvent.data)
        Log.d(TAG, "onMessageReceived: ${messageEvent.path} desde ${messageEvent.sourceNodeId}, datos: $data")
        
        when (messageEvent.path) {
            "/dhlwear/message" -> {
                // Procesar mensaje de texto recibido con formato texto|timestamp|nodoOrigen
                try {
                    val parts = data.split("|") 
                    
                    if (parts.isNotEmpty()) {
                        val text = parts[0]
                        // Formatear el mensaje con la hora actual
                        val formattedMessage = "${dateFormat.format(Date())}: $text"
                        
                        // Añadir mensaje a la lista y actualizar UI
                        runOnUiThread {
                            addMessageToList(formattedMessage)
                            Toast.makeText(this, R.string.message_received, Toast.LENGTH_SHORT).show()
                            smoothScrollToMessagingSection()
                        }
                        
                        // Enviar confirmación de recepción
                        val confirmData = "received:${System.currentTimeMillis()}".toByteArray()
                        Wearable.getMessageClient(this).sendMessage(
                            messageEvent.sourceNodeId,
                            "/dhlwear/message_received",
                            confirmData
                        )
                        
                        // Actualizar estado de conexión
                        if (!isConnected) {
                            isConnected = true
                            runOnUiThread {
                                binding.connectionStatusTextView.text = getString(R.string.connected)
                                binding.connectionStatusTextView.setTextColor(
                                    ContextCompat.getColor(this, R.color.connected_color)
                                )
                            }
                        }
                    } else {
                        Log.e(TAG, "Formato de mensaje inválido: $data")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al procesar mensaje: ${e.message}")
                }
            }
            "/dhlwear/refresh" -> {
                // El teléfono solicita una actualización completa
                runOnUiThread {
                    binding.connectionStatusTextView.text = getString(R.string.syncing)
                    Toast.makeText(this, "Sincronizando con teléfono...", Toast.LENGTH_SHORT).show()
                }
                
                isConnected = true
                requestInitialStatus()
            }
            "/dhlwear/update_status" -> {
                // El teléfono envía una actualización de estado directa
                runOnUiThread {
                    binding.connectionStatusTextView.text = getString(R.string.syncing)
                }
                
                isConnected = true
                requestInitialStatus()
            }
            "/dhlwear/connection_status" -> {
                val connected = data == "connected"
                isConnected = connected
                
                runOnUiThread {
                    binding.connectionStatusTextView.text = 
                        if (connected) getString(R.string.connected) 
                        else getString(R.string.disconnected)
                        
                    if (connected) {
                        Toast.makeText(this, "Teléfono conectado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            "/dhlwear/ping" -> {
                // Recibimos un ping del teléfono, responder con pong
                isConnected = true
                val sourceNode = messageEvent.sourceNodeId
                
                // Actualizar UI para mostrar conexión activa
                runOnUiThread {
                    binding.connectionStatusTextView.text = getString(R.string.connected)
                    binding.connectionStatusTextView.setTextColor(
                        ContextCompat.getColor(this, R.color.connected_color)
                    )
                }
                
                // Responder al ping
                Thread {
                    try {
                        val pongData = "pong:${System.currentTimeMillis()}".toByteArray()
                        Wearable.getMessageClient(this).sendMessage(
                            sourceNode,
                            "/dhlwear/pong", 
                            pongData
                        )
                        Log.d(TAG, "Pong enviado a $sourceNode")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al enviar pong: ${e.message}")
                    }
                }.start()
            }
            "/dhlwear/confirmation_received" -> {
                // El teléfono confirmó la recepción de un mensaje o acción
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.confirmation_received), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Solicita el estado inicial al teléfono
     */
    private fun requestInitialStatus() {
        Thread {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(this).connectedNodes)
                
                if (nodes.isEmpty()) {
                    Log.d(TAG, "requestInitialStatus: No hay nodos conectados")
                    runOnUiThread {
                        binding.connectionStatusTextView.text = getString(R.string.no_connected_phone)
                    }
                    return@Thread
                }
                
                // Actualizar UI
                runOnUiThread {
                    binding.connectionStatusTextView.text = getString(R.string.requesting)
                }
                
                // Priorizar nodos cercanos
                val nearbyNodes = nodes.filter { it.isNearby }
                val targetNodes = if (nearbyNodes.isNotEmpty()) nearbyNodes else nodes
                
                var success = false
                for (node in targetNodes) {
                    try {
                        // Solicitar actualización de estado
                        Wearable.getMessageClient(this).sendMessage(
                            node.id,
                            "/dhlwear/request_status",
                            "request".toByteArray()
                        )
                        
                        Log.d(TAG, "requestInitialStatus: Solicitud enviada a ${node.id}")
                        success = true
                        break  // Con un nodo es suficiente
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al solicitar estado a ${node.id}: ${e.message}")
                    }
                }
                
                if (!success) {
                    runOnUiThread {
                        binding.connectionStatusTextView.text = getString(R.string.request_failed)
                        Toast.makeText(
                            this,
                            getString(R.string.failed_to_request_status),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error general al solicitar estado: ${e.message}")
            }
        }.start()
    }
    
    // Método para conectarse con el teléfono
    private fun connectToPhone() {
        // Actualizar UI
        runOnUiThread {
            binding.connectionStatusTextView.text = getString(R.string.connecting)
        }
        
        // Descubrir nodos
        discoverConnectedNodes()
    }
    
    // Método para registrar los listeners de comunicación con el teléfono
    private fun registerWearableListeners() {
        try {
            // Registrar listeners de Wearable
            Wearable.getDataClient(this).addListener(this)
            Wearable.getMessageClient(this).addListener(this)
            Log.d(TAG, "registerWearableListeners: listeners registrados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar listeners: ${e.message}")
        }
    }
    
    // Darse de baja de listeners cuando ya no se necesitan
    private fun unregisterWearableListeners() {
        try {
            Wearable.getDataClient(this).removeListener(this)
            Wearable.getMessageClient(this).removeListener(this)
            Log.d(TAG, "unregisterWearableListeners: listeners eliminados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar listeners: ${e.message}")
        }
    }
    
    // Gestión del ciclo de vida
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: actividad en primer plano")
        
        // Asegurar que los listeners están registrados
        registerWearableListeners()
        
        // Verificar estado de conexión
        if (!isConnected) {
            connectToPhone()
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: actividad en segundo plano")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: actividad destruida")
        
        // Liberar recursos
        unregisterWearableListeners()
    }
    
    private fun attemptAlternativeConnection() {
        Log.d(TAG, "Iniciando estrategia alternativa de conexión")
        runOnUiThread {
            binding.connectionStatusTextView.text = getString(R.string.reconnecting)
            Toast.makeText(this, "Intentando reconexión alternativa...", Toast.LENGTH_LONG).show()
        }
        
        // Reiniciar contador de intentos
        connectionRetryAttempt = 0
        
        Thread {
            try {
                // Forzar limpieza de caché de nodos alternativa
                try {
                    // Forzar un rescan de nodos cercanos
                    val capabilityTask = Wearable.getCapabilityClient(this@MainActivity)
                        .getCapability("dhlwear_connection", CapabilityClient.FILTER_REACHABLE)
                    Tasks.await(capabilityTask, 1000, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    // Ignoramos errores aquí, es solo para forzar un refresh
                    Log.d(TAG, "Refresh de capacidades: ${e.message}")
                }
                
                // Pequeña pausa
                Thread.sleep(500)
                
                // Reiniciar listeners
                unregisterWearableListeners()
                Thread.sleep(500)
                registerWearableListeners()
                
                // Intentar conexión nuevamente
                connectToPhone()
            } catch (e: Exception) {
                Log.e(TAG, "Error en estrategia alternativa: ${e.message}")
                // Programar reintento normal
                connectionCheckHandler.postDelayed({ connectToPhone() }, BASE_RETRY_DELAY)
            }
        }.start()
    }
    
    // Eliminado método duplicado requestInitialStatus()
    
    /**
     * Envía un mensaje de ping al teléfono para mantener la conexión activa
     * Actualiza el estado de conexión según el resultado
     * Implementa un sistema más robusto de comunicación con timeouts
     */
    private fun sendPingToPhone() {
        Thread {
            try {
                // Primero, forzar actualización BLE para mejorar descubrimiento
                forceBleRefresh()
                
                // Obtener nodos con timeout para evitar bloqueos largos
                val nodeListTask = Wearable.getNodeClient(this).connectedNodes
                val nodes = try {
                    Tasks.await(nodeListTask, 2, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.w(TAG, "Timeout al buscar nodos para ping: ${e.message}")
                    null
                }
                
                if (nodes == null || nodes.isEmpty()) {
                    Log.d(TAG, "sendPingToPhone: No hay nodos conectados para ping")
                    handleDisconnection(false)
                    return@Thread
                }
                
                var pingSuccess = false
                val nearbyNodes = nodes.filter { it.isNearby }  // Priorizar nodos cercanos
                val targetNodes = if (nearbyNodes.isNotEmpty()) nearbyNodes else nodes
                
                for (node in targetNodes) {
                    try {
                        Log.d(TAG, "Enviando ping a nodo: ${node.displayName} [${node.id}]")
                        
                        // Enviar ping con un payload que incluya información de timestamp
                        val pingData = "ping:${System.currentTimeMillis()}".toByteArray()
                        val sendTask = Wearable.getMessageClient(this).sendMessage(
                            node.id,
                            "/dhlwear/ping",
                            pingData
                        )
                        
                        // Usar timeout para evitar bloqueos
                        Tasks.await(sendTask, 1500, TimeUnit.MILLISECONDS)
                        
                        Log.d(TAG, "sendPingToPhone: Ping enviado a ${node.displayName} [${node.id}]")
                        pingSuccess = true
                        
                        // Actualizar estado de conexión si cambió
                        if (!isConnected) {
                            isConnected = true
                            runOnUiThread {
                                binding.connectionStatusTextView.text = getString(R.string.connected)
                            }
                        }
                        
                        break // Si tenemos éxito con un nodo, no seguimos intentando
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al enviar ping a ${node.displayName} [${node.id}]: ${e.message}")
                    }
                }
                
                // Si ningún ping tuvo éxito, actualizamos el estado de conexión
                if (!pingSuccess && isConnected) {
                    handleDisconnection(true)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error general al enviar ping: ${e.message}")
                // En caso de error en el proceso de ping, indicamos desconexión
                handleDisconnection(true)
            }
        }.start()
    }
    
    /**
     * Gestiona una desconexión detectada
     * @param showError Si es true, muestra mensaje de error en lugar de desconexión
     */
    private fun handleDisconnection(showError: Boolean) {
        // Solo actualizamos si estaba conectado previamente
        if (isConnected) {
            isConnected = false
            runOnUiThread {
                binding.connectionStatusTextView.text = 
                    getString(if (showError) R.string.error else R.string.disconnected)
                
                if (showError) {
                    Toast.makeText(
                        this,
                        "Se perdió la conexión con el teléfono",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            
            // Si detectamos desconexión, intentar reconexión después de un breve retraso
            connectionCheckHandler.postDelayed({
                connectToPhone()
            }, 3000) // 3 segundos de espera
        }
    }
    
    /**
     * Implementación requerida para la interfaz DataClient.OnDataChangedListener
     * Procesa los cambios en DataItems sincronizados desde el teléfono
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            Log.d(TAG, "onDataChanged: recibidos ${dataEvents.count} eventos")
            
            for (event in dataEvents) {
                val uri = event.dataItem.uri
                val path = uri.path
                
                Log.d(TAG, "onDataChanged: evento en path=$path")
                
                when (path) {
                    "/dhlwear/status" -> {
                        if (event.type == DataEvent.TYPE_CHANGED) {
                            val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                            val statusValue = dataMapItem.dataMap.getInt("status")
                            val timestamp = dataMapItem.dataMap.getLong("timestamp")
                            val id = dataMapItem.dataMap.getString("id") ?: "unknown"
                            
                            currentStatus = PackageStatus(
                                status = PackageStatus.Status.fromInt(statusValue),
                                timestamp = timestamp,
                                id = id
                            )
                            
                            runOnUiThread {
                                updateStatusUI(currentStatus)
                                binding.connectionStatusTextView.text = getString(R.string.connected)
                                Toast.makeText(this, "Estado actualizado", Toast.LENGTH_SHORT).show()
                            }
                            
                            isConnected = true
                        }
                    }
                    "/dhlwear/connection" -> {
                        if (event.type == DataEvent.TYPE_CHANGED) {
                            val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                            val connected = dataMapItem.dataMap.getBoolean("connected")
                            
                            isConnected = connected
                            runOnUiThread {
                                binding.connectionStatusTextView.text = 
                                    getString(if (connected) R.string.connected else R.string.disconnected)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando DataEvents: ${e.message}")
        } finally {
            dataEvents.release()
        }
    }
    
    /**
     * Implementación de SensorEventListener para procesar cambios en el acelerómetro
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Calcular la aceleración total
            val acceleration = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            
            // Detectar sacudidas
            if (acceleration > SHAKE_THRESHOLD && abs(acceleration - lastAcceleration) > SHAKE_THRESHOLD / 2) {
                // Detectada una sacudida fuerte
                Log.d(TAG, "onSensorChanged: Sacudida detectada: $acceleration")
                
                // Procesar el gesto de sacudida solo si hay suficiente diferencia con la última
                // y no estamos en modo gesto activo
                if (!gestureModeActive) {
                    gestureModeActive = true
                    handleShakeGesture()
                    
                    // Desactivar el modo gesto después de un tiempo
                    connectionCheckHandler.postDelayed({ gestureModeActive = false }, 1500)
                }
            }
            
            lastAcceleration = acceleration
        }
    }
    
    /**
     * Implementación requerida por la interfaz SensorEventListener
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere acción específica para cambios de precisión
    }
    
    /**
     * Procesa el gesto de sacudida
     */
    private fun handleShakeGesture() {
        Log.d(TAG, "handleShakeGesture: Procesando gesto de sacudida")
        runOnUiThread {
            // Mostrar feedback visual
            Toast.makeText(this, getString(R.string.updating_status), Toast.LENGTH_SHORT).show()
            
            // Solicitar actualización al teléfono
            requestInitialStatus()
        }
    }
    
    /**
     * Actualiza el estado en el teléfono
     */
    private fun sendStatusToPhone() {
        Thread {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(this).connectedNodes)
                
                if (nodes.isEmpty()) {
                    Log.d(TAG, "sendStatusToPhone: No hay nodos conectados")
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.no_connected_phone), Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }
                
                // Crear datos a enviar
                val dataMap = DataMap().apply {
                    putInt("status", currentStatus.status.value)
                    putLong("timestamp", System.currentTimeMillis())
                    putString("id", currentStatus.id)
                    putString("source", "wear")
                }
                
                val request = PutDataMapRequest.create("/dhlwear/status_update").apply {
                    dataMap.putAll(dataMap)
                    setUrgent()
                }
                
                val putDataReq = request.asPutDataRequest()
                val result = Tasks.await(Wearable.getDataClient(this).putDataItem(putDataReq))
                
                Log.d(TAG, "sendStatusToPhone: Datos enviados correctamente: ${result.uri}")
                
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.status_sent), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar estado: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.failed_to_send_status), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    // Método eliminado: getStatusButton()
    
    /**
     * Método para rastrear dispositivos conectados
     */
    private fun no_connected_devices() = connectedNodes.isEmpty()
    
    /**
     * Método para verificar si un dispositivo está conectado por ID
     */
    private fun connected_to(nodeId: String) = connectedNodes.any { it.id == nodeId }
    
    /**
     * Métodos para rastrear errores de mensajería
     */
    private fun error_finding_devices(e: Exception) {
        Log.e(TAG, "Error encontrando dispositivos: ${e.message}")
    }
    
    /**
     * Monitoreo de estado de envío de mensajes
     */
    private fun message_sent_failed() = false
    private fun message_sent_partially() = false
    private fun message_sent_successfully() = true
    private fun message_send_error(e: Exception): Boolean {
        Log.e(TAG, "Error enviando mensaje: ${e.message}")
        return false
    }
    
    /**
     * Dispositivo solicitando estado
     */
    private fun requesting_status() {
        // Solicitar estado actual al teléfono
        requestInitialStatus()
    }
    
    /**
     * Verificar si una petición ha fallado
     */
    private fun request_failed() = false
    private fun failed_to_request_status() = false
    
    // Las referencias directas a través de binding son suficientes
}
