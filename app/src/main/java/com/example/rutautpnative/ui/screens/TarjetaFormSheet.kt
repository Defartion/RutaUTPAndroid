package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

//----Formulario de referencia de tarjeta (puerto del TarjetaFormSheet.swift)----
// Diseno HONESTO del iOS: SOLO el nombre, la red y los ULTIMOS 4 DIGITOS.
// Nunca el numero completo ni el CVV: la referencia no permite pagar, ni
// recargar el monedero, ni usar NFC (texto del footer).

private val redesDisponibles = listOf("Visa", "Mastercard", "American Express", "Diners Club", "Otra")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarjetaFormSheet(
    onGuardar: (nombre: String, red: String, ultimos4: String) -> Unit,
    onCancelar: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var red by remember { mutableStateOf("Visa") }
    var ultimos4 by remember { mutableStateOf("") }

    // Ultimos 4: solo digitos ASCII, exactamente 4.
    val digitos4 = ultimos4.length == 4 && ultimos4.all { it.isDigit() }
    val puedeGuardar = nombre.isNotBlank() && digitos4

    ModalBottomSheet(onDismissRequest = onCancelar, containerColor = AppSurface) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CreditCard, null, tint = AppPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(L.t("Añadir referencia de tarjeta", "Add card reference"), style = HeadlineMd, color = OnSurface)
            }
            Spacer(Modifier.height(16.dp))

            Text(L.t("NOMBRE EN LA TARJETA", "NAME ON CARD"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = nombre,
                onValueChange = { if (it.length <= 60) nombre = it },
                placeholder = { Text(L.t("Ej. Joaquín Díaz", "E.g. Joaquín Díaz")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))
            Text(L.t("RED", "NETWORK"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                redesDisponibles.forEach { r ->
                    FilterChip(
                        selected = red == r,
                        onClick = { red = r },
                        label = { Text(if (r == "American Express") "Amex" else r, style = BodyXs, maxLines = 1) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(L.t("ÚLTIMOS 4 DÍGITOS", "LAST 4 DIGITS"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = ultimos4,
                onValueChange = { entrada ->
                    // Solo digitos ASCII, maximo 4 (teclado numerico).
                    ultimos4 = entrada.filter { it.isDigit() }.take(4)
                },
                placeholder = { Text("1234") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    if (ultimos4.isNotEmpty() && !digitos4) {
                        Text(L.t("Deben ser exactamente 4 dígitos.", "Must be exactly 4 digits."), color = AppError)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onGuardar(nombre.trim(), red, ultimos4) },
                enabled = puedeGuardar,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
            ) {
                Text(L.t("Guardar referencia", "Save reference"), style = HeadlineSm, color = Color.White)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                L.t(
                    "No introduzcas el número completo ni el CVV. Esta referencia no vincula la tarjeta con el banco ni habilita pagos.",
                    "Don't enter the full number or the CVV. This reference doesn't link the card to the bank or enable payments."
                ),
                style = BodyXs, color = OnSurfaceVariant
            )
        }
    }
}
