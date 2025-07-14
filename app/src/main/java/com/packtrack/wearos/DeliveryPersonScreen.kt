package com.packtrack.wearos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.material.*
import com.packtrack.wearos.data.DeliveryItem
import com.packtrack.wearos.data.DeliveryStatus
import com.packtrack.wearos.sensors.GestureDetector
import com.packtrack.wearos.services.NotificationService

@Composable
fun DeliveryPersonScreen(navController: NavController) {
    val context = LocalContext.current
    var deliveries by remember { mutableStateOf(getSampleDeliveries()) }
    var selectedDelivery by remember { mutableStateOf<DeliveryItem?>(null) }
    val notificationService = remember { NotificationService(context) }

    // Detector de gestos
    val gestureDetector = remember { GestureDetector() }

    LaunchedEffect(Unit) {
        gestureDetector.initialize(context)
        gestureDetector.startListening { gesture ->
            if (gesture == "WRIST_TWIST" && selectedDelivery != null) {
                // Confirmar entrega con gesto de muñeca
                selectedDelivery?.let { delivery ->
                    deliveries = deliveries.map {
                        if (it.id == delivery.id) {
                            it.copy(status = DeliveryStatus.DELIVERED)
                        } else it
                    }
                    notificationService.showPackageDeliveredNotification(delivery.trackingNumber)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            gestureDetector.stopListening()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        Text(
            text = "Entregas Pendientes",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(deliveries.filter { it.status != DeliveryStatus.DELIVERED }) { delivery ->
                DeliveryCard(
                    delivery = delivery,
                    onConfirm = {
                        selectedDelivery = delivery
                        deliveries = deliveries.map {
                            if (it.id == delivery.id) {
                                it.copy(status = DeliveryStatus.DELIVERED)
                            } else it
                        }
                        notificationService.showPackageDeliveredNotification(delivery.trackingNumber)
                    },
                    onReportIssue = {
                        deliveries = deliveries.map {
                            if (it.id == delivery.id) {
                                it.copy(status = DeliveryStatus.ISSUE_REPORTED)
                            } else it
                        }
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun DeliveryCard(
    delivery: DeliveryItem,
    onConfirm: () -> Unit,
    onReportIssue: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        onClick = TODO()
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = delivery.recipientName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = delivery.address,
                color = Color.Gray,
                fontSize = 10.sp,
                maxLines = 2
            )

            Text(
                text = "Paquete: ${delivery.trackingNumber}",
                color = Color.Gray,
                fontSize = 9.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { onConfirm() },
                    modifier = Modifier.size(32.dp),
                    colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFF4CAF50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirmar",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Button(
                    onClick = { onReportIssue() },
                    modifier = Modifier.size(32.dp),
                    colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFFF44336))
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Reportar",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

fun getSampleDeliveries(): List<DeliveryItem> {
    return listOf(
        DeliveryItem(
            id = "1",
            recipientName = "Juan Pérez",
            address = "Calle 123 #45-67",
            status = DeliveryStatus.PENDING,
            trackingNumber = "PKG001",
            estimatedTime = "14:30"
        ),
        DeliveryItem(
            id = "2",
            recipientName = "María García",
            address = "Av. Principal 890",
            status = DeliveryStatus.PENDING,
            trackingNumber = "PKG002",
            estimatedTime = "15:00"
        ),
        DeliveryItem(
            id = "3",
            recipientName = "Carlos López",
            address = "Carrera 15 #23-45",
            status = DeliveryStatus.PENDING,
            trackingNumber = "PKG003",
            estimatedTime = "15:30"
        )
    )
}
