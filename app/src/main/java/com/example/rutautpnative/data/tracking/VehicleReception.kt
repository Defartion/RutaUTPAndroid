package com.example.rutautpnative.data.tracking

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

enum class VehicleDataOrigin { OBSERVED, SIMULATED, LEGACY_UNKNOWN, UNKNOWN }

/** Contratos v2/v3 y control del backend de main (138900d), independientes de Android. */
object VehicleTrackingWire {
    const val CONTROL_TOPIC = "rutautp/simulacion/estado"
    const val MAX_REVISION = 9_007_199_254_740_991L
    private val base = setOf("vehicleId", "routeId", "linea", "lat", "lon", "speed", "heading", "timestamp")
    private val metadata = setOf("schemaVersion", "dataOrigin", "simulationRunId", "controlRevision")
    fun uuid(text: String?): String? = text?.takeIf { it.length == 36 }?.let {
        runCatching { UUID.fromString(it).toString() }.getOrNull()?.takeIf { id -> id.equals(it, true) }
    }
    private fun identifier(text: String) = text.matches(Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,127}"))
    private fun obj(bytes: ByteArray): JsonObject? = if (bytes.size > 4096) null else runCatching { Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }.getOrNull()
    fun position(bytes: ByteArray, topic: String, retained: Boolean = false): VehiclePosition? = runCatching {
        if (retained) return null
        val o = obj(bytes) ?: return null
        val legacy = "schemaVersion" !in o
        val version = if (legacy) 1 else o.getValue("schemaVersion").jsonPrimitive.int
        val keys = if (legacy) base else base + metadata + if (version == 3) setOf("scenarioOccupancy", "occupancyScriptVersion") else emptySet()
        if ((!legacy && version !in 2..3) || o.keys != keys) return null
        fun str(k: String): String { val p = o.getValue(k).jsonPrimitive; require(p.isString); return p.content }
        fun number(k: String): Double { val p = o.getValue(k).jsonPrimitive; require(!p.isString); return p.double }
        val id = str("vehicleId"); val route = str("routeId"); val line = str("linea")
        if (!identifier(id) || !identifier(route) || line.isBlank() || line.length > 128 || line != line.trim() || line.any { it.isISOControl() }) return null
        if (topic != "rutautp/vehiculos/$id/posicion") return null
        val lat = number("lat"); val lon = number("lon"); val speed = number("speed"); val heading = number("heading"); val timestamp = number("timestamp")
        if (!listOf(lat, lon, speed, heading, timestamp).all { it.isFinite() } || lat !in -90.0..90.0 || lon !in -180.0..180.0 || timestamp < 0) return null
        var origin = VehicleDataOrigin.LEGACY_UNKNOWN
        var run: String? = null; var revision: Long? = null; var occupancy: String? = null
        if (!legacy) {
            origin = when (str("dataOrigin")) { "observed" -> VehicleDataOrigin.OBSERVED; "simulated" -> VehicleDataOrigin.SIMULATED; else -> return null }
            if ((speed != -1.0 && speed !in 0.0..30.0) || (heading != -1.0 && (heading < 0 || heading >= 360))) return null
            if (origin == VehicleDataOrigin.OBSERVED) {
                if (o["simulationRunId"] != JsonNull || o["controlRevision"] != JsonNull) return null
            } else {
                run = uuid(str("simulationRunId")) ?: return null
                revision = o.getValue("controlRevision").jsonPrimitive.takeUnless { it.isString }?.long ?: return null
                if (revision !in 1..MAX_REVISION || !id.startsWith("sim:$run:") || id.length <= "sim:$run:".length) return null
            }
            if (version == 3) {
                if (origin != VehicleDataOrigin.SIMULATED || o["occupancyScriptVersion"]?.jsonPrimitive?.int != 1) return null
                occupancy = str("scenarioOccupancy").takeIf { it in setOf("empty", "space", "full") } ?: return null
            }
        }
        if (origin != VehicleDataOrigin.SIMULATED && id.startsWith("sim:", true)) return null
        VehiclePosition(id, line, route, lat, lon, heading, speed, timestamp, origin, run, revision, occupancy)
    }.getOrNull()

    fun control(bytes: ByteArray): SimulationControlState? = runCatching {
        val o = obj(bytes) ?: return null
        if (o.keys != setOf("schemaVersion", "enabled", "runId", "controlRevision", "issuedAt", "validUntil", "serviceEndsAt", "timeZone")) return null
        Json.decodeFromString<SimulationControlState>(bytes.toString(Charsets.UTF_8)).takeIf { it.valid() }?.let { it.copy(runId = uuid(it.runId)) }
    }.getOrNull()
}

