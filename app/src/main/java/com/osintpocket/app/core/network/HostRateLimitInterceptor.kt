package com.osintpocket.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.InterruptedIOException

/**
 * Espaciado mínimo entre peticiones al mismo host. Reserva un "hueco" temporal por petición,
 * así varias llamadas concurrentes al mismo servicio se ponen en fila en lugar de dispararse a la vez.
 */
class HostRateLimitInterceptor(
    private val intervalsMs: Map<String, Long>,
    private val defaultIntervalMs: Long,
) : Interceptor {

    private val nextSlotNs = HashMap<String, Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val intervalNs = (intervalsMs[host] ?: defaultIntervalMs) * 1_000_000L

        val waitNs = synchronized(nextSlotNs) {
            val now = System.nanoTime()
            val slot = maxOf(now, nextSlotNs[host] ?: now)
            nextSlotNs[host] = slot + intervalNs
            slot - now
        }

        if (waitNs > 0) {
            try {
                Thread.sleep(waitNs / 1_000_000L, (waitNs % 1_000_000L).toInt())
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Petición cancelada")
            }
        }
        return chain.proceed(chain.request())
    }
}
