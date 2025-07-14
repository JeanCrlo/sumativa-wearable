package com.packtrack.wearos.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.packtrack.wearos.MainActivity
import com.packtrack.wearos.data.NotificationData
import com.packtrack.wearos.data.NotificationType

class NotificationService(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "packtrack_notifications"
        private const val CHANNEL_NAME = "PackTrack Notifications"
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de PackTrack Pro"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showNotification(notificationData: NotificationData) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(getNotificationIcon(notificationData.type))
            .setContentTitle(notificationData.title)
            .setContentText(notificationData.message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationData.id.hashCode(), notification)
            }
        } catch (e: SecurityException) {
            // Manejar caso donde no se tienen permisos de notificación
            e.printStackTrace()
        }
    }
    
    private fun getNotificationIcon(type: NotificationType): Int {
        return when (type) {
            NotificationType.DELIVERY_ASSIGNED -> android.R.drawable.ic_menu_send
            NotificationType.PACKAGE_IN_TRANSIT -> android.R.drawable.ic_menu_directions
            NotificationType.PACKAGE_DELIVERED -> android.R.drawable.ic_menu_agenda
            NotificationType.ISSUE_REPORTED -> android.R.drawable.ic_dialog_alert
            NotificationType.RATING_REQUEST -> android.R.drawable.ic_menu_preferences
        }
    }
    
    fun showDeliveryAssignedNotification(trackingNumber: String, address: String) {
        val notification = NotificationData(
            id = "delivery_$trackingNumber",
            title = "Nueva entrega asignada",
            message = "Paquete $trackingNumber - $address",
            type = NotificationType.DELIVERY_ASSIGNED
        )
        showNotification(notification)
    }
    
    fun showPackageInTransitNotification(trackingNumber: String) {
        val notification = NotificationData(
            id = "transit_$trackingNumber",
            title = "Paquete en camino",
            message = "Tu paquete $trackingNumber está siendo entregado",
            type = NotificationType.PACKAGE_IN_TRANSIT
        )
        showNotification(notification)
    }
    
    fun showPackageDeliveredNotification(trackingNumber: String) {
        val notification = NotificationData(
            id = "delivered_$trackingNumber",
            title = "Paquete entregado",
            message = "Paquete $trackingNumber ha sido entregado",
            type = NotificationType.PACKAGE_DELIVERED
        )
        showNotification(notification)
    }
    
    fun showIssueReportedNotification(trackingNumber: String) {
        val notification = NotificationData(
            id = "issue_$trackingNumber",
            title = "Incidencia reportada",
            message = "Se reportó una incidencia con el paquete $trackingNumber",
            type = NotificationType.ISSUE_REPORTED
        )
        showNotification(notification)
    }
    
    fun showRatingRequestNotification(trackingNumber: String) {
        val notification = NotificationData(
            id = "rating_$trackingNumber",
            title = "Califica tu entrega",
            message = "Por favor califica la entrega del paquete $trackingNumber",
            type = NotificationType.RATING_REQUEST
        )
        showNotification(notification)
    }
}
