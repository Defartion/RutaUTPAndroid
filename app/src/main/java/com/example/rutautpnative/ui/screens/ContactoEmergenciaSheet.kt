package com.example.rutautpnative.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.*
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactoEmergenciaSheet(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val store = PreferenciasPerfilStore
    var nombre by remember { mutableStateOf(store.emergenciaNombre) }
    var parentesco by remember { mutableStateOf(store.emergenciaParentesco) }
    var numero by remember { mutableStateOf(store.emergenciaNumero) }
    var alternativo by remember { mutableStateOf(store.emergenciaAlternativo) }
    var confirmarEliminar by remember { mutableStateOf(false) }
    val telefono = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri -> runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) { nombre = cursor.getString(0).orEmpty(); numero = cursor.getString(1).orEmpty() }
            }
        } }
    }
    val principal = ContactoEmergencia.telefonoValido(numero)
    val segundo = ContactoEmergencia.telefonoValido(alternativo)
    val puedeGuardar = nombre.isNotBlank() && principal != null && (alternativo.isBlank() || (segundo != null && segundo != principal))
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L.t("Contacto de emergencia", "Emergency contact"), style = HeadlineMd)
            OutlinedButton(onClick = { runCatching { telefono.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } }) { Text(L.t("Elegir de contactos", "Choose from contacts")) }
            OutlinedTextField(nombre, { nombre = it.take(120) }, label = { Text(L.t("Nombre", "Name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Parentesco.entries.forEach { valor -> FilterChip(parentesco == valor.clave, { parentesco = valor.clave }, label = { Text(valor.label) }) }
            }
            OutlinedTextField(numero, { numero = it.take(40) }, label = { Text(L.t("Número principal", "Primary phone")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(alternativo, { alternativo = it.take(40) }, label = { Text(L.t("Número alternativo (opcional)", "Alternative phone (optional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(L.t("Usa entre 7 y 15 dígitos, con código de país si hace falta. El número alternativo debe ser diferente.", "Use 7 to 15 digits, with country code if needed. The alternative number must be different."), style = BodyXs)
            Button(onClick = { store.establecerEmergencia(nombre.trim(), parentesco, principal!!, segundo.orEmpty()); onCerrar() }, enabled = puedeGuardar, modifier = Modifier.fillMaxWidth()) { Text(L.t("Guardar", "Save")) }
            if (store.emergenciaNombre.isNotBlank() || store.emergenciaNumero.isNotBlank()) TextButton(onClick = { confirmarEliminar = true }) { Text(L.t("Eliminar contacto", "Delete contact"), color = AppError) }
            Text(L.t("Se guarda solo en este teléfono. Las llamadas se abren en la aplicación de teléfono.", "Stored only on this phone. Calls open in the phone app."), style = BodyXs)
            TextButton(onClick = onCerrar) { Text(L.t("Cancelar", "Cancel")) }
        }
    }
    if (confirmarEliminar) AlertDialog(onDismissRequest = { confirmarEliminar = false },
        title = { Text(L.t("¿Eliminar contacto?", "Delete contact?")) },
        confirmButton = { TextButton(onClick = { store.establecerEmergencia("", "", "", ""); onCerrar() }) { Text(L.t("Eliminar", "Delete")) } },
        dismissButton = { TextButton(onClick = { confirmarEliminar = false }) { Text(L.t("Cancelar", "Cancel")) } })
}

@Composable
fun ContactoEmergenciaCard(onEditar: () -> Unit) {
    val ctx = LocalContext.current
    val store = PreferenciasPerfilStore
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L.t("Contacto de emergencia", "Emergency contact"), style = HeadlineSm)
            if (store.emergenciaNombre.isNotBlank()) Text(store.emergenciaNombre, color = OnSurface)
            listOf(store.emergenciaNumero, store.emergenciaAlternativo).mapNotNull(ContactoEmergencia::telefonoValido).forEach { numero ->
                OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero"))) } }) { Text(L.t("Llamar", "Call") + " " + numero) }
            }
            TextButton(onClick = onEditar) { Text(L.t("Editar contacto de emergencia", "Edit emergency contact")) }
        }
    }
}
