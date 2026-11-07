package com.osintpocket.app.domain.dorks

import java.net.URLEncoder

enum class DorkTarget(val label: String, val hint: String) {
    USERNAME("Alias", "johndoe"),
    EMAIL("Email", "tu@correo.com"),
    DOMAIN("Dominio", "ejemplo.com"),
    NAME("Nombre", "Juan Pérez"),
    PHONE("Teléfono", "+34612345678"),
}

enum class SearchEngine(val label: String, private val base: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=");

    fun url(query: String): String = base + URLEncoder.encode(query, "UTF-8")
}

data class Dork(val category: String, val label: String, val query: String)

/**
 * Generador de Google dorks para recon pasivo. Solo construye consultas de buscador
 * sobre fuentes públicas; no accede a nada. Lógica pura: testeable en JVM.
 */
object Dorks {

    private fun q(v: String) = "\"${v.replace("\"", "")}\""

    fun generate(target: DorkTarget, rawValue: String): List<Dork> {
        val v = rawValue.trim()
        if (v.isEmpty()) return emptyList()
        val quoted = q(v)
        val domainBare = v.removePrefix("www.")

        return when (target) {
            DorkTarget.USERNAME -> listOf(
                Dork("Perfiles", "Menciones del alias", quoted),
                Dork("Perfiles", "Redes sociales", "$quoted (site:twitter.com OR site:instagram.com OR site:tiktok.com OR site:facebook.com OR site:linkedin.com)"),
                Dork("Perfiles", "Foros y dev", "$quoted (site:reddit.com OR site:github.com OR site:news.ycombinator.com)"),
                Dork("Fugas", "Pegado en pastes", "$quoted (site:pastebin.com OR site:ghostbin.com OR site:paste.ee)"),
            )
            DorkTarget.EMAIL -> listOf(
                Dork("Rastro", "Menciones del email", quoted),
                Dork("Fugas", "Email en pastes", "$quoted (site:pastebin.com OR site:throwbin.io OR site:paste.ee)"),
                Dork("Fugas", "Volcados y credenciales", "$quoted (password OR passwd OR dump OR leaked)"),
                Dork("Documentos", "En documentos", "$quoted (filetype:pdf OR filetype:xlsx OR filetype:csv OR filetype:txt)"),
            )
            DorkTarget.DOMAIN -> listOf(
                Dork("Superficie", "Subdominios indexados", "site:$domainBare -www"),
                Dork("Ficheros", "Documentos del dominio", "site:$domainBare (filetype:pdf OR filetype:docx OR filetype:xlsx OR filetype:csv)"),
                Dork("Exposición", "Paneles de acceso", "site:$domainBare (inurl:login OR inurl:admin OR inurl:dashboard)"),
                Dork("Exposición", "Directorios abiertos", "site:$domainBare intitle:\"index of\""),
                Dork("Exposición", "Config y backups", "site:$domainBare (ext:env OR ext:sql OR ext:bak OR ext:log OR ext:ini)"),
                Dork("Fugas", "Dominio en pastes", "$quoted site:pastebin.com"),
            )
            DorkTarget.NAME -> listOf(
                Dork("Perfiles", "Nombre exacto", quoted),
                Dork("Perfiles", "En redes", "$quoted (site:linkedin.com OR site:facebook.com OR site:instagram.com)"),
                Dork("Documentos", "Currículos y documentos", "$quoted (CV OR resume OR curriculum) (filetype:pdf OR filetype:docx)"),
            )
            DorkTarget.PHONE -> listOf(
                Dork("Rastro", "Número exacto", quoted),
                Dork("Rastro", "Variantes y anuncios", "$quoted (contacto OR whatsapp OR telegram)"),
                Dork("Fugas", "Teléfono en pastes", "$quoted site:pastebin.com"),
            )
        }
    }
}
