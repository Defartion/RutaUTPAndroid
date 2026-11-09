package com.example.rutautpnative.model

import com.example.rutautpnative.ui.idioma.L

//----Fotos de archivo para publicaciones de la comunidad (FotoComunidad.swift)----
// Procedencia verificable: Wikimedia Commons (con licencia declarada) y el
// archivo de noticias de la MPT (sin licencia, fecha de la publicación
// fuente). Se cargan desde assets/comunidad/<asset>.jpg.
data class FotoComunidad(
    val asset: String,
    val lugar: String,
    val autor: String,
    val fecha: String,
    val fuente: String,
    // Solo se indica una licencia cuando la fuente la declara expresamente.
    val licencia: String? = null,
    val licenciaURL: String? = null,
    val fechaEsPublicacion: Boolean = false,
    val descripcion: String = "",
    val descripcionIngles: String = ""
) {
    val descripcionLocalizada: String
        get() = if (L.esIngles) descripcionIngles else descripcion

    val atribucion: String
        get() {
            val fechaVisible = if (fechaEsPublicacion) L.t("Publicada: ", "Published: ") + fecha else fecha
            return listOf(autor, fechaVisible, licencia).filterNotNull().joinToString(" · ")
        }

    companion object {
        //----Wikimedia Commons (licencias declaradas)----
        val pizarro = FotoComunidad(
            asset = "comunidad-pizarro", lugar = "Jirón Pizarro · Trujillo",
            autor = "EACC", fecha = "2012",
            fuente = "https://commons.wikimedia.org/wiki/File:Jir%C3%B3n_Pizarro.jpg",
            licencia = "CC BY-SA 3.0", licenciaURL = "https://creativecommons.org/licenses/by-sa/3.0/"
        )
        val centro = FotoComunidad(
            asset = "comunidad-centro", lugar = "Centro de Trujillo",
            autor = "Pitxiquin", fecha = "2017",
            fuente = "https://commons.wikimedia.org/wiki/File:Carrers_del_centre_de_Trujillo.jpg",
            licencia = "CC BY-SA 4.0", licenciaURL = "https://creativecommons.org/licenses/by-sa/4.0/"
        )
        val papal = FotoComunidad(
            asset = "comunidad-papal", lugar = "Óvalo Papal · Trujillo",
            autor = "Latimax", fecha = "2011",
            fuente = "https://commons.wikimedia.org/wiki/File:Ovalo_papal_-_Trujillo_,Per%C3%BA.jpg",
            licencia = "CC BY-SA 3.0", licenciaURL = "https://creativecommons.org/licenses/by-sa/3.0/"
        )

        //----Archivo de la Municipalidad Provincial de Trujillo (MPT)----
        // Las fechas corresponden a las publicaciones fuente (gob.pe).
        val cierreCentro = FotoComunidad(
            asset = "comunidad-trujillo-cierre-centro", lugar = "Centro histórico · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-08-07",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1427794-mejoran-pistas-y-veredas-en-jirones-independencia-y-san-martin",
            fechaEsPublicacion = true,
            descripcion = "Señal de calle cerrada, conos y barreras en una vía del centro histórico.",
            descripcionIngles = "Road closed sign, cones and barriers on a downtown street."
        )
        val inundacionCalles = FotoComunidad(
            asset = "comunidad-trujillo-inundacion-calles", lugar = "Calles de Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-01-12",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1331756-mpt-responde-de-manera-inmediata-para-limpiar-y-recuperar-calles-inundadas-por-fuerte-lluvia",
            fechaEsPublicacion = true,
            descripcion = "Agua acumulada en una calle y personal trabajando con una unidad de succión.",
            descripcionIngles = "Standing water on a street and workers using a suction truck."
        )
        val pistaLiverpool = FotoComunidad(
            asset = "comunidad-trujillo-pista-liverpool", lugar = "Sector Liverpool y Aguamarina · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-06-05",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1403825-mpt-mejorara-calles-liverpool-y-aguamarina-desde-julio",
            fechaEsPublicacion = true,
            descripcion = "Pavimento desgastado, grietas y un hueco en una calle con vehículos.",
            descripcionIngles = "Worn pavement, cracks and a pothole on a street with vehicles."
        )
        val obraTeheran = FotoComunidad(
            asset = "comunidad-trujillo-obra-teheran", lugar = "Teherán, Filadelfia y Las Gemas · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2025-12-02",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1303730-30-de-avance-en-mejora-de-pistas-en-las-calles-teheran-filadelfia-y-las-gemas",
            fechaEsPublicacion = true,
            descripcion = "Vista aérea de una calle con pavimento retirado y trabajos sobre la vía.",
            descripcionIngles = "Aerial view of a street with removed pavement and roadworks."
        )
        val pistaSalaverry = FotoComunidad(
            asset = "comunidad-trujillo-pista-salaverry", lugar = "Jirón Salaverry · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2025-05-19",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1171778-jiron-salaverry-comienza-a-ser-renovado-entre-av-roma-y-av-espana",
            fechaEsPublicacion = true,
            descripcion = "Calle urbana con parches y pavimento deteriorado junto a viviendas y comercios.",
            descripcionIngles = "An urban street with patches and worn pavement beside homes and shops."
        )
        val asfaltoLarco = FotoComunidad(
            asset = "comunidad-trujillo-asfalto-larco", lugar = "Av. Larco · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-05-12",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1390807-70-de-avance-tiene-obra-de-la-mpt-en-av-larco",
            fechaEsPublicacion = true,
            descripcion = "Trabajadores y maquinaria colocando una nueva capa de asfalto.",
            descripcionIngles = "Workers and machinery laying a new asphalt layer."
        )
        val aniegosTrujillo = FotoComunidad(
            asset = "comunidad-trujillo-aniegos-trujillo", lugar = "Vías urbanas · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2025-11-18",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1292184-mas-de-40-mil-litros-de-agua-de-lluvias-dreno-el-segat",
            fechaEsPublicacion = true,
            descripcion = "Personal retirando agua de una avenida con buses, vehículos y paso peatonal.",
            descripcionIngles = "Workers removing water from an avenue with buses, vehicles and pedestrian access."
        )
        val concretoAmerica = FotoComunidad(
            asset = "comunidad-trujillo-concreto-america", lugar = "América y Jesús de Nazaret · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2025-08-26",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1234882-empezaron-a-vaciar-concreto-en-intersecciones-viales-de-trujillo",
            fechaEsPublicacion = true,
            descripcion = "Trabajadores vaciando concreto en una intersección durante la noche.",
            descripcionIngles = "Workers pouring concrete at a junction at night."
        )
        val parchaFatima = FotoComunidad(
            asset = "comunidad-trujillo-parcha-fatima", lugar = "Av. Fátima · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2024-07-26",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/996931-parcha-movil-de-la-mpt-sigue-avanzando-en-el-mantenimiento-vial",
            fechaEsPublicacion = true,
            descripcion = "Cuadrilla reparando un sector de pavimento junto a conos de tránsito.",
            descripcionIngles = "A crew repairing a section of pavement beside traffic cones."
        )
        val drenajeTrujillo = FotoComunidad(
            asset = "comunidad-trujillo-drenaje-trujillo", lugar = "Red de drenaje · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-06-22",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1410836-la-mpt-esta-ejecutando-un-plan-de-accion-ante-lluvias",
            fechaEsPublicacion = true,
            descripcion = "Rejilla y tapa de drenaje junto a tierra removida y un cono en la vía.",
            descripcionIngles = "A drain grate and cover beside excavated soil and a cone on the road."
        )
        val parchadoEspana = FotoComunidad(
            asset = "comunidad-trujillo-parchado-espana", lugar = "Av. España · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2025-10-14",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1264299-mas-de-1-400-m2-de-pista-parcho-la-mpt-en-la-avenida-espana",
            fechaEsPublicacion = true,
            descripcion = "Trabajadores extendiendo asfalto en un carril delimitado por barreras.",
            descripcionIngles = "Workers spreading asphalt in a lane separated by barriers."
        )
        val reparacionCalles = FotoComunidad(
            asset = "comunidad-trujillo-reparacion-calles", lugar = "Vías urbanas · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-01-29",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1346294-municipalidad-inicia-trabajos-de-reparacion-vial-en-importantes-calles-de-trujillo",
            fechaEsPublicacion = true,
            descripcion = "Excavadora y trabajador retirando pavimento en una calle urbana.",
            descripcionIngles = "An excavator and worker removing pavement on an urban street."
        )
        val excavacionLarco = FotoComunidad(
            asset = "comunidad-trujillo-excavacion-larco", lugar = "Óvalo Larco · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-03-30",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1373288-ovalo-larco-esta-cerrado-al-transito-hasta-este-5-de-abril",
            fechaEsPublicacion = true,
            descripcion = "Maquinaria pesada, tierra removida y personal de obra en el óvalo.",
            descripcionIngles = "Heavy machinery, excavated soil and roadworkers at the roundabout."
        )
        val pavimentacionLarco = FotoComunidad(
            asset = "comunidad-trujillo-pavimentacion-larco", lugar = "Óvalo Larco · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-04-14",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1378970-nuevo-paquete-de-obras-anuncia-la-mpt",
            fechaEsPublicacion = true,
            descripcion = "Cuadrilla y equipo de pavimentación trabajando junto al óvalo.",
            descripcionIngles = "A crew and paving equipment working beside the roundabout."
        )
        val maquinariaTrujillo = FotoComunidad(
            asset = "comunidad-trujillo-maquinaria-trujillo", lugar = "Vías urbanas · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-03-11",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1364865-nuevo-paquete-de-obras-anuncian-para-trujillo",
            fechaEsPublicacion = true,
            descripcion = "Maquinaria y material de construcción sobre una calle en reparación.",
            descripcionIngles = "Machinery and construction material on a street under repair."
        )
        val reparacionEspana = FotoComunidad(
            asset = "comunidad-trujillo-reparacion-espana", lugar = "Av. España · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2025-10-30",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1279381-alcalde-anuncia-mas-obras-para-trujillo",
            fechaEsPublicacion = true,
            descripcion = "Personal reparando asfalto junto a barreras en una vía con tránsito.",
            descripcionIngles = "Workers repairing asphalt beside barriers on a road with traffic."
        )
        val obraSanNicolas = FotoComunidad(
            asset = "comunidad-trujillo-obra-san-nicolas", lugar = "Urb. San Nicolás · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2025-12-05",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1310137-mpt-mejora-pistas-veredas-y-senaletica-en-la-urb-san-nicolas",
            fechaEsPublicacion = true,
            descripcion = "Trabajadores preparando una calle con pavimento retirado en una urbanización.",
            descripcionIngles = "Workers preparing a street with removed pavement in a residential area."
        )
        val obraGonzalezPrada = FotoComunidad(
            asset = "comunidad-trujillo-obra-gonzalez-prada", lugar = "Av. González Prada · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-07-08",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1417936-mas-obras-para-trujillo-anuncia-la-mpt",
            fechaEsPublicacion = true,
            descripcion = "Excavadora, conos y trabajadores sobre una avenida con el pavimento retirado.",
            descripcionIngles = "An excavator, cones and workers on an avenue with removed pavement."
        )
        val asfaltoRoma = FotoComunidad(
            asset = "comunidad-trujillo-asfalto-roma", lugar = "Av. Roma · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2025-08-26",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1234887-colocacion-de-nuevo-pavimento-en-av-roma-tiene-70-de-avance",
            fechaEsPublicacion = true,
            descripcion = "Cuadrilla extendiendo asfalto en una avenida junto a viviendas.",
            descripcionIngles = "A crew spreading asphalt on an avenue beside homes."
        )
        val mantenimientoTrujillo = FotoComunidad(
            asset = "comunidad-trujillo-mantenimiento-trujillo", lugar = "Vías urbanas · Trujillo",
            autor = "Municipalidad Provincial de Trujillo", fecha = "2026-01-16",
            fuente = "https://www.gob.pe/institucion/munitrujillo/noticias/1337119-municipalidad-de-trujillo-ejecuta-mantenimiento-de-pistas-en-distintos-sectores-de-la-ciudad",
            fechaEsPublicacion = true,
            descripcion = "Trabajadores y una unidad municipal reparando un tramo de pavimento.",
            descripcionIngles = "Workers and a municipal vehicle repairing a section of pavement."
        )
    }
}
