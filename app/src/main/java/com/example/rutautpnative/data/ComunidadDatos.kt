package com.example.rutautpnative.data

import com.example.rutautpnative.model.ReporteComunidad
import com.example.rutautpnative.model.TipoReporte
import com.example.rutautpnative.ui.theme.AppPrimary
import com.example.rutautpnative.ui.theme.ErrorContainer
import com.example.rutautpnative.ui.theme.OnErrorContainer
import com.example.rutautpnative.ui.theme.OnSecondaryContainer
import com.example.rutautpnative.ui.theme.OnSurfaceVariant
import com.example.rutautpnative.ui.theme.OnTertiaryContainer
import com.example.rutautpnative.ui.theme.PrimaryContainer
import com.example.rutautpnative.ui.theme.SecondaryContainer
import com.example.rutautpnative.ui.theme.SurfaceContainerHigh
import com.example.rutautpnative.ui.theme.TertiaryContainer

//----Publicaciones de ejemplo de la Comunidad----
// Contenido de demostración (igual que en iOS): 18 publicaciones fijas con
// nombres, textos, contadores y tiempos inventados, ambientadas en el
// transporte de Trujillo. No vienen de ningún backend.
object ComunidadDatos {

    val publicaciones: List<ReporteComunidad> = listOf(
        ReporteComunidad(
            iniciales = "JD", nombre = "Jorge D.", hace = "HACE 3 MIN",
            tipo = TipoReporte.ALERTA,
            cuerpo = "Micro lleno en Av. Larco. Pasaron 3 sin parar hacia la UTP.",
            utiles = 12, dislikes = 1, comentarios = 2,
            avatarColor = SurfaceContainerHigh, avatarForeground = OnSurfaceVariant
        ),
        ReporteComunidad(
            iniciales = "MA", nombre = "María A.", hace = "HACE 8 MIN",
            tipo = TipoReporte.TRAFICO,
            cuerpo = "Demora en Óvalo Papal por obras. Considerar 10 min adicionales.",
            utiles = 45, dislikes = 2, comentarios = 8, utilMarcado = true,
            avatarColor = SecondaryContainer, avatarForeground = OnSecondaryContainer
        ),
        ReporteComunidad(
            iniciales = "RC", nombre = "Rosa C.", hace = "HACE 15 MIN",
            tipo = TipoReporte.SUGERENCIA,
            cuerpo = "Tomar Av. Miraflores a las 7:30 AM evita el tráfico de España.",
            utiles = 28, dislikes = 0, comentarios = 5,
            avatarColor = TertiaryContainer, avatarForeground = OnTertiaryContainer
        ),
        ReporteComunidad(
            iniciales = "LP", nombre = "Luis P.", hace = "HACE 22 MIN",
            tipo = TipoReporte.ALERTA,
            cuerpo = "Paradero de Av. España cuadra 14 sin luz otra vez. Ojo al bajar de noche.",
            utiles = 19, dislikes = 0, comentarios = 4,
            avatarColor = ErrorContainer, avatarForeground = OnErrorContainer
        ),
        ReporteComunidad(
            iniciales = "CV", nombre = "Carmen V.", hace = "HACE 34 MIN",
            tipo = TipoReporte.TRAFICO,
            cuerpo = "Av. Mansiche bloqueada a la altura del cementerio. La línea B se está desviando por Huáscar.",
            utiles = 33, dislikes = 3, comentarios = 6,
            avatarColor = PrimaryContainer, avatarForeground = AppPrimary
        ),
        ReporteComunidad(
            iniciales = "DT", nombre = "Diego T.", hace = "HACE 41 MIN",
            tipo = TipoReporte.OTRO,
            cuerpo = "¿Alguien sabe si la línea A subió el pasaje? Hoy me cobraron 2 soles.",
            utiles = 7, dislikes = 1, comentarios = 11,
            avatarColor = TertiaryContainer, avatarForeground = OnTertiaryContainer
        ),
        ReporteComunidad(
            iniciales = "SF", nombre = "Sandra F.", hace = "HACE 48 MIN",
            tipo = TipoReporte.SUGERENCIA,
            cuerpo = "Si van a Real Plaza en hora punta, bájense antes del Óvalo y crucen caminando. Se gana más tiempo.",
            utiles = 22, dislikes = 0, comentarios = 3,
            avatarColor = SecondaryContainer, avatarForeground = OnSecondaryContainer
        ),
        ReporteComunidad(
            iniciales = "HM", nombre = "Héctor M.", hace = "HACE 1 HORA",
            tipo = TipoReporte.ALERTA,
            cuerpo = "Motochorros merodeando el paradero de la 10 en El Porvenir. Esperen dentro de la bodega.",
            utiles = 51, dislikes = 0, comentarios = 9,
            avatarColor = ErrorContainer, avatarForeground = OnErrorContainer
        ),
        ReporteComunidad(
            iniciales = "PR", nombre = "Paola R.", hace = "HACE 1 HORA",
            tipo = TipoReporte.TRAFICO,
            cuerpo = "Tráfico pesadísimo en Av. América Norte. Los buses de la C van en caravana, ninguno avanza.",
            utiles = 16, dislikes = 2, comentarios = 2,
            avatarColor = SurfaceContainerHigh, avatarForeground = OnSurfaceVariant
        ),
        ReporteComunidad(
            iniciales = "JG", nombre = "José G.", hace = "HACE 2 HORAS",
            tipo = TipoReporte.SUGERENCIA,
            cuerpo = "La línea 4 pasa vacía si la tomas en el paradero del hospital, no en la esquina de España.",
            utiles = 38, dislikes = 1, comentarios = 7, utilMarcado = true,
            avatarColor = PrimaryContainer, avatarForeground = AppPrimary
        ),
        ReporteComunidad(
            iniciales = "LB", nombre = "Lucía B.", hace = "HACE 2 HORAS",
            tipo = TipoReporte.OTRO,
            cuerpo = "Encontré una mochila en el micro de la línea B, la dejé con el cobrador. Difundan porfa.",
            utiles = 44, dislikes = 0, comentarios = 13,
            avatarColor = TertiaryContainer, avatarForeground = OnTertiaryContainer
        ),
        ReporteComunidad(
            iniciales = "FA", nombre = "Fernando A.", hace = "HACE 3 HORAS",
            tipo = TipoReporte.TRAFICO,
            cuerpo = "Choque menor en Av. Jesús de Nazareth. Un solo carril libre, convoyes muy lentos.",
            utiles = 11, dislikes = 0, comentarios = 1,
            avatarColor = SecondaryContainer, avatarForeground = OnSecondaryContainer
        ),
        ReporteComunidad(
            iniciales = "MQ", nombre = "Milagros Q.", hace = "HACE 3 HORAS",
            tipo = TipoReporte.ALERTA,
            cuerpo = "El paradero frente a Cineplanet está oscuro y vacío después de las 9 PM. No esperen solas.",
            utiles = 26, dislikes = 0, comentarios = 8,
            avatarColor = ErrorContainer, avatarForeground = OnErrorContainer
        ),
        ReporteComunidad(
            iniciales = "RS", nombre = "Roberto S.", hace = "HACE 4 HORAS",
            tipo = TipoReporte.SUGERENCIA,
            cuerpo = "Los sábados al mediodía la B demora el doble. Si pueden, salgan antes del almuerzo.",
            utiles = 15, dislikes = 2, comentarios = 2,
            avatarColor = SurfaceContainerHigh, avatarForeground = OnSurfaceVariant
        ),
        ReporteComunidad(
            iniciales = "VN", nombre = "Valeria N.", hace = "HACE 5 HORAS",
            tipo = TipoReporte.TRAFICO,
            cuerpo = "Huelga de colectores en el centro: Av. España está cortada a la altura de la Plaza de Armas.",
            utiles = 40, dislikes = 4, comentarios = 12,
            avatarColor = PrimaryContainer, avatarForeground = AppPrimary
        ),
        ReporteComunidad(
            iniciales = "EC", nombre = "Ernesto C.", hace = "HACE 6 HORAS",
            tipo = TipoReporte.OTRO,
            cuerpo = "El cobrador de la 7 me devolvió el vuelto completo aunque no me di cuenta que faltaba. Hay gente buena todavía.",
            utiles = 63, dislikes = 0, comentarios = 15, utilMarcado = true,
            avatarColor = TertiaryContainer, avatarForeground = OnTertiaryContainer
        ),
        ReporteComunidad(
            iniciales = "AH", nombre = "Andrea H.", hace = "HACE 7 HORAS",
            tipo = TipoReporte.ALERTA,
            cuerpo = "Asaltaron a un pasajero en el Óvalo Papal hace un rato. Esperen el bus con más gente alrededor.",
            utiles = 58, dislikes = 1, comentarios = 10,
            avatarColor = ErrorContainer, avatarForeground = OnErrorContainer
        ),
        ReporteComunidad(
            iniciales = "GM", nombre = "Gustavo M.", hace = "HACE 8 HORAS",
            tipo = TipoReporte.SUGERENCIA,
            cuerpo = "El primer bus de la línea 10 sale 5:40 AM y casi siempre llega vacío al Kenzie. Buen dato si madrugan.",
            utiles = 21, dislikes = 0, comentarios = 4,
            avatarColor = SecondaryContainer, avatarForeground = OnSecondaryContainer
        ),
        ReporteComunidad(
            iniciales = "TP", nombre = "Teresa P.", hace = "HACE 10 HORAS",
            tipo = TipoReporte.OTRO,
            cuerpo = "Ya pusieron techo nuevo en el paradero de Hospital Belén. Por fin no nos mojamos esperando.",
            utiles = 34, dislikes = 0, comentarios = 6,
            avatarColor = PrimaryContainer, avatarForeground = AppPrimary
        )
    )
}
