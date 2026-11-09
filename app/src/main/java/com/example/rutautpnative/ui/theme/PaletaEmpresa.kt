package com.example.rutautpnative.ui.theme

import androidx.compose.ui.graphics.Color

//----Paletas de empresa (puerto del PaletaEmpresa.swift)----
// Colores preparados una vez por tematica. Superficies neutras, errores,
// colores GTFS y marcas de pago conservan sus responsabilidades originales.
//
// Los tokens con variante claro/oscuro usan ParColor; el resto es fijo. Los
// valores provienen de las marcas publicadas en sus recursos web; los tonos
// de interfaz son derivados para que los botones sigan admitiendo texto
// blanco (comentarios del iOS conservados).

/// Color de un token con variante clara y oscura (Color(light:dark:) del iOS).
data class ParColor(val claro: Color, val oscuro: Color) {
    fun en(oscuro: Boolean): Color = if (oscuro) this.oscuro else this.claro
}

private fun hex(hex: String): Color {
    // Parseo puro (sin android.graphics): los tests JVM no tienen framework.
    val valor = hex.removePrefix("#").toLong(16)
    return Color(
        red = ((valor shr 16) and 0xFF).toInt(),
        green = ((valor shr 8) and 0xFF).toInt(),
        blue = (valor and 0xFF).toInt()
    )
}
private fun fijo(hex: String): ParColor = ParColor(hex(hex), hex(hex))
private fun par(claro: String, oscuro: String): ParColor = ParColor(hex(claro), hex(oscuro))

data class PaletaEmpresa(
    val colorMarca: Color,
    val appPrimary: ParColor,
    val primaryFill: Color,
    val onPrimaryFill: Color,
    val primaryGradientEnd: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryFixed: Color,
    val inversePrimary: ParColor,
    val secondary: Color,
    val secondaryContainer: ParColor,
    val onSecondary: Color,
    val onSecondaryContainer: ParColor,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiary: Color,
    val onTertiaryContainer: Color,
    val onSurfaceVariant: ParColor,
    val outline: ParColor,
    val outlineVariant: ParColor
)

object PaletasEmpresa {

    val actual: PaletaEmpresa
        get() = paleta(TematicaEmpresaStore.seleccion)

    fun paleta(tematica: TematicaEmpresa): PaletaEmpresa = when (tematica) {
        TematicaEmpresa.UTP -> utp
        TematicaEmpresa.INTERBANK -> interbank
        TematicaEmpresa.POPEYES -> popeyes
        TematicaEmpresa.PLAZA_VEA -> plazaVea
        TematicaEmpresa.CINEPLANET -> cineplanet
        TematicaEmpresa.INNOVA_SCHOOLS -> innovaSchools
        TematicaEmpresa.INKAFARMA -> inkafarma
        TematicaEmpresa.MIFARMA -> mifarma
        TematicaEmpresa.BEMBOS -> bembos
        TematicaEmpresa.DON_BELISARIO -> donBelisario
    }

    /// Constructor de paletas corporativas simples (corporativa() del iOS):
    /// contenedores claro/oscuro, texto y contornos mantienen roles separados.
    private fun corporativa(
        marca: String, primaria: String, secundaria: String,
        claro: String, oscuro: String
    ): PaletaEmpresa = PaletaEmpresa(
        colorMarca = hex(marca),
        appPrimary = fijo(primaria),
        primaryFill = hex(primaria),
        onPrimaryFill = Color.White,
        primaryGradientEnd = hex(primaria),
        primaryContainer = hex(primaria),
        onPrimaryContainer = Color.White,
        primaryFixed = hex(claro),
        inversePrimary = par(primaria, claro),
        secondary = hex(secundaria),
        secondaryContainer = par(claro, oscuro),
        onSecondary = Color.White,
        onSecondaryContainer = par(secundaria, claro),
        tertiary = hex(primaria),
        tertiaryContainer = hex(primaria),
        onTertiary = Color.White,
        onTertiaryContainer = Color.White,
        onSurfaceVariant = par("#48505A", "#C2CBD6"),
        outline = par("#77808D", "#A5AFBD"),
        outlineVariant = par("#D5DCE5", "#3B4655")
    )

