package com.example.rutautpnative.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

@Composable
internal fun PerfilIdentidad(nombre: String, rol: String, foto: Bitmap?, carnetGuardado: Boolean, onEditar: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = SuperficieAgrupada) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Perfil", "Profile"), style = BodyMdMedium, color = OnSurfaceVariant,
                    modifier = Modifier.weight(1f).semantics { heading() })
                FilledTonalIconButton(onClick = onEditar,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SurfaceContainerLow, contentColor = AcentoInterfaz)) {
                    Icon(Icons.Filled.Edit, L.t("Editar perfil", "Edit profile"))
                }
            }
            BoxWithConstraints {
                if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AvatarPerfil(nombre, foto)
                        DatosIdentidad(nombre, rol, carnetGuardado)
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AvatarPerfil(nombre, foto)
                    Box(Modifier.weight(1f)) { DatosIdentidad(nombre, rol, carnetGuardado) }
                }
            }
        }
    }
}

@Composable
private fun AvatarPerfil(nombre: String, foto: Bitmap?) {
    val iniciales = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).map { it.first() }.joinToString("")
    Box(Modifier.size(72.dp).clip(CircleShape).background(SurfaceContainerLow), contentAlignment = Alignment.Center) {
        foto?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Text(iniciales, style = HeadlineMd, color = AcentoInterfaz)
    }
}

@Composable
private fun DatosIdentidad(nombre: String, rol: String, carnetGuardado: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(nombre, style = HeadlineMd, color = OnSurface)
        Text(rol, style = BodyXs, color = OnSurfaceVariant)
        if (carnetGuardado) Text(L.t("✓ Carné guardado", "✓ Card saved"), style = BodyXs, color = AcentoInterfaz)
    }
}
