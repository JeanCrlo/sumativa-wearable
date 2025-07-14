package com.packtrack.wearos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import com.packtrack.wearos.data.PackageTracking
import com.packtrack.wearos.data.DeliveryStatus
import com.packtrack.wearos.services.NotificationService

@Composable
fun RecipientScreen(navController: NavController) {
    val context = LocalContext.current
    var packages by remember { mutableStateOf(getSamplePackages()) }
    var showRating by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<PackageTracking?>(null) }
    val notificationService = remember { NotificationService(context) }

    Column(
        modifier = ModifierZ
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        Text(
            text = "Mis Paquetes",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (showRating && selectedPackage != null) {
            RatingScreen(
                packageItem = selectedPackage!!,
                onRatingComplete = { rating ->
                    packages = packages.map {
                        if (it.id == selectedPackage!!.id) {
                            it.copy(rating = rating, status = DeliveryStatus.COMPLETED)
                        } else it
                    }
                    showRating = false
                    selectedPackage = null
                }
            )
        } else {
            LazyColumn {
                items(packages) { packageItem ->
                    PackageCard(
                        packageItem = packageItem,
                        onConfirmReceived = {
                            selectedPackage = packageItem
                            showRating = true
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun PackageCard(
    packageItem: PackageTracking,
    onConfirmReceived: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "Paquete #${packageItem.trackingNumber}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Estado: ${getStatusText(packageItem.status)}",
                color = getStatusColor(packageItem.status),
                fontSize = 10.sp
            )

            Text(
                text = "Repartidor: ${packageItem.deliveryPerson}",
                color = Color.Gray,
                fontSize = 10.sp
            )

            Text(
                text = "Hora estimada: ${packageItem.estimatedTime}",
                color = Color.Gray,
                fontSize = 9.sp
            )

            if (packageItem.status == DeliveryStatus.DELIVERED && packageItem.rating == 0) {
                Button(
                    onClick = { onConfirmReceived() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFF4CAF50))
                ) {
                    Text(
                        text = "Confirmar Recepción",
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }

            if (packageItem.rating > 0) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Calificación: ",
                        color = Color.Gray,
                        fontSize = 9.sp
                    )
                    repeat(packageItem.rating) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Estrella",
                            tint = Color.Yellow,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RatingScreen(
    packageItem: PackageTracking,
    onRatingComplete: (Int) -> Unit
) {
    var rating by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Califica la entrega",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Paquete #${packageItem.trackingNumber}",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(5) { index ->
                Button(
                    onClick = { rating = index + 1 },
                    modifier = Modifier.size(32.dp),
                    colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color.Transparent)
                ) {
                    Icon(
                        imageVector = if (index < rating) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Estrella ${index + 1}",
                        tint = Color.Yellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onRatingComplete(rating) },
            enabled = rating > 0,
            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFF2196F3))
        ) {
            Text(
                text = "Enviar",
                fontSize = 12.sp,
                color = Color.White
            )
        }
    }
}

fun getSamplePackages(): List<PackageTracking> {
    return listOf(
        PackageTracking(
            id = "1",
            trackingNumber = "PKG001",
            status = DeliveryStatus.DELIVERED,
            deliveryPerson = "Ana Rodríguez",
            estimatedTime = "14:30"
        ),
        PackageTracking(
            id = "2",
            trackingNumber = "PKG002",
            status = DeliveryStatus.IN_TRANSIT,
            deliveryPerson = "Luis Martín",
            estimatedTime = "15:45"
        ),
        PackageTracking(
            id = "3",
            trackingNumber = "PKG003",
            status = DeliveryStatus.PENDING,
            deliveryPerson = "Carmen Silva",
            estimatedTime = "16:20"
        )
    )
}

fun getStatusText(status: DeliveryStatus): String {
    return when (status) {
        DeliveryStatus.PENDING -> "Pendiente"
        DeliveryStatus.IN_TRANSIT -> "En camino"
        DeliveryStatus.DELIVERED -> "Entregado"
        DeliveryStatus.COMPLETED -> "Completado"
        DeliveryStatus.ISSUE_REPORTED -> "Incidencia"
    }
}

fun getStatusColor(status: DeliveryStatus): Color {
    return when (status) {
        DeliveryStatus.PENDING -> Color.Yellow
        DeliveryStatus.IN_TRANSIT -> Color.Blue
        DeliveryStatus.DELIVERED -> Color.Green
        DeliveryStatus.COMPLETED -> Color.Green
        DeliveryStatus.ISSUE_REPORTED -> Color.Red
    }
}
