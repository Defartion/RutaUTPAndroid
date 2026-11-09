package com.example.rutautpnative.ui.components

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.AppPrimary
import com.example.rutautpnative.ui.theme.BodyMdMedium
import com.example.rutautpnative.ui.theme.OnSurface
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

//----Selector de foto cámara/galería (compartido)----
// Antes duplicado en PublicarComunidadSheet y CarneDigitalScreen.
// Galería = Photo Picker (sin permiso). Cámara = TakePicturePreview (miniatura,
// sin FileProvider) + permiso CAMERA con Accompanist.

//----Fila de opción del selector de foto----
@Composable
fun OpcionFoto(icono: ImageVector, texto: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Icon(icono, null, tint = AppPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(texto, style = BodyMdMedium, color = OnSurface)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberSelectorFoto(
    titulo: String = L.t("Añadir foto", "Add photo"),
    onFoto: (Bitmap?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    var mostrarDialogo by remember { mutableStateOf(false) }
    var pendienteCamara by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val galeriaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Decodifica en background: una foto a resolucion completa puede
            // tardar 100-500 ms (jank visible en main thread si se hace inline).
            scope.launch {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                    } catch (e: Exception) {
                        null
                    }
                }
                onFoto(bitmap)
            }
        }
    }
    val camaraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> if (bitmap != null) onFoto(bitmap) }

    val permisoCamara = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(permisoCamara.status.isGranted) {
        if (permisoCamara.status.isGranted && pendienteCamara) {
            pendienteCamara = false
            camaraLauncher.launch(null)
        } else if (!permisoCamara.status.isGranted && pendienteCamara) {
            // El usuario acaba de rechazar el permiso desde el diálogo del
            // sistema: aviso breve, sin dejar el silencio sin explicación.
            pendienteCamara = false
            Toast.makeText(
                context,
                L.t("Necesitas dar acceso a la cámara para tomar una foto.", "We need camera access to take a photo."),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            title = { Text(titulo) },
            text = {
                Column {
                    OpcionFoto(Icons.Filled.AddAPhoto, L.t("Tomar foto", "Take photo")) {
                        mostrarDialogo = false
                        if (permisoCamara.status.isGranted) {
                            camaraLauncher.launch(null)
                        } else {
                            pendienteCamara = true
                            permisoCamara.launchPermissionRequest()
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OpcionFoto(Icons.Filled.PhotoLibrary, L.t("Elegir de galería", "Choose from gallery")) {
                        mostrarDialogo = false
                        galeriaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    return { mostrarDialogo = true }
}
