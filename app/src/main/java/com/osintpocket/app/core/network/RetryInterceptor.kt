package com.osintpocket.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.random.Random

/**
 * Reintentos con backoff exponencial + jitter ante 429/5xx y errores de red.
 * Respeta Retry-After (segundos); si el servidor pide esperar demasiado, devuelve el error tal cual.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1_000,
    private val maxDelayMs: Long = 16_000,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        while (true) {
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (chain.call().isCanceled() || attempt >= maxRetries) throw e
                pause(backoff(attempt++))
                continue
            }

            if (response.code !in RETRYABLE || attempt >= maxRetries) return response

            val delay = retryAfterMs(response) ?: backoff(attempt)
            if (delay > MAX_RETRY_AFTER_MS) return response

            response.close()
            pause(delay)
            attempt++
        }
    }

    private fun backoff(attempt: Int): Long =
        (baseDelayMs shl attempt).coerceAtMost(maxDelayMs) + Random.nextLong(0, 250)

    private fun retryAfterMs(response: Response): Long? =
        response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1_000)

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Petición cancelada")
        }
    }

    private companion object {
        val RETRYABLE = setOf(429, 500, 502, 503, 504)
        const val MAX_RETRY_AFTER_MS = 30_000L
    }
}
