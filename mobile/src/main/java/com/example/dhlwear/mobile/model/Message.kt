package com.example.dhlwear.mobile.model

/**
 * Modelo para representar mensajes entre el teléfono y el reloj
 */
data class Message(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromWatch: Boolean = false,
    val senderNodeId: String = ""
) {
    // Obtenemos formato amigable para hora
    fun getFormattedTime(): String {
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(timestamp))
    }
}
