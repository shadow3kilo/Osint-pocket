package com.osintpocket.app.domain.alias

enum class AliasStatus { FOUND, UNKNOWN, NOT_FOUND, INVALID }

data class SiteResult(
    val site: Site,
    val status: AliasStatus,
    val profileUrl: String,
    val detail: String,
    val timeMs: Long? = null,
)

/** Decide si un alias existe en un sitio a partir de la respuesta HTTP. Sin red: testeable en JVM. */
object AliasClassifier {

    private val USERNAME = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun isValidUsername(u: String): Boolean = USERNAME.matches(u)

    fun acceptsUsername(site: Site, username: String): Boolean =
        site.regex?.let { runCatching { Regex(it).matches(username) }.getOrDefault(true) } ?: true

    fun classify(site: Site, code: Int, finalUrl: String, body: String?): Pair<AliasStatus, String> {
        if (code in site.notFoundStatus) return AliasStatus.NOT_FOUND to "HTTP $code"

        when (site.detect) {
            Detect.URL -> {
                val marker = site.errorUrl
                if (marker != null && finalUrl.contains(marker, ignoreCase = true)) {
                    return AliasStatus.NOT_FOUND to "Redirige a la página de «no existe»"
                }
            }
            Detect.TEXT -> {
                val marker = site.errorText
                if (marker != null && body != null && body.contains(marker)) {
                    return AliasStatus.NOT_FOUND to "La respuesta indica que no existe"
                }
            }
            Detect.STATUS -> Unit
        }

        return when (code) {
            in 200..299 -> AliasStatus.FOUND to "HTTP $code"
            401, 403 -> AliasStatus.UNKNOWN to "Acceso bloqueado (HTTP $code); puede filtrar clientes automáticos"
            429 -> AliasStatus.UNKNOWN to "Límite de peticiones (HTTP 429)"
            in 500..599 -> AliasStatus.UNKNOWN to "Error del servidor (HTTP $code)"
            else -> AliasStatus.UNKNOWN to "Respuesta inesperada (HTTP $code)"
        }
    }
}