@Serializable
data class SimulationControlState(val schemaVersion: Int, val enabled: Boolean, val runId: String?, val controlRevision: Long,
    val issuedAt: Double, val validUntil: Double, val serviceEndsAt: Double, val timeZone: String) {
    fun valid(): Boolean = schemaVersion == 1 && timeZone == "America/Lima" && controlRevision in 0..VehicleTrackingWire.MAX_REVISION &&
        (runId == null || VehicleTrackingWire.uuid(runId) != null) && listOf(issuedAt, validUntil, serviceEndsAt).all { it.isFinite() && it >= 0 } &&
        (controlRevision != 0L || (!enabled && runId == null)) &&
        if (enabled) runId != null && controlRevision > 0 && issuedAt < validUntil && validUntil <= serviceEndsAt && validUntil - issuedAt <= 15
        else validUntil == issuedAt
}

@Serializable
data class SimulationCheckpoint(val version: Int = 1, val lastControl: SimulationControlState? = null,
    val retiredRuns: Set<String> = emptySet(), val exhausted: Boolean = false) {
    fun valid() = version == 1 && retiredRuns.size <= 256 && retiredRuns.all { VehicleTrackingWire.uuid(it) == it } &&
        (lastControl?.valid() != false) && (exhausted || lastControl?.enabled != true || lastControl.runId !in retiredRuns)
}

class SimulationControlGate(checkpoint: SimulationCheckpoint = SimulationCheckpoint()) {
    private var last = checkpoint.lastControl
    private val retired = checkpoint.retiredRuns.toMutableSet()
    private var exhausted = checkpoint.exhausted
    private var lease: SimulationControlState? = null
    private var expiresUptime = 0.0
    private var previousWall: Double? = null
    private var previousUptime: Double? = null
    private var requireIssuedAfter: Double? = null
    var legacyBlocked = last?.enabled == true
        private set
    val checkpoint: SimulationCheckpoint get() = SimulationCheckpoint(lastControl = last, retiredRuns = retired.toSet(), exhausted = exhausted)
    fun invalidate(blockLegacy: Boolean = false) { lease = null; expiresUptime = 0.0; if (blockLegacy) legacyBlocked = true }
    fun checkTime(wall: Double, uptime: Double): Boolean {
        val oldWall = previousWall; val oldUp = previousUptime
        previousWall = wall; previousUptime = uptime
        if (!wall.isFinite() || !uptime.isFinite() || wall < 0 || uptime < 0 ||
            (oldWall != null && oldUp != null && (wall < oldWall || uptime < oldUp || abs((wall - oldWall) - (uptime - oldUp)) > 2))) {
            requireIssuedAfter = last?.issuedAt; invalidate(true); return false
        }
        return true
    }
    fun receive(state: SimulationControlState, wall: Double, uptime: Double): Boolean {
        val clockOK = checkTime(wall, uptime)
        if (!state.valid() || exhausted || (!clockOK && state.enabled)) return false
        if (state.issuedAt > wall + 10) { invalidate(true); return false }
        val old = last
        if (old != null) {
            if (state.controlRevision < old.controlRevision) return false
            if (state.controlRevision == old.controlRevision) {
                if (state.enabled != old.enabled || state.runId != old.runId || state.serviceEndsAt != old.serviceEndsAt || (state.issuedAt == old.issuedAt && state != old)) { invalidate(true); return false }
                if (state.issuedAt < old.issuedAt) return false
            }
        }
        if (state.enabled && state.runId?.lowercase() in retired) { invalidate(true); return false }
        fun retire(run: String) {
            if (retired.size >= 256 && run !in retired) exhausted = true else retired.add(run)
        }
        if (old != null && state.controlRevision > old.controlRevision && old.runId != null && (old.runId != state.runId || !state.enabled)) retire(old.runId.lowercase())
        if (!state.enabled) state.runId?.let { retire(it.lowercase()) }
        if (exhausted) { invalidate(true); return false }
        val duplicate = state == old
        last = state
        legacyBlocked = state.enabled
        if (!state.enabled) { invalidate(); return true }
        if (requireIssuedAfter?.let { state.issuedAt <= it } == true) { invalidate(true); return true }
        if (!duplicate || lease == null) {
            lease = state
            expiresUptime = uptime + min(15.0, (min(state.validUntil, state.serviceEndsAt) - wall).coerceAtLeast(0.0))
        }
        return true
    }
    fun permits(p: VehiclePosition, wall: Double, uptime: Double, allowLegacy: Boolean = false): Boolean = when (p.dataOrigin) {
        VehicleDataOrigin.OBSERVED -> true
        VehicleDataOrigin.LEGACY_UNKNOWN -> allowLegacy && !legacyBlocked && !exhausted
        VehicleDataOrigin.UNKNOWN -> false
        VehicleDataOrigin.SIMULATED -> lease?.let {
            !exhausted && wall >= it.issuedAt && wall < min(it.validUntil, it.serviceEndsAt) && uptime < expiresUptime &&
                p.simulationRunId == it.runId?.lowercase() && p.controlRevision == it.controlRevision
        } == true
    }
}
