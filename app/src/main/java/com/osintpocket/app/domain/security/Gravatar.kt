package com.osintpocket.app.domain.security

import java.security.MessageDigest

/**
 * Gravatar es una API gratuita y sin key: el MD5 del email en minúsculas identifica el perfil
 * público. Si existe, revela avatar y, a veces, nombre, ubicación y cuentas enlazadas.
 */
object Gravatar {

    fun hash(email: String): String {
        val normalized = email.trim().lowercase()
        val md5 = MessageDigest.getInstance("MD5").digest(normalized.toByteArray(Charsets.UTF_8))
        return Hashing.hex(md5)
    }

    /** d=404: devuelve 404 si el email no tiene avatar (en vez de una imagen genérica). */
    fun avatarUrl(email: String, size: Int = 200): String =
        "https://www.gravatar.com/avatar/${hash(email)}?s=$size&d=404"

    fun profileUrl(email: String): String = "https://www.gravatar.com/${hash(email)}"

    fun profileJsonUrl(email: String): String = "https://www.gravatar.com/${hash(email)}.json"
}
