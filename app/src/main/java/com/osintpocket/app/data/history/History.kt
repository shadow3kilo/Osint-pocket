package com.osintpocket.app.data.history

import android.content.Context
import android.net.Uri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import com.osintpocket.app.BuildConfig
import com.osintpocket.app.core.AppPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    suspend fun all(): List<HistoryEntry>

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
}

enum class ExportFormat(val mime: String, val ext: String) {
    JSON("application/json", "json"),
    MARKDOWN("text/markdown", "md"),
}

@Singleton
class HistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HistoryDao,
    private val prefs: AppPrefs,
) {
    fun observe(): Flow<List<HistoryEntry>> = dao.observeAll()

    /** No guarda nada si el usuario ha desactivado el historial en Ajustes. */
    suspend fun record(module: String, query: String, summary: String, details: String) {
        if (!prefs.historyEnabled) return
        runCatching {
            dao.insert(
                HistoryEntry(
                    module = module,
                    query = query.take(500),
                    summary = summary.take(500),
                    details = details.take(MAX_DETAILS),
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()

    suspend fun export(uri: Uri, format: ExportFormat): Int = withContext(Dispatchers.IO) {
        val entries = dao.all()
        val text = when (format) {
            ExportFormat.JSON -> HistoryExporter.toJson(entries, BuildConfig.VERSION_NAME)
            ExportFormat.MARKDOWN -> HistoryExporter.toMarkdown(entries)
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: throw IOException("No se pudo escribir el archivo")
        entries.size
    }

    private companion object {
        const val MAX_DETAILS = 200_000
    }
}
