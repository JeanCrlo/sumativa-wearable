package com.example.dhlwear.wear.model

/**
 * Modelo que representa el estado de un paquete en la aplicación DHL WEAR.
 * Se usa tanto en la aplicación móvil como en la de Wear OS.
 */
data class PackageStatus(
    val status: Status,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = "package-${timestamp}"
) {
    enum class Status(val value: Int) {
        PENDING(0),
        IN_TRANSIT(1),
        DELIVERED(2);
        
        companion object {
            fun fromInt(value: Int) = values().find { it.value == value } ?: PENDING
        }
    }
}
