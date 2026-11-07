package com.osintpocket.app.data

import android.content.Context
import android.net.Uri
import com.osintpocket.app.domain.alias.Site
import com.osintpocket.app.domain.alias.SiteList
import com.osintpocket.app.domain.alias.SiteValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

class InvalidSiteListException(val errors: List<String>) : Exception(
    "Lista no válida:\n" + errors.take(8).joinToString("\n") +
        if (errors.size > 8) "\n… y ${errors.size - 8} errores más" else ""
)

/** Lista de sitios del módulo Alias: la de fábrica (assets) o una personalizada importada por el usuario. */
@Singleton
class SiteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Source { DEFAULT, CUSTOM }

    data class Loaded(val sites: List<Site>, val source: Source, val warning: String? = null)

    // Sin coerceInputValues: un "detect" mal escrito debe dar error, no convertirse en "status" en silencio.
    private val json = Json { ignoreUnknownKeys = true }

    private val customFile get() = File(context.filesDir, CUSTOM_FILE)

    suspend fun load(): Loaded = withContext(Dispatchers.IO) {
        if (customFile.exists()) {
            val custom = runCatching { decodeValidated(customFile.readText()) }.getOrNull()
            if (custom != null) return@withContext Loaded(custom, Source.CUSTOM)
            return@withContext Loaded(decodeValidated(defaultText()), Source.DEFAULT,
                warning = "La lista personalizada guardada no es válida; se usa la de fábrica")
        }
        Loaded(decodeValidated(defaultText()), Source.DEFAULT)
    }

    /** Valida y guarda una lista importada. Devuelve el número de sitios. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readCapped(MAX_BYTES) }
            ?: throw IOException("No se pudo abrir el archivo")
        val sites = decodeValidated(text)
        val tmp = File(context.filesDir, "$CUSTOM_FILE.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(customFile)) {
            tmp.delete()
            throw IOException("No se pudo guardar la lista")
        }
        sites.size
    }

    /** Exporta la lista en uso (personalizada o de fábrica) para editarla fuera de la app. */
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val text = if (customFile.exists()) customFile.readText() else defaultText()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: throw IOException("No se pudo escribir el archivo")
    }

    suspend fun restoreDefault() = withContext(Dispatchers.IO) {
        customFile.delete()
        Unit
    }

    private fun defaultText(): String =
        context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }

    private fun decodeValidated(text: String): List<Site> {
        val list = try {
            json.decodeFromString(SiteList.serializer(), text)
        } catch (e: IllegalArgumentException) { // SerializationException hereda de aquí
            throw InvalidSiteListException(listOf("JSON mal formado: ${e.message?.lineSequence()?.firstOrNull()}"))
        }
        val errors = SiteValidator.validate(list)
        if (errors.isNotEmpty()) throw InvalidSiteListException(errors)
        return list.sites
    }

    private fun InputStream.readCapped(max: Int): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8_192)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw IOException("Archivo demasiado grande (máx. 1 MB)")
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    private companion object {
        const val ASSET_FILE = "sites.json"
        const val CUSTOM_FILE = "sites.json"
        const val MAX_BYTES = 1_048_576
    }
}
