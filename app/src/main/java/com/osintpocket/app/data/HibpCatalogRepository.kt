package com.osintpocket.app.data

import android.content.Context
import com.osintpocket.app.data.remote.HibpBreach
import com.osintpocket.app.data.remote.HibpCatalogApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class Catalog(val breaches: List<HibpBreach>, val fetchedAt: Long, val fromCache: Boolean)

/**
 * Catálogo completo de brechas de HIBP (un solo GET sin key). Se guarda en disco y se renueva
 * cada 24 h: así la app no repite la descarga en cada búsqueda y funciona sin conexión.
 */
@Singleton
class HibpCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: HibpCatalogApi,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var memory: Catalog? = null
    private val file get() = File(context.filesDir, CACHE_FILE)
    private val serializer = ListSerializer(HibpBreach.serializer())

    suspend fun get(forceRefresh: Boolean = false): Catalog = mutex.withLock {
        val now = System.currentTimeMillis()
        memory?.let { if (!forceRefresh && now - it.fetchedAt < MAX_AGE_MS) return@withLock it }

        val disk = if (memory == null) readDisk() else memory
        if (!forceRefresh && disk != null && now - disk.fetchedAt < MAX_AGE_MS) {
            memory = disk
            return@withLock disk
        }

        try {
            val fresh = api.breaches()
            withContext(Dispatchers.IO) {
                val tmp = File(context.filesDir, "$CACHE_FILE.tmp")
                tmp.writeText(json.encodeToString(serializer, fresh))
                tmp.renameTo(file)
            }
            Catalog(fresh, now, fromCache = false).also { memory = it }
        } catch (e: Exception) {
            // Sin red o servicio caído: mejor una copia antigua que nada.
            disk?.copy(fromCache = true)?.also { memory = it } ?: throw e
        }
    }

    private suspend fun readDisk(): Catalog? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching null
            Catalog(json.decodeFromString(serializer, file.readText()), file.lastModified(), fromCache = true)
        }.getOrNull()
    }

    private companion object {
        const val CACHE_FILE = "hibp_breaches.json"
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
