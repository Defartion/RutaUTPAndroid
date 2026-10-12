package com.example.rutautpnative.data

import com.example.rutautpnative.model.FotoComunidad
import com.example.rutautpnative.model.ReporteComunidad
import com.example.rutautpnative.model.TipoReporte
import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.ui.theme.AppPrimary
import com.example.rutautpnative.ui.theme.OnSecondaryContainer
import com.example.rutautpnative.ui.theme.OnSurfaceVariant
import com.example.rutautpnative.ui.theme.OnTertiaryContainer
import com.example.rutautpnative.ui.theme.PrimaryContainer
import com.example.rutautpnative.ui.theme.SecondaryContainer
import com.example.rutautpnative.ui.theme.SurfaceContainerHigh
import com.example.rutautpnative.ui.theme.TertiaryContainer

//----Publicaciones de la comunidad (puerto del publicacionesDemo iOS)----
// 44 publicaciones de demostración: 18 de texto + 26 con FOTO de archivo
// (20 de la MPT + 6 de Wikimedia Commons), intercaladas; todas con textos
// ES/EN, atributos y licencias verificables. No vienen de ningún backend.
object ComunidadDatos {

    // (nombre, iniciales)
    private val nombres = listOf(
        "Jorge D." to "JD", "Maria A." to "MA", "Rosa C." to "RC",
        "Luis F." to "LF", "Ana P." to "AP", "Carlos M." to "CM",
        "Gabriela S." to "GS", "Pedro L." to "PL", "Fernanda R." to "FR",
        "Diego V." to "DV", "Lucía T." to "LT", "Marco E." to "ME",
        "Karla B." to "KB", "Renzo Q." to "RQ", "Valeria H." to "VH",
        "Oscar N." to "ON", "Pamela G." to "PG", "Julio C." to "JC",
        "Andrea M." to "AM", "Victor S." to "VS", "Rocío F." to "RF",
        "Héctor Z." to "HZ", "Natalia O." to "NO", "Iván P." to "IP",
        "Silvia R." to "SR", "Bruno A." to "BA", "Katia L." to "KL",
        "Ricardo T." to "RT", "Elena V." to "EV", "Fausto M." to "FM"
    )

    // (cuerpo ES, tipo)
    private val cuerpos = listOf(
        "Micro lleno en Av. Larco. Pasaron 3 sin parar hacia la UTP." to TipoReporte.ALERTA,
        "Demora en Óvalo Papal por obras. Considerar 10 min adicionales." to TipoReporte.TRAFICO,
        "Tomar Av. Miraflores a las 7:30 AM evita el tráfico de España." to TipoReporte.SUGERENCIA,
        "El chofer de la C-01 muy amable, esperó a una señora mayor que corría." to TipoReporte.OTRO,
        "Cuidado con los carteristas en el paradero del Mercado Mayorista, hora punta." to TipoReporte.ALERTA,
        "Colapso total en Av. América Sur desde las 6 PM, mejor ir por Mansiche." to TipoReporte.TRAFICO,
        "La línea C-07 va despejada sábados por la mañana, casi siempre hay asiento." to TipoReporte.SUGERENCIA,
        "Paradero frente a la UTP sin luz desde el lunes, Reporté al 105." to TipoReporte.ALERTA,
        "Tráfico lento en Av. César Vallejo por desfile, tomar La Ribera." to TipoReporte.TRAFICO,
        "Tip: bajarse 1 cuadra antes de la UTP por Piérola ahorra 5 min de embotellamiento." to TipoReporte.SUGERENCIA,
        "Moto-taxista se pasó el semáforo en España con Mansiche. Suerte que frenó a tiempo." to TipoReporte.ALERTA,
        "En el centro de Trujillo, revisen el paradero de su línea antes de salir los domingos." to TipoReporte.TRAFICO,
        "El micro de las 6:20 AM llega vacío al paradero de Urb. El Recreo." to TipoReporte.SUGERENCIA,
        "Se accidentó un combi cerca del Óvalo Faustino Sánchez, colapso 40 min." to TipoReporte.ALERTA,
        "Ruta M-05 toma caminos raros para evitar tráfico, pero llega rápido." to TipoReporte.OTRO,
        "Tarifa S/ 2.50 en la C-01 confirmado. Algunos intentan cobrar más de noche." to TipoReporte.ALERTA,
        "Si Av. Larco está congestionada al mediodía, revisen el recorrido antes de ir al paradero." to TipoReporte.TRAFICO,
        "Los paraderos nuevos de Av. España tienen techo y cámaras, bien ahí." to TipoReporte.OTRO
    )

