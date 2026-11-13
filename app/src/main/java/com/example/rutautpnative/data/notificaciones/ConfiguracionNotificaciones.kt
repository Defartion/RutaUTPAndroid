package com.example.rutautpnative.data.notificaciones

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Serializable
data class RecordatorioViaje(val id: String = UUID.randomUUID().toString(), val minutoDelDia: Int,
    val dias: Set<Int>, val destinoID: String) {
    fun valido() = minutoDelDia in 0..1439 && dias.isNotEmpty() && dias.all { it in 1..7 } && destinoID.isNotBlank()
}

@Serializable
data class ConfiguracionNotificaciones(val version: Int = 1, val recordatorio: RecordatorioViaje? = null,
    val pausaHasta: Long? = null, val diasSilencio: Set<Int> = emptySet(), val silencioHorario: Boolean = false,
    val inicioSilencio: Int = 1320, val finSilencio: Int = 420, val sonido: Boolean = true,
    val diariosActivos: Boolean = false, val cantidadDiaria: Int = 1, val inicioDiario: Int = 540, val finDiario: Int = 1200,
    val diarioID: String = UUID.randomUUID().toString()) {
    fun valida() = version == 1 && (recordatorio?.valido() != false) && diasSilencio.all { it in 1..7 } &&
        inicioSilencio in 0..1439 && finSilencio in 0..1439 && cantidadDiaria in 1..3 &&
        inicioDiario in 0..1439 && finDiario in 1..1439 && finDiario - inicioDiario >= cantidadDiaria &&
        (pausaHasta == null || pausaHasta in 0..4_102_444_800_000L)
    /** El aviso pertenece a la configuración que sigue vigente, igual que en iOS. */
    fun aceptaAviso(id: String?, destino: String?, diario: Int?): Boolean {
        if (!valida() || id.isNullOrBlank()) return false
        return when {
            destino != null -> recordatorio?.let { it.id == id && it.destinoID == destino } == true
            diario != null -> diariosActivos && diarioID == id && diario in 0..3
            else -> false
        }
    }
    fun permite(instante: Instant, zona: ZoneId): Boolean {
        val fecha = instante.atZone(zona)
        val dia = fecha.dayOfWeek.value % 7 + 1
        val minuto = fecha.hour * 60 + fecha.minute
        if (pausaHasta?.let { instante.toEpochMilli() < it } == true || dia in diasSilencio) return false
        if (!silencioHorario) return true
        return when {
            inicioSilencio == finSilencio -> false
            inicioSilencio < finSilencio -> minuto !in inicioSilencio until finSilencio
            else -> minuto >= finSilencio && minuto < inicioSilencio
        }
    }
}

data class AvisoProgramado(val fecha: Instant, val destinoID: String? = null, val tipoDiario: Int? = null)

/** Agenda de entregas únicas, calculada en la zona del teléfono como en iOS. */
object ProgramadorRecordatorios {
    fun fechas(config: ConfiguracionNotificaciones, ahora: Instant, zona: ZoneId, limite: Int = 30): List<Instant> {
        val recordatorio = config.recordatorio ?: return emptyList()
        if (!config.valida() || limite <= 0) return emptyList()
        val salida = mutableListOf<Instant>()
        val inicio = maxOf(ahora, config.pausaHasta?.let(Instant::ofEpochMilli) ?: ahora).atZone(zona).toLocalDate()
        repeat(366) { offset ->
            val dia = inicio.plusDays(offset.toLong())
            if (dia.dayOfWeek.value % 7 + 1 in recordatorio.dias) {
                val fecha = dia.atStartOfDay().plusMinutes(recordatorio.minutoDelDia.toLong()).atZone(zona).toInstant()
                if (fecha > ahora && config.permite(fecha, zona)) salida.add(fecha)
                if (salida.size >= limite) return salida
            }
        }
        return salida
    }

    fun diarios(config: ConfiguracionNotificaciones, ahora: Instant, zona: ZoneId, limite: Int = 30): List<AvisoProgramado> {
        if (!config.valida() || !config.diariosActivos || limite <= 0) return emptyList()
        val salida = mutableListOf<AvisoProgramado>()
        val inicio: LocalDate = ahora.atZone(zona).toLocalDate()
        repeat(366) { offset ->
            val dia = inicio.plusDays(offset.toLong())
            repeat(config.cantidadDiaria) { indice ->
                val inferior = config.inicioDiario + (config.finDiario - config.inicioDiario) * indice / config.cantidadDiaria
                val superior = config.inicioDiario + (config.finDiario - config.inicioDiario) * (indice + 1) / config.cantidadDiaria
                val bytes = java.security.MessageDigest.getInstance("SHA-256").digest("${config.diarioID}|$dia|$indice".toByteArray())
                val semilla = java.nio.ByteBuffer.wrap(bytes).long
                // Un horario determinista por franja evita volver a sortear avisos pasados al reabrir.
                val minuto = inferior + Math.floorMod(semilla, (superior - inferior).toLong()).toInt()
                val local = dia.atStartOfDay().plusMinutes(minuto.toLong())
                if (zona.rules.getValidOffsets(local).isNotEmpty()) {
                    val fecha = local.atZone(zona).withEarlierOffsetAtOverlap().toInstant()
                    if (fecha > ahora && config.permite(fecha, zona)) salida.add(AvisoProgramado(fecha, tipoDiario = Math.floorMod(semilla, 4).toInt()))
                }
                if (salida.size >= limite) return salida
            }
        }
        return salida
    }
}
