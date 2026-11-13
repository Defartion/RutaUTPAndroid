package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.rutautpnative.BuildConfig
import com.example.rutautpnative.data.conexion.*
import com.example.rutautpnative.data.google.ServiciosGoogle
import com.example.rutautpnative.data.tracking.MQTTConfiguration
import com.example.rutautpnative.navigation.ViajesActivos
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

/** Las claves se mantienen solo en memoria de la hoja hasta su guardado seguro. */
@Composable
fun ConexionTransporteScreen(onCerrar: () -> Unit) {
    var rol by remember { mutableStateOf(RolConexion.CONTRIBUCION) }
    val guardada = remember(ConexionInstalacionStore.revision, rol) { ConexionInstalacionStore.cuenta(rol) }
    val canal = guardada?.canal ?: ConexionInstalacionStore.receptor()?.canal ?: MQTTConfiguration.canalPublico()
    var host by remember(rol) { mutableStateOf(canal?.host.orEmpty()) }
    var puerto by remember(rol) { mutableStateOf((canal?.puerto ?: 8883).toString()) }
    var tls by remember(rol) { mutableStateOf(if (BuildConfig.DEBUG) canal?.tls ?: true else true) }
    var usuario by remember(rol) { mutableStateOf(guardada?.usuario.orEmpty()) }
    var clave by remember(rol) { mutableStateOf("") }
    var ca by remember(rol) { mutableStateOf(canal?.caPem.orEmpty()) }
    var avanzado by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }
    var borrarTodo by remember { mutableStateOf(false) }
    fun ejecutar(accion: () -> Unit) {
        if (ViajesActivos.estado.enCurso) pendiente = accion else accion()
    }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.94f).statusBarsPadding(), color = AppSurface) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(L.t("Conexión de transporte", "Transport connection"), style = HeadlineMd, color = OnSurface)
                Text(L.t("Usa la cuenta que el equipo te asignó para este teléfono. Se guarda solo en este dispositivo.", "Use the account assigned to this phone by your team. It is stored only on this device."), style = BodySm, color = OnSurfaceVariant)
                if (!ConexionInstalacionStore.disponible) Text(L.t("No se puede abrir la configuración protegida. Puedes borrar la configuración de conexión y volver a introducir tus cuentas.", "Protected settings could not be opened. You can clear connection settings and enter your accounts again."), color = AppError)
                if (ConexionInstalacionStore.autorizacionRechazada) Text(L.t("La cuenta no fue autorizada. Revisa los datos o solicita una nueva.", "The account was not authorized. Check the details or request a new one."), color = AppError)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(rol == RolConexion.LECTURA, { rol = RolConexion.LECTURA; mensaje = null }, { Text(L.t("Leer el mapa", "Read the map")) })
                    FilterChip(rol == RolConexion.CONTRIBUCION, { rol = RolConexion.CONTRIBUCION; mensaje = null }, { Text(L.t("Aportar ubicaciones", "Contribute locations")) })
                }
                guardada?.let { Text(L.t("Cuenta guardada: ", "Saved account: ") + it.usuario, style = BodySm) }
                OutlinedTextField(host, { host = it }, label = { Text(L.t("Servidor", "Server")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(puerto, { puerto = it }, label = { Text(L.t("Puerto", "Port")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (BuildConfig.DEBUG) Row {
                    Checkbox(tls, { tls = it; puerto = if (it) "8883" else "1883" })
                    Text(L.t("Conexión protegida", "Protected connection"), modifier = Modifier.padding(top = 12.dp))
                }
                OutlinedTextField(usuario, { usuario = it }, label = { Text(L.t("Usuario", "Username")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(clave, { clave = it }, label = { Text(L.t("Contraseña", "Password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { avanzado = !avanzado }) { Text(L.t("Certificado del servidor", "Server certificate")) }
                if (avanzado) OutlinedTextField(ca, { ca = it }, label = { Text(L.t("Certificado del administrador (opcional)", "Administrator certificate (optional)")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Button(onClick = {
                    val cuenta = CuentaConexion(CanalConexion(host.trim(), puerto.toIntOrNull() ?: 0, tls,
                        if (ca.isBlank()) canal?.caAsset.orEmpty() else "", ca), rol, usuario.trim(), clave)
                    ejecutar {
                        if (ConexionInstalacionStore.guardar(cuenta)) { clave = ""; onCerrar() }
                        else mensaje = L.t("No se pudo guardar. Revisa el servidor, la cuenta y el certificado. Las cuentas de lectura y aporte deben ser distintas.", "Could not save. Check the server, account and certificate. Reading and contribution accounts must be different.")
                    }
                }, enabled = ConexionInstalacionStore.disponible && host.isNotBlank() && usuario.isNotBlank() && clave.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(L.t("Guardar cuenta", "Save account")) }
                if (guardada != null) OutlinedButton(onClick = { ejecutar { if (ConexionInstalacionStore.borrar(rol)) onCerrar() else mensaje = L.t("No se pudo borrar la cuenta.", "Could not remove the account.") } }, modifier = Modifier.fillMaxWidth()) { Text(L.t("Quitar esta cuenta", "Remove this account")) }
                HorizontalDivider()
                Text(L.t("Mapas y búsqueda", "Maps and search"), style = HeadlineSm)
                Text(if (ServiciosGoogle.mapaConfigurado) L.t("La configuración del mapa está incluida en esta versión.", "Map configuration is included in this version.") else L.t("Falta la configuración del mapa en esta versión.", "Map configuration is missing in this version."), style = BodySm)
                val webGuardada = remember(ConexionInstalacionStore.revisionWeb) { ConexionInstalacionStore.claveWeb() != null }
                Text(if (webGuardada) L.t("Búsqueda configurada en este teléfono.", "Search configured on this phone.") else L.t("Solicita al equipo la clave de búsqueda para este teléfono.", "Ask your team for this phone's search key."), style = BodySm)
                OutlinedTextField(web, { web = it }, label = { Text(L.t("Clave de búsqueda", "Search key")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { mensaje = if (ConexionInstalacionStore.guardarClaveWeb(web)) { web = ""; L.t("Búsqueda configurada.", "Search configured.") } else L.t("No se pudo guardar la clave.", "Could not save the key.") }, enabled = ConexionInstalacionStore.disponible && web.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(L.t("Guardar búsqueda", "Save search settings")) }
                if (webGuardada) TextButton(onClick = { ConexionInstalacionStore.guardarClaveWeb(""); mensaje = L.t("Se quitó la clave de búsqueda.", "Search key removed.") }) { Text(L.t("Quitar clave de búsqueda", "Remove search key")) }
                mensaje?.let { Text(it, style = BodySm, color = OnSurfaceVariant) }
                TextButton(onClick = { borrarTodo = true }) { Text(L.t("Borrar configuración de conexión", "Clear connection settings"), color = AppError) }
                TextButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text(L.t("Cerrar", "Close")) }
            }
        }
    }
    if (pendiente != null) AlertDialog(onDismissRequest = { pendiente = null }, title = { Text(L.t("Terminar viaje actual", "End current trip")) },
        text = { Text(L.t("Cambiar la cuenta termina el viaje actual y desactiva sus aportes y avisos. Podrás activarlos de nuevo con la nueva cuenta.", "Changing the account ends the current trip and disables its contributions and notifications. You can enable them again with the new account.")) },
        confirmButton = { TextButton(onClick = { val accion = pendiente; pendiente = null; accion?.invoke() }) { Text(L.t("Continuar", "Continue")) } },
        dismissButton = { TextButton(onClick = { pendiente = null }) { Text(L.t("Cancelar", "Cancel")) } })
    if (borrarTodo) AlertDialog(onDismissRequest = { borrarTodo = false }, title = { Text(L.t("Borrar configuración", "Clear settings")) },
        text = { Text(L.t("Se borrarán las cuentas y la clave de búsqueda de este teléfono. Necesitarás introducirlas de nuevo.", "Accounts and the search key on this phone will be removed. You will need to enter them again.")) },
        confirmButton = { TextButton(onClick = { borrarTodo = false; ejecutar { if (ConexionInstalacionStore.reiniciar()) onCerrar() else mensaje = L.t("No se pudo borrar la configuración.", "Could not clear settings.") } }) { Text(L.t("Borrar", "Clear")) } },
        dismissButton = { TextButton(onClick = { borrarTodo = false }) { Text(L.t("Cancelar", "Cancel")) } })
}
