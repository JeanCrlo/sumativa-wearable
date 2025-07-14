package com.packtrack.wearos.data

import kotlinx.serialization.Serializable

@Serializable
data class DeliveryItem(
    val id: String,
    val recipientName: String,
    val address: String,
    val status: DeliveryStatus,
    val trackingNumber: String,
    val estimatedTime: String? = null
)

@Serializable
data class PackageTracking(
    val id: String,
    val trackingNumber: String,
    val status: DeliveryStatus,
    val deliveryPerson: String,
    val estimatedTime: String,
    val rating: Int = 0
)

@Serializable
enum class DeliveryStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    ISSUE_REPORTED
}

@Serializable
data class NotificationData(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class NotificationType {
    DELIVERY_ASSIGNED,
    PACKAGE_IN_TRANSIT,
    PACKAGE_DELIVERED,
    ISSUE_REPORTED,
    RATING_REQUEST
}
