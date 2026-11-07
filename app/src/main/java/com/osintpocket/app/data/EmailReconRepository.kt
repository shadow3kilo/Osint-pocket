package com.osintpocket.app.data

import com.osintpocket.app.core.network.await
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.di.WebClient
import com.osintpocket.app.domain.security.Gravatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class GravatarResult(
    val hasAvatar: Boolean,
    val avatarUrl: String,
    val profileUrl: String,
    val fields: List<KeyValue>,
    val accounts: List<KeyValue>,
)

/** Perfil público de Gravatar a partir de un email. Gratis y sin key. Usa el cliente con Tor opcional. */
@Singleton
class EmailReconRepository @Inject constructor(
    @WebClient private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun gravatar(email: String): GravatarResult = withContext(Dispatchers.IO) {
        val hasAvatar = head(Gravatar.avatarUrl(email))
        val (fields, accounts) = runCatching { profile(email) }.getOrDefault(emptyList<KeyValue>() to emptyList())
        GravatarResult(
            hasAvatar = hasAvatar,
            avatarUrl = Gravatar.avatarUrl(email, size = 200),
            profileUrl = Gravatar.profileUrl(email),
            fields = fields,
            accounts = accounts,
        )
    }

    private suspend fun head(url: String): Boolean =
        client.newCall(Request.Builder().url(url).head().build()).await().use { it.isSuccessful }

    /** Perfil JSON público (solo existe si el usuario lo rellenó). Devuelve (campos, cuentas enlazadas). */
    private suspend fun profile(email: String): Pair<List<KeyValue>, List<KeyValue>> {
        val req = Request.Builder().url(Gravatar.profileJsonUrl(email)).get().build()
        client.newCall(req).await().use { r ->
            if (!r.isSuccessful) return emptyList<KeyValue>() to emptyList()
            val body = r.body?.string().orEmpty()
            val entry = runCatching {
                json.parseToJsonElement(body).jsonObject["entry"]?.jsonArray?.firstOrNull()?.jsonObject
            }.getOrNull() ?: return emptyList<KeyValue>() to emptyList()

            fun str(key: String) = entry[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val fields = buildList {
                str("displayName")?.let { add(KeyValue("Nombre", it)) }
                str("preferredUsername")?.let { add(KeyValue("Usuario", it)) }
                entry["currentLocation"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { add(KeyValue("Ubicación", it)) }
                entry["aboutMe"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { add(KeyValue("Sobre", it)) }
            }
            val accounts = entry["accounts"]?.jsonArray.orEmpty().mapNotNull { el ->
                val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                val name = o["shortname"]?.jsonPrimitive?.content ?: o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val url = o["url"]?.jsonPrimitive?.content ?: ""
                KeyValue(name, url)
            }
            return fields to accounts
        }
    }
}