    // UTP reproduce literalmente los tokens anteriores de Color.kt.
    private val utp = PaletaEmpresa(
        colorMarca = hex("#a80033"),
        appPrimary = fijo("#a80033"),
        primaryFill = hex("#a80033"),
        onPrimaryFill = Color.White,
        primaryGradientEnd = hex("#005b6e"),
        primaryContainer = hex("#d31245"),
        onPrimaryContainer = hex("#ffe8e8"),
        primaryFixed = hex("#ffdadb"),
        inversePrimary = fijo("#ffb2b7"),
        secondary = hex("#3c5d9c"),
        secondaryContainer = fijo("#99b8fe"),
        onSecondary = Color.White,
        onSecondaryContainer = fijo("#244885"),
        tertiary = hex("#005b6e"),
        tertiaryContainer = hex("#00758d"),
        onTertiary = Color.White,
        onTertiaryContainer = hex("#d1f2ff"),
        onSurfaceVariant = par("#5c3f41", "#c9adaf"),
        outline = par("#906f70", "#a98b8c"),
        outlineVariant = par("#e4bdbf", "#4a3f40")
    )

    // Referencia: icono oficial Interbank APP (#05BE50 / #0039A6).
    // Los acentos verdes de botones se oscurecen para conservar texto blanco.
    private val interbank = PaletaEmpresa(
        colorMarca = hex("#05BE50"),
        appPrimary = fijo("#007D36"),
        primaryFill = hex("#007D36"),
        onPrimaryFill = Color.White,
        primaryGradientEnd = hex("#006D35"),
        primaryContainer = hex("#00853A"),
        onPrimaryContainer = hex("#E5FFEF"),
        primaryFixed = hex("#D7F8E3"),
        inversePrimary = fijo("#007D36"),
        secondary = hex("#0039A6"),
        secondaryContainer = par("#DAE6FF", "#193660"),
        onSecondary = Color.White,
        onSecondaryContainer = par("#002C80", "#DAE6FF"),
        tertiary = hex("#006D35"),
        tertiaryContainer = hex("#007D36"),
        onTertiary = Color.White,
        onTertiaryContainer = hex("#DAFFE8"),
        onSurfaceVariant = par("#43534A", "#B8CAC0"),
        outline = par("#6E8777", "#9AB4A4"),
        outlineVariant = par("#CCDCD1", "#344A3D")
    )

    // Referencia: SVG oficial #FF7D00 y CSS #B54000 / #3E342F.
    private val popeyes = PaletaEmpresa(
        colorMarca = hex("#FF7D00"),
        appPrimary = fijo("#B54000"),
        primaryFill = hex("#B54000"),
        onPrimaryFill = Color.White,
        primaryGradientEnd = hex("#8A3100"),
        primaryContainer = hex("#B54000"),
        onPrimaryContainer = hex("#FFF0E3"),
        primaryFixed = hex("#FFE6CD"),
        inversePrimary = fijo("#B54000"),
        secondary = hex("#3E342F"),
        secondaryContainer = par("#F5E6D8", "#46392F"),
        onSecondary = Color.White,
        onSecondaryContainer = par("#3E342F", "#F5E6D8"),
        tertiary = hex("#8A3100"),
        tertiaryContainer = hex("#B54000"),
        onTertiary = Color.White,
        onTertiaryContainer = hex("#FFE6CD"),
        onSurfaceVariant = par("#594B40", "#D2C3B4"),
        outline = par("#987F6A", "#BFA78F"),
        outlineVariant = par("#E8D3C0", "#514032")
    )