    private val cuerposIngles = listOf(
        "Buses are full on Av. Larco. Three passed without stopping on the way to UTP.",
        "Roadworks are causing delays at Óvalo Papal. Allow an extra 10 minutes.",
        "Taking Av. Miraflores at 7:30 AM avoids traffic on España.",
        "The C-01 driver was very kind and waited for an older woman running to the stop.",
        "Watch out for pickpockets at the Mercado Mayorista stop during rush hour.",
        "Av. América Sur is gridlocked after 6 PM. Mansiche may be a better option.",
        "The C-07 is quiet on Saturday mornings; seats are usually available.",
        "The stop across from UTP has had no lighting since Monday. I reported it to 105.",
        "A parade is slowing traffic on Av. César Vallejo. Try La Ribera.",
        "Tip: getting off one block before UTP on Piérola avoids about 5 minutes of traffic.",
        "A mototaxi ran a red light at España and Mansiche. Luckily it stopped in time.",
        "In downtown Trujillo, check your line's stop before heading out on Sundays.",
        "The 6:20 AM bus reaches the Urb. El Recreo stop nearly empty.",
        "A combi crashed near Óvalo Faustino Sánchez. Traffic has been blocked for 40 minutes.",
        "The M-05 takes unusual detours to avoid traffic, but arrives quickly.",
        "The C-01 fare is S/ 2.50. Some drivers try to charge more at night.",
        "If Av. Larco is busy at midday, check the route before heading to the stop.",
        "The new stops on Av. España have roofs and cameras. Great improvement."
    )

    private val tiempos = listOf("HACE 3 MIN", "HACE 8 MIN", "HACE 12 MIN", "HACE 20 MIN", "HACE 35 MIN", "HACE 1 HORA", "HACE 2 HORAS")

    // (nombre, iniciales, tipo, foto, cuerpo ES, cuerpo EN) — Wikimedia.
    private val ilustradosWikimedia = listOf(
        Ilustrado("Andrea M.", "AM", TipoReporte.ALERTA, FotoComunidad.centro,
            "Ojo al esperar el micro en el centro: hay vehículos junto a la vereda. Busquen un punto de subida que deje libre el paso peatonal.",
            "Take care while waiting for a bus downtown: vehicles are next to the sidewalk. Choose a boarding point that keeps pedestrian access clear."),
        Ilustrado("Víctor S.", "VS", TipoReporte.TRAFICO, FotoComunidad.papal,
            "Los accesos al Óvalo Papal pueden demorar el viaje. Salgan con tiempo y revisen su línea antes de ir al paradero.",
            "The approaches to Óvalo Papal can delay your trip. Leave with time to spare and check your line before heading to the stop."),
        Ilustrado("Rocío F.", "RF", TipoReporte.SUGERENCIA, FotoComunidad.pizarro,
            "Para moverme a pie por el centro prefiero el paseo Pizarro. Al buscar un micro, reviso en el mapa el paradero de subida fuera del tramo peatonal.",
            "I prefer the Pizarro pedestrian street when walking downtown. To catch a bus, I check the map for a boarding stop outside the pedestrian section."),
        Ilustrado("Héctor Z.", "HZ", TipoReporte.OTRO, FotoComunidad.papal,
            "Comparto esta referencia del Óvalo Papal para quienes recién conocen Trujillo. Confirmen el sentido de su línea antes de abordar.",
            "Sharing this reference of Óvalo Papal for newcomers to Trujillo. Check your line's direction before boarding."),
        Ilustrado("Natalia O.", "NO", TipoReporte.ALERTA, FotoComunidad.pizarro,
            "Al salir del paseo Pizarro, atentos a los cruces con calles vehiculares. Antes de seguir hacia el paradero, miren ambos lados.",
            "Watch for crossings with vehicle traffic when leaving Pizarro street. Look both ways before continuing to your stop."),
        Ilustrado("Iván P.", "IP", TipoReporte.TRAFICO, FotoComunidad.centro,
            "En las calles del centro se comparte espacio con taxis y vehículos de reparto. Evitemos pedir al micro que se detenga en una esquina.",
            "Downtown streets share space with taxis and delivery vehicles. Avoid asking the bus to stop at a corner.")
    )

