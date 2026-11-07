package com.osintpocket.app.domain.alias

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Detect {
    /** 2xx = existe; notFoundStatus = no existe. */
    @SerialName("status") STATUS,

    /** Si el cuerpo contiene errorText = no existe. */
    @SerialName("text") TEXT,

    /** Si tras las redirecciones la URL final contiene errorUrl = no existe. */
    @SerialName("url") URL,
}

@Serializable
data class Site(
    val name: String,
    val category: String = "Otros",
    /** URL pública del perfil; {} se sustituye por el alias. */
    val url: String,
    /** URL que se consulta realmente (p. ej. una API); si falta se usa url. */
    val probe: String? = null,
    val detect: Detect = Detect.STATUS,
    val notFoundStatus: List<Int> = listOf(404, 410),
    val errorText: String? = null,
    val errorUrl: String? = null,
    /** Formato de alias admitido por el sitio; si no casa, el sitio se marca como no aplicable. */
    val regex: String? = null,
)

@Serializable
data class SiteList(
    val version: Int = 1,
    val sites: List<Site>,
)

object SiteValidator {
    const val MAX_SITES = 500

    fun validate(list: SiteList): List<String> = buildList {
        if (list.sites.isEmpty()) add("La lista no contiene sitios")
        if (list.sites.size > MAX_SITES) add("Demasiados sitios (${list.sites.size}); máximo $MAX_SITES")

        list.sites.groupBy { it.name.trim().lowercase() }
            .filter { it.key.isNotEmpty() && it.value.size > 1 }
            .keys.forEach { add("Nombre duplicado: $it") }

        list.sites.forEachIndexed { i, s ->
            val tag = s.name.ifBlank { "Sitio #${i + 1}" }
            if (s.name.isBlank()) add("$tag: falta name")
            for ((field, value) in listOf("url" to s.url, "probe" to s.probe)) {
                if (value == null) continue
                if (!value.startsWith("https://") && !value.startsWith("http://")) {
                    add("$tag: $field debe empezar por http:// o https://")
                }
                if (!value.contains("{}")) add("$tag: $field debe contener {}")
            }
            if (s.detect == Detect.TEXT && s.errorText.isNullOrEmpty()) add("$tag: detect=text requiere errorText")
            if (s.detect == Detect.URL && s.errorUrl.isNullOrEmpty()) add("$tag: detect=url requiere errorUrl")
            if (s.notFoundStatus.any { it !in 100..599 }) add("$tag: notFoundStatus fuera de rango")
            s.regex?.let { r -> if (runCatching { Regex(r) }.isFailure) add("$tag: regex no válida") }
        }
    }
}
