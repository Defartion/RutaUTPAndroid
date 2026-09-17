package com.example.rutautpnative

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.theme.*

@Composable
fun MapaPlaceholderScreen(router: AppRouter) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Map,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Mapa", style = HeadlineMd, color = AppPrimary)
            Text("Se implementa en la Fase 3", style = BodyMd, color = OnSurfaceVariant)
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNavBar(router)
        }
    }
}