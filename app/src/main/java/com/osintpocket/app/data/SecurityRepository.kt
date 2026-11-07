package com.osintpocket.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.osintpocket.app.core.SecretStore
import com.osintpocket.app.data.remote.HibpApi
import com.osintpocket.app.data.remote.HibpBreach
import com.osintpocket.app.data.remote.HudsonRockApi
import com.osintpocket.app.data.remote.LeakCheckApi
import com.osintpocket.app.data.remote.XonApi
import com.osintpocket.app.data.remote.XonPasswordApi
import com.osintpocket.app.data.remote.PwnedPasswordsApi
import com.osintpocket.app.domain.security.FileHashes
import com.osintpocket.app.domain.security.Hashing
import com.osintpocket.app.domain.security.PwnedRange
import com.osintpocket.app.domain.security.XonPassword
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class MissingApiKeyException : Exception("Falta la API key de Have I Been Pwned (Más → Ajustes)")

data class HashedFile(val name: String, val hashes: FileHashes)

@Singleton
class SecurityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hibp: HibpApi,
    private val pwned: PwnedPasswordsApi,
    private val xon: XonApi,
    private val xonPasswords: XonPasswordApi,
    private val leakCheck: LeakCheckApi,
    private val hudsonRock: HudsonRockApi,
    private val secrets: SecretStore,
) {

    fun hasHibpKey(): Boolean = secrets.hibpKey != null

    suspend fun breaches(email: String): List<HibpBreach> {
        val key = secrets.hibpKey ?: throw MissingApiKeyException()
        val r = hibp.breaches(email.trim(), key)
        return when {
            r.isSuccessful -> r.body().orEmpty().sortedByDescending { it.breachDate }
            r.code() == 404 -> emptyList()
            r.code() == 401 -> throw IllegalStateException("La API key de HIBP no es válida o ha caducado")
            r.code() == 403 -> throw IllegalStateException("HIBP ha rechazado la petición (403)")
            else -> throw HttpException(r)
        }
    }

    /** Consulta un proveedor de filtraciones por email y devuelve el resultado en formato común. */
    suspend fun lookup(provider: LeakProvider, email: String): ProviderResult {
        val e = email.trim()
        return when (provider) {
            LeakProvider.XON -> handle(xon.breachAnalytics(e), "XposedOrNot", notFoundCodes = setOf(404)) { LeakParsers.xon(it) }
            LeakProvider.LEAKCHECK -> handle(leakCheck.check(e), "LeakCheck", notFoundCodes = setOf(404)) { LeakParsers.leakCheck(it) }
            LeakProvider.HUDSONROCK -> handle(hudsonRock.byEmail(e), "Hudson Rock", notFoundCodes = setOf(404)) { LeakParsers.hudsonRock(it) }
            LeakProvider.HIBP -> LeakParsers.hibp(breaches(e))
        }
    }

    private fun handle(
        r: Response<JsonObject>,
        service: String,
        notFoundCodes: Set<Int>,
        parse: (JsonObject?) -> ProviderResult,
    ): ProviderResult = when {
        r.isSuccessful -> parse(r.body())
        r.code() in notFoundCodes -> parse(null)
        r.code() == 429 -> throw IllegalStateException("Límite gratuito de $service alcanzado; prueba más tarde")
        r.code() == 403 -> throw IllegalStateException("$service ha bloqueado la consulta (403)")
        else -> throw HttpException(r)
    }

    /** Veces que aparece la contraseña en filtraciones. La contraseña nunca sale del móvil. */
    suspend fun passwordCount(password: String): Int {
        val q = PwnedRange.queryFor(password)
        val body = withContext(Dispatchers.IO) { pwned.range(q.prefix).use { it.string() } }
        return PwnedRange.countFor(body, q.suffix)
    }

    /**
     * XposedOrNot: envía 10 caracteres hex del Keccak-512 (40 bits). Es menos anónimo que
     * Pwned Passwords (20 bits), por eso en la app es opcional.
     */
    suspend fun passwordCountXon(password: String): Long {
        val r = xonPasswords.anon(XonPassword.prefixFor(password))
        return when {
            r.isSuccessful -> LeakParsers.xonPasswordCount(r.body())
            r.code() == 404 -> 0L
            r.code() == 429 -> throw IllegalStateException("Límite de XposedOrNot alcanzado; prueba más tarde")
            else -> throw HttpException(r)
        }
    }

    suspend fun hashFile(uri: Uri, onProgress: (Float) -> Unit): HashedFile = withContext(Dispatchers.IO) {
        var name = "archivo"
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                        c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                    }
                }
        }
        val ctx = coroutineContext
        val hashes = context.contentResolver.openInputStream(uri)?.use { input ->
            Hashing.hashStream(input, size, onProgress) { !ctx.isActive }
        } ?: throw IOException("No se pudo abrir el archivo")
        ctx.ensureActive()
        HashedFile(name, hashes)
    }
}