    // (foto, tipo, cuerpo ES, cuerpo EN) — archivo MPT; las personas se toman
    // del mismo ciclo de nombres que el iOS (indices 18+).
    private val fotosViales = listOf(
        Vial(FotoComunidad.cierreCentro, TipoReporte.ALERTA,
            "Si encuentras una calle cerrada en el centro de Trujillo, reporta la esquina y explica por dónde continúa tu micro.",
            "If you find a closed street in downtown Trujillo, report the junction and explain where your bus continues."),
        Vial(FotoComunidad.inundacionCalles, TipoReporte.ALERTA,
            "Una calle inundada puede impedir llegar al paradero. En Reportar, marca el tramo afectado y menciona si tu línea cambia de recorrido.",
            "A flooded street can block access to a stop. In Report, mark the affected section and mention whether your line changes its route."),
        Vial(FotoComunidad.pistaLiverpool, TipoReporte.ALERTA,
            "Los huecos en la pista pueden obligar al micro a cambiar de carril. Reporta el daño con una referencia de la calle y el sentido del recorrido.",
            "Potholes can force a bus to change lanes. Report the damage with a street landmark and direction of travel."),
        Vial(FotoComunidad.obraTeheran, TipoReporte.TRAFICO,
            "Cuando una obra deja un tramo sin pavimento, confirma tu punto de subida. Si el transporte se desvía, indica dónde comienza el cambio.",
            "When roadworks leave a section without pavement, check your boarding point. If the bus takes a detour, indicate where the change starts."),
        Vial(FotoComunidad.pistaSalaverry, TipoReporte.SUGERENCIA,
            "En Salaverry, un reporte de pavimento deteriorado será más útil si incluye la cuadra, el cruce y cómo afecta al paso del transporte.",
            "On Salaverry, a damaged-pavement report is more useful when it includes the block, junction and effect on transit."),
        Vial(FotoComunidad.asfaltoLarco, TipoReporte.TRAFICO,
            "Si hay trabajos de asfaltado en Larco, señala el carril afectado y la línea que toma otra calle. La foto es una referencia de archivo.",
            "If asphalt works affect Larco, identify the affected lane and line taking another street. This is an archive reference photo."),
        Vial(FotoComunidad.aniegosTrujillo, TipoReporte.ALERTA,
            "Agua junto al paradero: describe si afecta el paso peatonal o el carril del micro. Marca el punto sin acercarte al agua acumulada.",
            "Water beside a stop: describe whether it affects pedestrian access or the bus lane. Mark the location without approaching the standing water."),
        Vial(FotoComunidad.concretoAmerica, TipoReporte.TRAFICO,
            "Una intersección en reparación puede cambiar el recorrido nocturno. Reporta el cruce, tu línea y la calle por la que sigue el micro.",
            "Junction repairs can change a nighttime route. Report the junction, your line and the street the bus continues along."),
        Vial(FotoComunidad.parchaFatima, TipoReporte.SUGERENCIA,
            "Las reparaciones pequeñas también pueden mover el punto de subida. En Fátima, toma como referencia la esquina y mantén libre el área de trabajo.",
            "Small repairs can also move the boarding point. On Fátima, use a junction as a landmark and keep the work area clear."),
        Vial(FotoComunidad.drenajeTrujillo, TipoReporte.ALERTA,
            "Una excavación de drenaje puede dificultar el acceso al paradero. Reporta si afecta la vereda, el carril o ambos.",
            "Drainage excavations can make a stop harder to reach. Report whether they affect the sidewalk, lane or both."),
        Vial(FotoComunidad.parchadoEspana, TipoReporte.TRAFICO,
            "Si una reparación en España obliga a cambiar de paradero, marca el tramo desde Reportar y describe dónde está parando tu transporte.",
            "If repairs on España require a different stop, mark the section in Report and describe where your bus is stopping."),
        Vial(FotoComunidad.reparacionCalles, TipoReporte.TRAFICO,
            "El retiro de pavimento puede cerrar una cuadra por etapas. Para reportarlo, distingue entre cierre total y paso por un solo carril.",
            "Pavement removal can close a block in stages. When reporting it, distinguish between a full closure and traffic using one lane."),
        Vial(FotoComunidad.excavacionLarco, TipoReporte.ALERTA,
            "Si el Óvalo Larco está cerrado por una obra, señala el acceso bloqueado y la referencia del paradero alternativo que usa tu línea.",
            "If Óvalo Larco is closed for roadworks, identify the blocked approach and the alternative stop used by your line."),
        Vial(FotoComunidad.pavimentacionLarco, TipoReporte.TRAFICO,
            "Durante el asfaltado, los accesos al óvalo pueden variar. Un reporte ayuda más si indica por dónde se está desviando el transporte.",
            "During paving, access to the roundabout may change. A report is more helpful when it explains where transit is being diverted."),
        Vial(FotoComunidad.maquinariaTrujillo, TipoReporte.TRAFICO,
            "Material y maquinaria pueden reducir el espacio de circulación. Describe el tramo ocupado y si el micro necesita pasar por otra calle.",
            "Material and machinery can reduce road space. Describe the occupied section and whether the bus needs to use another street."),
        Vial(FotoComunidad.reparacionEspana, TipoReporte.SUGERENCIA,
            "Una barrera temporal puede bloquear el lugar habitual de subida. Antes de abordar en España, revisa el punto y reporta el cambio si afecta tu ruta.",
            "A temporary barrier can block the usual boarding point. Before boarding on España, check the location and report a change affecting your route."),
        Vial(FotoComunidad.obraSanNicolas, TipoReporte.TRAFICO,
            "En una calle en obras de San Nicolás, anota la cuadra y el sentido afectado. Eso permite ubicar mejor el desvío de tu línea.",
            "On a street under repair in San Nicolás, note the block and affected direction. This helps locate your line's detour."),
        Vial(FotoComunidad.obraGonzalezPrada, TipoReporte.TRAFICO,
            "Conos y excavadoras pueden indicar un cierre por etapas. Si cambia tu recorrido en González Prada, reporta dónde empieza y termina el tramo.",
            "Cones and excavators may indicate a staged closure. If your route changes on González Prada, report where the affected section begins and ends."),
        Vial(FotoComunidad.asfaltoRoma, TipoReporte.TRAFICO,
            "Si hay pavimentación en Roma, verifica el acceso a tu paradero. En Reportar, indica si la línea mantiene su recorrido o toma un desvío.",
            "If paving affects Roma, check access to your stop. In Report, indicate whether the line keeps its route or takes a detour."),
        Vial(FotoComunidad.mantenimientoTrujillo, TipoReporte.SUGERENCIA,
            "Para reportar mantenimiento de pistas en Trujillo, incluye una esquina cercana, el carril afectado y cualquier cambio de parada.",
            "To report road maintenance in Trujillo, include a nearby junction, the affected lane and any stop changes.")
    )

