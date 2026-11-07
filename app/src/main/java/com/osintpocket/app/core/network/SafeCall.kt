package com.osintpocket.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> when (code()) {
        404 -> "Sin datos (404)"
        429 -> "Límite de peticiones alcanzado; prueba en un rato"
        in 500..599 -> "El servicio no responde (HTTP ${code()})"
        else -> "Error HTTP ${code()}"
    }
    // ConnectException es subclase de SocketException: una sola rama cubre ambas.
    is java.net.SocketException -> {
        val m = message.orEmpty()
        val torProxyDown = m.contains("SOCKS", ignoreCase = true) ||
            m.contains("127.0.0.1") || m.contains("ECONNREFUSED", ignoreCase = true)
        if (torProxyDown) {
            "Tor está activado pero Orbot no responde. Abre Orbot y pulsa «Iniciar», o desactiva Tor en Ajustes."
        } else {
            "No se pudo conectar: ${m.ifEmpty { "sin conexión" }}"
        }
    }
    is SocketTimeoutException -> "Tiempo de espera agotado"
    is UnknownHostException -> "Sin conexión o host no resoluble"
    is SSLException -> "Error TLS: ${message.orEmpty()}"
    is SerializationException -> "Respuesta no válida (el servicio puede estar saturado)"
    is IllegalArgumentException -> "Respuesta inesperada del servicio"
    else -> message ?: javaClass.simpleName
}