    // Referencia: SVG oficial #CC292E / #FEC600; CSS #9D1E23.
    private val plazaVea = PaletaEmpresa(
        colorMarca = hex("#CC292E"),
        appPrimary = fijo("#CC292E"),
        primaryFill = hex("#CC292E"),
        onPrimaryFill = Color.White,
        primaryGradientEnd = hex("#9D1E23"),
        primaryContainer = hex("#9D1E23"),
        onPrimaryContainer = hex("#FFF0F1"),
        primaryFixed = hex("#FFF0F1"),
        inversePrimary = fijo("#B22227"),
        secondary = hex("#7D6100"),
        secondaryContainer = par("#FEC600", "#594700"),
        onSecondary = Color.White,
        onSecondaryContainer = par("#332800", "#FFE99A"),
        tertiary = hex("#9D1E23"),
        tertiaryContainer = hex("#9D1E23"),
        onTertiary = Color.White,
        onTertiaryContainer = hex("#FFE6E6"),
        onSurfaceVariant = par("#5B4849", "#D2BCBD"),
        outline = par("#937A7B", "#BCA4A5"),
        outlineVariant = par("#DFCBCB", "#513E3F")
    )

    // Valores de marca publicados en sus recursos web.
    private val cineplanet = corporativa(
        marca = "#004A8C", primaria = "#004A8C", secundaria = "#C5003B",
        claro = "#E2EEFF", oscuro = "#203B58"
    )

    // El simbolo publicado en el favicon oficial contiene azul #0069AD,
    // verde #6BC62A y naranja #FF9700. Los acentos pequenos se oscurecen.
    private val innovaSchools = PaletaEmpresa(
        colorMarca = hex("#0069AD"),
        appPrimary = par("#0069AD", "#8BCBFA"),
        primaryFill = hex("#0069AD"),
        onPrimaryFill = Color.White,
        primaryGradientEnd = hex("#A85400"),
        primaryContainer = hex("#0069AD"),
        onPrimaryContainer = Color.White,
        primaryFixed = hex("#DDF0FF"),
        inversePrimary = fijo("#0069AD"),
        secondary = hex("#317B13"),
        secondaryContainer = par("#6BC62A", "#294E1D"),
        onSecondary = Color.White,
        onSecondaryContainer = par("#17360B", "#D4F3B7"),
        tertiary = hex("#A85400"),
        tertiaryContainer = hex("#A85400"),
        onTertiary = Color.White,
        onTertiaryContainer = Color.White,
        onSurfaceVariant = par("#40535D", "#BFD2DF"),
        outline = par("#728C99", "#9CBCCB"),
        outlineVariant = par("#CCDDE3", "#354E5B")
    )

    // Amarillo dominante en superficies de accion; verde para enlaces,
    // iconos y detalles. La tinta sobre amarillo nunca depende de .white.
    private val inkafarma = PaletaEmpresa(
        colorMarca = hex("#FFF200"),
        appPrimary = par("#167538", "#8FDC9C"),
        primaryFill = hex("#FFF200"),
        onPrimaryFill = hex("#193D23"),
        primaryGradientEnd = hex("#FFFAD1"),
        primaryContainer = hex("#FFF200"),
        onPrimaryContainer = hex("#193D23"),
        primaryFixed = hex("#FFFAD1"),
        inversePrimary = fijo("#FFF200"),
        secondary = hex("#167538"),
        secondaryContainer = par("#DEF3CB", "#274A2A"),
        onSecondary = Color.White,
        onSecondaryContainer = par("#255A2A", "#DDF5C3"),
        tertiary = hex("#167538"),
        tertiaryContainer = hex("#167538"),
        onTertiary = Color.White,
        onTertiaryContainer = Color.White,
        onSurfaceVariant = par("#4C5139", "#CFD3B6"),
        outline = par("#887D40", "#BDB56B"),
        outlineVariant = par("#E3DEB8", "#4A482C")
    )

    private val mifarma = corporativa(
        marca = "#FF7929", primaria = "#AD4C00", secundaria = "#167538",
        claro = "#FFF0DE", oscuro = "#54371D"
    )

    private val bembos = corporativa(
        marca = "#1100CF", primaria = "#1100CF", secundaria = "#7D5800",
        claro = "#EAE7FF", oscuro = "#302764"
    )

    private val donBelisario = corporativa(
        marca = "#E5133A", primaria = "#B70F2E", secundaria = "#121212",
        claro = "#FFE8ED", oscuro = "#542A35"
    )
}
