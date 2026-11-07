package com.osintpocket.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.osintpocket.app.core.SecretStore
import com.osintpocket.app.data.remote.HibpApi
import com.osintpocket.app.data.remote.HibpBreach
import com.osintpocket.app.data.remote.PwnedPasswordsApi
import com.osintpocket.app.domain.security.FileHashes
import com.osintpocket.app.domain.security.Hashing
import com.osintpocket.app.domain.security.PwnedRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import retrofit2.HttpException
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

    /** Veces que aparece la contraseña en filtraciones. La contraseña nunca sale del móvil. */
    suspend fun passwordCount(password: String): Int {
        val q = PwnedRange.queryFor(password)
        val body = withContext(Dispatchers.IO) { pwned.range(q.prefix).use { it.string() } }
        return PwnedRange.countFor(body, q.suffix)
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
