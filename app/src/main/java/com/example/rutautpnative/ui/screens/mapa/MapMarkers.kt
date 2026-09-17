package com.example.rutautpnative.ui.screens.mapa

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.ui.theme.*

// Marcador del bus
@Composable
fun BusMarker(linea: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "bus_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(AppPrimary.copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(AppPrimary),
        )
    }
}

// Marcador del usuario
@Composable
fun PulsingUserMarker() {
    val infiniteTransition = rememberInfiniteTransition(label = "user_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(SecondaryContainer.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Secondary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.DirectionsWalk,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

// Marcador de utp
@Composable
fun MarcadorUTP() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(AppPrimary)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("UTP Trujillo", style = LabelCapsSm, color = Color.White)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.School, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}