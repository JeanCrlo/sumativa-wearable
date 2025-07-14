package com.packtrack.wearos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.packtrack.wearos.ui.theme.PackTrackTheme
import com.packtrack.wearos.services.NotificationService

class MainActivity : ComponentActivity() {
    private lateinit var notificationService: NotificationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationService = NotificationService(this)

        setContent {
            PackTrackTheme {
                PackTrackApp()
            }
        }
    }
}

@Composable
fun PackTrackApp() {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onDeliveryPersonClick = { navController.navigate("delivery_person") },
                onRecipientClick = { navController.navigate("recipient") }
            )
        }
        composable("delivery_person") {
            DeliveryPersonScreen(navController = navController)
        }
        composable("recipient") {
            RecipientScreen(navController = navController)
        }
    }
}

@Composable
fun HomeScreen(
    onDeliveryPersonClick: () -> Unit,
    onRecipientClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PackTrack Pro",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Botón Repartidor
        Button(
            onClick = onDeliveryPersonClick,
            modifier = Modifier
                .size(80.dp)
                .padding(4.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.LocalShipping,
                contentDescription = "Repartidor",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = "Repartidor",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Botón Receptor
        Button(
            onClick = onRecipientClick,
            modifier = Modifier
                .size(80.dp)
                .padding(4.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Receptor",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = "Receptor",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
