package com.example.rutautpnative.ui.theme

import androidx.compose.ui.graphics.Color

//----Paleta oscura compartida (antes duplicada en NavegacionScreen, TrackingDemoScreen y EscanerQRScreen)----
// Las pantallas de demo/navegación usan su propio tema oscuro FUERZADO (como
// el iOS que fuerza .dark en esas pantallas); estos tokens centralizan los
// colores que antes estaban hardcodeados en 3 archivos distintos.

// Fondos
val PanelOscuro = Color(0xFF141414)       // fondo principal de paneles oscuros
val PanelOscuroItem = Color(0xFF26292C)   // chips, toggles, pistas de progreso
val PanelOscuroDialogo = Color(0xFF1D2022) // dialogos y overlays

// Texto
val TextoOscuro = Color(0xFFE4E8EA)       // texto principal sobre oscuro
val TextoOscuroVariante = Color(0xFF9AA0A3) // texto secundario sobre oscuro

// Estados
val VerdeExito = Color(0xFF43A047)        // exito, conectado, GPS on
val NaranjaAdvertencia = Color(0xFFFB8C00) // fuera de ruta, conectando
val RojoPeligro = Color(0xFFE53935)        // finalizar, error, bandera fin
val AzulDemo = Color(0xFF1E88E5)          // badges demo, usuario simulado
