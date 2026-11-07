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
    is java.net.ConnectException -> "No se pudo conectar (si usas Tor, comprueba que Orbot esté encendido)"
    is java.net.SocketException ->
        if (message.orEmpty().contains("SOCKS", ignoreCase = true)) {
            "Tor no responde o el destino no es accesible por Tor. Comprueba que Orbot esté encendido"
        } else {
            message ?: "Error de conexión"
        }
    is SocketTimeoutException -> "Tiempo de espera agotado"
    is UnknownHostException -> "Sin conexión o host no resoluble"
    is SSLException -> "Error TLS: ${message.orEmpty()}"
    is SerializationException -> "Respuesta no válida (el servicio puede estar saturado)"
    is IllegalArgumentException -> "Respuesta inesperada del servicio"
    else -> message ?: javaClass.simpleName
}
