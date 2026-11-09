package com.example.rutautpnative.ui.components

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Water
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.rutautpnative.model.CategoriaLugar
import com.example.rutautpnative.model.CategoriaNegocio

//----Íconos de categoría (compartidos entre pantallas)----
// Antes estaban duplicados: GuardadoScreen + SeguridadScreen (lugares) y
// PerfilScreen + NavegacionScreen (negocios). Una sola fuente de verdad.

fun iconoParaCategoria(cat: CategoriaLugar): ImageVector = when (cat) {
    CategoriaLugar.UNIVERSIDAD -> Icons.Filled.School
    CategoriaLugar.HOGAR       -> Icons.Filled.Home
    CategoriaLugar.TIENDA      -> Icons.Filled.Storefront
    CategoriaLugar.RESTAURANTE -> Icons.Filled.Restaurant
    CategoriaLugar.PLAZA       -> Icons.Filled.AccountBalance
    CategoriaLugar.PLAYA       -> Icons.Filled.Water
    CategoriaLugar.TRABAJO -> Icons.Filled.Work
    CategoriaLugar.ESTUDIOS -> Icons.Filled.MenuBook
    CategoriaLugar.SALUD -> Icons.Filled.MedicalServices
    CategoriaLugar.MERCADO -> Icons.Filled.ShoppingBasket
    CategoriaLugar.CENTRO_COMERCIAL -> Icons.Filled.ShoppingBag
    CategoriaLugar.CAFETERIA -> Icons.Filled.LocalCafe
    CategoriaLugar.PARQUE -> Icons.Filled.Park
    CategoriaLugar.GIMNASIO -> Icons.Filled.FitnessCenter
    CategoriaLugar.PARADERO -> Icons.Filled.DirectionsBus
    CategoriaLugar.PERSONALIZADO -> Icons.Filled.Star
    CategoriaLugar.OTRO        -> Icons.Filled.LocationOn
}

fun iconoParaCategoria(cat: CategoriaNegocio): ImageVector = when (cat) {
    CategoriaNegocio.POLLERIA    -> Icons.Filled.DinnerDining
    CategoriaNegocio.MENU        -> Icons.Filled.Restaurant
    CategoriaNegocio.CAFETERIA   -> Icons.Filled.LocalCafe
    CategoriaNegocio.CHIFA       -> Icons.Filled.RamenDining
    CategoriaNegocio.SALCHIPAPAS -> Icons.Filled.Fastfood
    CategoriaNegocio.PANADERIA   -> Icons.Filled.BakeryDining
    CategoriaNegocio.HELADERIA   -> Icons.Filled.Icecream
    CategoriaNegocio.JUGUERIA    -> Icons.Filled.LocalDrink
    CategoriaNegocio.PIZZA       -> Icons.Filled.LocalPizza
    CategoriaNegocio.BURGER      -> Icons.Filled.LunchDining
    CategoriaNegocio.CEVICHERIA  -> Icons.Filled.SetMeal
    CategoriaNegocio.EMPANADAS   -> Icons.Filled.BakeryDining
}

fun iconoParaLugar(lugar: com.example.rutautpnative.model.LugarGuardado): ImageVector = when (lugar.iconoPersonalizado) {
    "favorite" -> Icons.Filled.Favorite
    "flag" -> Icons.Filled.Flag
    "people" -> Icons.Filled.People
    "pets" -> Icons.Filled.Pets
    "local_pharmacy" -> Icons.Filled.LocalPharmacy
    "sports_soccer" -> Icons.Filled.SportsSoccer
    "movie" -> Icons.Filled.Movie
    "music_note" -> Icons.Filled.MusicNote
    "star" -> Icons.Filled.Star
    else -> iconoParaCategoria(if (lugar.esParadero) CategoriaLugar.PARADERO else lugar.categoria)
}
