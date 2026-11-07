package com.osintpocket.app.domain.security

import java.io.InputStream
import java.security.MessageDigest

data class FileHashes(val md5: String, val sha1: String, val sha256: String, val bytes: Long)

object Hashing {

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { b ->
            val v = b.toInt() and 0xFF
            append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
    }

    fun sha1Hex(text: String): String = hex(MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)))

    /** Calcula MD5, SHA-1 y SHA-256 en una sola pasada, en streaming (sirve para archivos grandes). */
    fun hashStream(
        input: InputStream,
        totalBytes: Long? = null,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): FileHashes {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(256 * 1024)
        var read = 0L
        var lastReport = 0L
        while (true) {
            if (isCancelled()) throw InterruptedException("Cancelado")
            val n = input.read(buf)
            if (n < 0) break
            md5.update(buf, 0, n)
            sha1.update(buf, 0, n)
            sha256.update(buf, 0, n)
            read += n
            if (totalBytes != null && totalBytes > 0 && read - lastReport >= 4 * 1024 * 1024) {
                lastReport = read
                onProgress((read.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f))
            }
        }
        onProgress(1f)
        return FileHashes(hex(md5.digest()), hex(sha1.digest()), hex(sha256.digest()), read)
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

/** Pwned Passwords con k-anonimato: solo se envían los 5 primeros caracteres del SHA-1. */
object PwnedRange {

    data class Query(val prefix: String, val suffix: String)

    fun queryFor(password: String): Query {
        val h = Hashing.sha1Hex(password).uppercase()
        return Query(h.take(5), h.drop(5))
    }

    /** Respuesta "SUFIJO:conteo" por línea. Las entradas de relleno (Add-Padding) tienen conteo 0. */
    fun countFor(body: String, suffix: String): Int =
        body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.substringBefore(':').equals(suffix, ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull()
            ?: 0
}

object EmailValidator {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,253}\\.[A-Za-z]{2,63}$")
    fun isValid(s: String) = EMAIL.matches(s.trim())
}
