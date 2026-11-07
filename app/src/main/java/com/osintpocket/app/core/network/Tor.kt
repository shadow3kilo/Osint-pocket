package com.osintpocket.app.core.network

import com.osintpocket.app.core.AppPrefs
import com.osintpocket.app.di.WebClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Con Tor activado devuelve SOLO el proxy SOCKS de Orbot: si Orbot no responde la petición
 * falla, nunca sale por conexión directa (fail-closed). OkHttp pasa el nombre del host sin
 * resolver al proxy SOCKS, así que el DNS también lo resuelve Tor y no hay fugas de DNS.
 */
class TorProxySelector(private val prefs: AppPrefs) : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> =
        if (prefs.torEnabled) {
            listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.torSocksPort)))
        } else {
            listOf(Proxy.NO_PROXY)
        }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}

data class TorStatus(
    /** Orbot escucha en el puerto SOCKS. */
    val orbotRunning: Boolean,
    /** Confirmado por check.torproject.org (null si no se pudo comprobar). */
    val isTor: Boolean?,
    val exitIp: String?,
    val error: String? = null,
)

@Singleton
class TorManager @Inject constructor(
    private val prefs: AppPrefs,
    private val client: OkHttpClient,
    @WebClient private val webClient: OkHttpClient,
) {
    val enabled get() = prefs.torEnabled

    /** Al cambiar de modo se cierran las conexiones abiertas para que ninguna se reutilice por la ruta anterior. */
    fun setEnabled(value: Boolean) {
        prefs.torEnabled = value
        client.connectionPool.evictAll()
        webClient.connectionPool.evictAll()
    }

    suspend fun check(): TorStatus = withContext(Dispatchers.IO) {
        val running = runCatching {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", prefs.torSocksPort), 1_500) }
        }.isSuccess
        if (!running) return@withContext TorStatus(orbotRunning = false, isTor = null, exitIp = null)
        if (!prefs.torEnabled) return@withContext TorStatus(orbotRunning = true, isTor = null, exitIp = null)

        try {
            val req = Request.Builder().url("https://check.torproject.org/api/ip").get().build()
            client.newCall(req).await().use { r ->
                if (!r.isSuccessful) return@use TorStatus(true, null, null, "check.torproject.org respondió HTTP ${r.code}")
                val o = Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                TorStatus(
                    orbotRunning = true,
                    isTor = (o["IsTor"] as? JsonPrimitive)?.content == "true",
                    exitIp = (o["IP"] as? JsonPrimitive)?.content,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TorStatus(orbotRunning = true, isTor = null, exitIp = null, error = e.toUserMessage())
        }
    }
}
