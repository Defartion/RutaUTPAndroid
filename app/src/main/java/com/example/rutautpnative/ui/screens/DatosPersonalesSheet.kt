package com.example.rutautpnative.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.PreferenciasPerfilStore
import com.example.rutautpnative.data.Parentesco
import com.example.rutautpnative.data.imagenes.ProfileImageStore
import com.example.rutautpnative.ui.components.EditorEncuadre
import com.example.rutautpnative.ui.components.OpcionFoto
import com.example.rutautpnative.ui.components.rememberSelectorFoto
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

//----Datos personales (puerto del DatosPersonalesSheet.swift)----
// Ficha institucional de solo lectura, telefono y correo personales
// editables (persisten), contacto de emergencia y foto de perfil con
// editor de encuadre. Se abre desde el drawer (avatar con lapiz) y desde
// Perfil -> Editar perfil.

// Datos institucionales (demo, solo lectura — igual que iOS).
private const val NOMBRES_COMPLETOS = "Joaquín Díaz"
private const val CARRERA = "Ingeniería de Software"
private const val CORREO_INSTITUCIONAL = "joaquin.diaz@utp.edu.pe"
private const val CODIGO_UTP = "1234567"
private const val DNI = "76543210"
private const val MODALIDAD = "Presencial"
private const val CAMPUS = "Trujillo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatosPersonalesSheet(onCerrar: () -> Unit) {
    val contexto = LocalContext.current

    // Foto de perfil persistida (fuente unica: ProfileImageStore).
    var fotoPerfil by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ProfileImageStore.revision) {
        fotoPerfil = ProfileImageStore.cargar(contexto)
    }
    var bitmapParaEncuadre by remember { mutableStateOf<Bitmap?>(null) }
    val abrirSelectorFoto = rememberSelectorFoto(titulo = L.t("Foto de perfil", "Profile photo")) {
        bitmapParaEncuadre = it
    }
    var mostrarFuenteFoto by remember { mutableStateOf(false) }

    // Datos personales editables (espejos persistentes del store).
    var editandoDatos by remember { mutableStateOf(false) }
    var telefonoInput by remember { mutableStateOf(PreferenciasPerfilStore.telefono) }
    var correoInput by remember { mutableStateOf(PreferenciasPerfilStore.correoPersonal) }

    // Contacto de emergencia.
    var mostrarEditorEmergencia by remember { mutableStateOf(false) }
    var editandoEmergencia by remember { mutableStateOf(false) }
    var emergenciaNombreInput by remember { mutableStateOf(PreferenciasPerfilStore.emergenciaNombre) }
    var emergenciaParentescoInput by remember { mutableStateOf(PreferenciasPerfilStore.emergenciaParentesco) }
    var emergenciaNumeroInput by remember { mutableStateOf(PreferenciasPerfilStore.emergenciaNumero) }
    var mostrarParentescoPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Datos Personales", "Personal details"), style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onCerrar) { Text(L.t("Cerrar", "Close")) }
            }
            Spacer(Modifier.height(12.dp))

            //----Cabecera: foto + nombres + carrera + correo----
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(PrimaryFill, PrimaryContainer, PrimaryGradientEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        fotoPerfil?.let { foto ->
                            Image(
                                bitmap = foto.asImageBitmap(),
                                contentDescription = L.t("Foto de perfil", "Profile photo"),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: Text("JD", style = HeadlineMd, color = OnPrimaryFill)
                    }
                    // Insignia de camara: elige camara, galeria o reencuadrar.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { mostrarFuenteFoto = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CameraAlt, L.t("Cambiar foto de perfil", "Change profile photo"), tint = AppPrimary, modifier = Modifier.size(13.dp))
                    }
                }
                Column {
                    Text(NOMBRES_COMPLETOS, style = HeadlineSm, color = OnSurface)
                    Text(CARRERA, style = BodySm, color = OnSurfaceVariant)
                    Text(CORREO_INSTITUCIONAL, style = BodyXs, color = OnSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))

            //----Cuadro institucional (solo lectura)----
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DatoFila(L.t("Código UTP", "UTP code"), CODIGO_UTP)
                    DatoFila(L.t("DNI", "ID NUMBER"), DNI)
                    DatoFila(L.t("Modalidad", "Modality"), MODALIDAD)
                    DatoFila(L.t("Campus", "Campus"), CAMPUS)
                }
            }
            Spacer(Modifier.height(16.dp))

            //----Teléfono y correo (editables, persisten)----
            Text(L.t("CONTACTO", "CONTACT"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (!editandoDatos) {
                DatoFila(L.t("Teléfono", "Phone"), PreferenciasPerfilStore.telefono)
                DatoFila(L.t("Correo personal", "Personal email"), PreferenciasPerfilStore.correoPersonal)
                TextButton(onClick = {
                    telefonoInput = PreferenciasPerfilStore.telefono
                    correoInput = PreferenciasPerfilStore.correoPersonal
                    editandoDatos = true
                }) {
                    Icon(Icons.Filled.Edit, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(L.t("Editar contacto", "Edit contact"), style = BodySmMedium, color = AppPrimary)
                }
            } else {
                OutlinedTextField(
                    value = telefonoInput,
                    onValueChange = { telefonoInput = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text(L.t("Teléfono, 9 dígitos", "Phone, 9 digits")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = correoInput,
                    onValueChange = { correoInput = it },
                    label = { Text(L.t("Correo personal", "Personal email")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { editandoDatos = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(L.t("Cancelar", "Cancel")) }
                    Button(
                        onClick = {
                            PreferenciasPerfilStore.establecerTelefono(telefonoInput)
                            PreferenciasPerfilStore.establecerCorreoPersonal(correoInput)
                            editandoDatos = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                    ) { Text(L.t("Guardar", "Save")) }
                }
            }
            Spacer(Modifier.height(16.dp))

            ContactoEmergenciaCard(onEditar = { mostrarEditorEmergencia = true })
            Spacer(Modifier.height(16.dp))
            Text(
                L.t(
                    "El contacto de emergencia se guarda solo en este teléfono.",
                    "The emergency contact is saved on this phone only."
                ),
                style = BodyXs, color = OnSurfaceVariant
            )
        }
    }

    if (mostrarEditorEmergencia) ContactoEmergenciaSheet { mostrarEditorEmergencia = false }

    //----Dialogo de fuente de la foto (camara / galeria / reencuadrar)----
    if (mostrarFuenteFoto) {
        AlertDialog(
            onDismissRequest = { mostrarFuenteFoto = false },
            title = { Text(L.t("Foto de perfil", "Profile photo")) },
            text = {
                Column {
                    OpcionFoto(Icons.Filled.CameraAlt, L.t("Tomar foto", "Take photo")) {
                        mostrarFuenteFoto = false
                        abrirSelectorFoto()   // el selector interno ofrece camara/galeria
                    }
                    OpcionFoto(Icons.Filled.PhotoLibrary, L.t("Elegir de galería", "Choose from gallery")) {
                        mostrarFuenteFoto = false
                        abrirSelectorFoto()
                    }
                    if (fotoPerfil != null) {
                        OpcionFoto(Icons.Filled.Edit, L.t("Ajustar foto actual", "Adjust current photo")) {
                            mostrarFuenteFoto = false
                            fotoPerfil?.let { bitmapParaEncuadre = it }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    //----Editor de encuadre circular (perfil)----
    bitmapParaEncuadre?.let { bitmap ->
        EditorEncuadre(
            bitmap = bitmap,
            circular = true,
            onGuardar = { recorte ->
                ProfileImageStore.guardar(contexto, recorte)
                bitmapParaEncuadre = null
            },
            onCancelar = { bitmapParaEncuadre = null }
        )
    }

    //----Selector de parentesco (ventanita del iOS)----
    if (mostrarParentescoPicker) {
        AlertDialog(
            onDismissRequest = { mostrarParentescoPicker = false },
            title = { Text(L.t("Parentesco", "Relationship")) },
            text = {
                Column {
                    Parentesco.entries.forEach { opcion ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (emergenciaParentescoInput == opcion.clave) PrimaryContainer.copy(alpha = 0.25f)
                                    else SurfaceContainerLow
                                )
                                .clickable {
                                    com.example.rutautpnative.ui.components.AppHaptics.seleccion()
                                    emergenciaParentescoInput = opcion.clave
                                }
                                .padding(14.dp)
                        ) {
                            Icon(
                                if (emergenciaParentescoInput == opcion.clave) Icons.Filled.CheckCircle else Icons.Filled.Call,
                                null,
                                tint = if (emergenciaParentescoInput == opcion.clave) AppPrimary else OnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(opcion.label, style = BodyMd, color = OnSurface)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mostrarParentescoPicker = false }) { Text(L.t("Aceptar", "OK")) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarParentescoPicker = false }) { Text(L.t("Cancelar", "Cancel")) }
            }
        )
    }
}

@Composable
private fun DatoFila(titulo: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(titulo, style = BodySm, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valor, style = BodySmMedium, color = OnSurface)
    }
}