    private data class Ilustrado(
        val nombre: String, val iniciales: String, val tipo: TipoReporte,
        val foto: FotoComunidad, val cuerpo: String, val cuerpoIngles: String
    )

    private data class Vial(
        val foto: FotoComunidad, val tipo: TipoReporte,
        val cuerpo: String, val cuerpoIngles: String
    )

    // Estilos de avatar ciclicos (iOS: primario/secundario/terciario).
    private data class EstiloAvatar(val fondo: Color, val frente: Color)

    private val estilos = listOf(
        EstiloAvatar(PrimaryContainer, AppPrimary),
        EstiloAvatar(SecondaryContainer, OnSecondaryContainer),
        EstiloAvatar(TertiaryContainer, OnTertiaryContainer)
    )

    private fun estiloSegundo() = EstiloAvatar(SecondaryContainer, OnSecondaryContainer)

    val publicaciones: List<ReporteComunidad> = buildList {
        // 18 originales de texto.
        val originales = cuerpos.mapIndexed { i, par ->
            val persona = nombres[i % nombres.size]
            val estilo = estilos[i % estilos.size]
            ReporteComunidad(
                iniciales = persona.second,
                nombre = persona.first,
                hace = tiempos[(i * 3 + 1) % tiempos.size],
                tipo = par.second,
                cuerpo = par.first,
                cuerpoIngles = cuerposIngles[i],
                utiles = 6 + (i * 13) % 78,
                dislikes = 1 + (i * 7) % 9,
                comentarios = (i * 5) % 11,
                utilMarcado = i % 6 == 0,
                avatarColor = estilo.fondo,
                avatarForeground = estilo.frente
            )
        }

        // 26 ilustradas: 20 MPT (personas del ciclo 18+) + 6 Wikimedia.
        val ilustrados = buildList {
            fotosViales.forEachIndexed { index, vial ->
                val persona = nombres[(index + 18) % nombres.size]
                add(
                    Ilustrado(persona.first, persona.second, vial.tipo, vial.foto, vial.cuerpo, vial.cuerpoIngles)
                )
            }
            addAll(ilustradosWikimedia)
        }

        // Intercalar: una con foto, una de texto (todas legibles al deslizar).
        var restantes = originales.toList()
        ilustrados.forEachIndexed { index, dato ->
            val estilo = estiloSegundo()
            add(
                ReporteComunidad(
                    iniciales = dato.iniciales,
                    nombre = dato.nombre,
                    hace = "HACE 3 MIN",
                    tipo = dato.tipo,
                    cuerpo = dato.cuerpo,
                    cuerpoIngles = dato.cuerpoIngles,
                    foto = dato.foto,
                    utiles = 8 + index * 3,
                    comentarios = 2,
                    avatarColor = estilo.fondo,
                    avatarForeground = estilo.frente
                )
            )
            restantes.firstOrNull()?.let {
                add(it)
                restantes = restantes.drop(1)
            }
        }
        addAll(restantes)
    }

    /// Conteo real de alertas para la barra resumen de Seguridad (el "2"
    /// fijo anterior): cuenta las publicaciones tipo ALERTA del feed.
    val alertasHoy: Int get() = publicaciones.count { it.tipo == TipoReporte.ALERTA }
}
