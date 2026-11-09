package com.example.rutautpnative.data.directions

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Caché de éxitos y solicitudes compartidas con cancelación por consumidor, como RouteCalculationService.swift. */
class RouteCalculationCache<K, V>(private val cacheable: (V) -> Boolean, private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val ttlMs: Long = 300_000, private val limit: Int = 128) {
    private data class Entry<V>(val value: V, val at: Long)
    private data class Pending<V>(val task: Deferred<V>, var consumers: Int = 0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val cache = linkedMapOf<K, Entry<V>>()
    private val requests = mutableMapOf<K, Pending<V>>()
    suspend fun get(key: K, loader: suspend () -> V): V {
        currentCoroutineContext().ensureActive()
        val pending = mutex.withLock {
            cache.entries.removeAll { now() - it.value.at !in 0 until ttlMs }
            cache[key]?.let { return it.value }
            val request = requests[key] ?: Pending(scope.async { loader() }).also { requests[key] = it }
            request.consumers++
            request
        }
        try {
            val value = pending.task.await()
            mutex.withLock {
                if (cacheable(value)) {
                    cache.remove(key)
                    while (cache.size >= limit && cache.isNotEmpty()) cache.remove(cache.keys.first())
                    if (limit > 0) cache[key] = Entry(value, now())
                }
            }
            return value
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    pending.consumers--
                    if (pending.consumers == 0) {
                        if (requests[key] === pending) requests.remove(key)
                        if (pending.task.isActive) pending.task.cancel()
                    }
                }
            }
        }
    }
}
