package com.example.rutautpnative

import com.example.rutautpnative.data.directions.RouteCalculationCache
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RouteCalculationCacheTest {
    @Test fun soloExitosSeGuardanYVencen() = runBlocking {
        var clock = 0L
        var calls = 0
        val cache = RouteCalculationCache<String, Int>(cacheable = { it > 0 }, now = { clock })
        assertEquals(1, cache.get("a") { ++calls })
        assertEquals(1, cache.get("a") { ++calls })
        clock = 300_001
        assertEquals(2, cache.get("a") { ++calls })
        repeat(2) { cache.get("error") { calls++; 0 } }
        assertEquals(4, calls)
    }
    @Test fun cancelarUnConsumidorNoCancelaLaSolicitudDeOtro() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Int>()
        val calls = AtomicInteger()
        val cache = RouteCalculationCache<String, Int>(cacheable = { true })
        val loader: suspend () -> Int = { calls.incrementAndGet(); started.complete(Unit); response.await() }
        val first = async { cache.get("misma", loader) }
        started.await()
        val second = async { cache.get("misma", loader) }
        yield()
        first.cancelAndJoin()
        response.complete(42)
        assertEquals(42, second.await())
        assertEquals(1, calls.get())
    }
    @Test fun cancelarElUltimoConsumidorCancelaLaPeticion() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val cache = RouteCalculationCache<String, Int>(cacheable = { true })
        val task = async {
            cache.get("a") {
                try { started.complete(Unit); awaitCancellation() }
                finally { cancelled.complete(Unit) }
            }
        }
        started.await()
        task.cancelAndJoin()
        withTimeout(2_000) { cancelled.await() }
        assertTrue(task.isCancelled)
    }
}
