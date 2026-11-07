package com.osintpocket.app.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Ver [HistoryModule]. */
    val module: String,
    val query: String,
    val summary: String,
    /** Informe en texto plano, el mismo que se copia desde la pantalla. */
    val details: String,
    val createdAt: Long,
)

object HistoryModule {
    const val DOMAIN = "Dominio/IP"
    const val WEB = "Web"
    const val ALIAS = "Alias"
    const val EXIF = "EXIF"
    const val BREACH = "Brechas"
    const val HASH = "Hash"
}
