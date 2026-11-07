package com.osintpocket.app.domain.web

import okhttp3.Headers

enum class Grade { GOOD, WARN, BAD, INFO }

data class HeaderCheck(
    val title: String,
    val header: String,
    val value: String?,
    val grade: Grade,
    val note: String,
    /** Peso en la puntuación; 0 = solo informativo. */
    val weight: Int,
)

data class SecurityReport(val checks: List<HeaderCheck>, val score: Int, val letter: Char) {
    val light: Grade
        get() = when (letter) {
            'A', 'B' -> Grade.GOOD
            'C', 'D' -> Grade.WARN
            else -> Grade.BAD
        }
}

/**
 * Evalúa cabeceras de seguridad HTTP. GOOD puntúa el peso completo, WARN la mitad, BAD cero.
 * Las comprobaciones que no aplican (p. ej. sin cookies) salen del denominador.
 */
object SecurityGrader {

    private const val HSTS_MIN_SECONDS = 15_552_000L // 180 días

    fun grade(isHttps: Boolean, headers: Headers): SecurityReport {
        val csp = headers["Content-Security-Policy"]
        val checks = listOfNotNull(
            hsts(isHttps, headers["Strict-Transport-Security"]),
            csp(csp, headers["Content-Security-Policy-Report-Only"]),
            framing(headers["X-Frame-Options"], csp),
            contentType(headers["X-Content-Type-Options"]),
            referrer(headers["Referrer-Policy"]),
            permissions(headers["Permissions-Policy"], headers["Feature-Policy"]),
            disclosure(headers),
            cookies(isHttps, headers.values("Set-Cookie")),
            coop(headers["Cross-Origin-Opener-Policy"]),
            cors(headers["Access-Control-Allow-Origin"]),
        )

        val scored = checks.filter { it.weight > 0 }
        val total = scored.sumOf { it.weight }
        val earnedHalves = scored.sumOf {
            when (it.grade) {
                Grade.GOOD -> it.weight * 2
                Grade.WARN -> it.weight
                else -> 0
            }
        }
        var score = if (total == 0) 0 else earnedHalves * 50 / total
        if (!isHttps) score = minOf(score, 40)

        val letter = when {
            score >= 90 -> 'A'
            score >= 75 -> 'B'
            score >= 60 -> 'C'
            score >= 45 -> 'D'
            score >= 30 -> 'E'
            else -> 'F'
        }
        return SecurityReport(checks, score, letter)
    }

    private fun hsts(isHttps: Boolean, v: String?): HeaderCheck {
        fun check(g: Grade, note: String) = HeaderCheck("HSTS", "Strict-Transport-Security", v, g, note, 20)
        if (!isHttps) return check(Grade.BAD, "La página final no usa HTTPS; HSTS no aplica sobre HTTP")
        if (v == null) return check(Grade.BAD, "Ausente: permite downgrade a HTTP (SSL stripping) en la primera visita")

        val maxAge = Regex("max-age\\s*=\\s*\"?(\\d+)", RegexOption.IGNORE_CASE)
            .find(v)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val days = maxAge / 86_400
        val extras = listOfNotNull(
            "includeSubDomains".takeIf { v.contains(it, ignoreCase = true) },
            "preload".takeIf { v.contains(it, ignoreCase = true) },
        )
        val suffix = if (extras.isEmpty()) "" else " · ${extras.joinToString(" + ")}"
        return when {
            maxAge >= HSTS_MIN_SECONDS -> check(Grade.GOOD, "max-age de $days días$suffix")
            maxAge > 0 -> check(Grade.WARN, "max-age corto ($days días); se recomienda ≥ 180")
            else -> check(Grade.BAD, "max-age=0 desactiva HSTS")
        }
    }

    internal fun parseCsp(v: String): Map<String, List<String>> =
        v.split(';')
            .mapNotNull { directive ->
                val parts = directive.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (parts.isEmpty()) null else parts[0].lowercase() to parts.drop(1).map { it.lowercase() }
            }
            .distinctBy { it.first } // por especificación, gana la primera aparición
            .toMap()

    private fun csp(v: String?, reportOnly: String?): HeaderCheck {
        fun check(g: Grade, note: String, value: String? = v) =
            HeaderCheck("CSP", "Content-Security-Policy", value, g, note, 25)

        if (v == null && reportOnly != null) {
            return check(Grade.WARN, "Solo en modo report-only: informa pero no bloquea nada", reportOnly)
        }
        if (v == null) return check(Grade.BAD, "Ausente: sin defensa en profundidad contra XSS e inyección de recursos")

        val d = parseCsp(v)
        val src = d["script-src"] ?: d["default-src"]
        val issues = mutableListOf<String>()
        if (src == null) {
            issues += "no define script-src ni default-src"
        } else {
            val nonceOrHash = src.any {
                it.startsWith("'nonce-") || it.startsWith("'sha256-") ||
                    it.startsWith("'sha384-") || it.startsWith("'sha512-")
            }
            val strictDynamic = "'strict-dynamic'" in src
            if ("'unsafe-inline'" in src && !nonceOrHash) issues += "'unsafe-inline' en scripts"
            if ("'unsafe-eval'" in src) issues += "'unsafe-eval'"
            if (!strictDynamic && src.any { it == "*" || it == "http:" || it == "https:" || it == "data:" }) {
                issues += "orígenes comodín para scripts"
            }
        }
        return if (issues.isEmpty()) {
            check(Grade.GOOD, "Política restrictiva para scripts")
        } else {
            check(Grade.WARN, "Presente pero débil: ${issues.joinToString(", ")}")
        }
    }

    private fun framing(xfo: String?, csp: String?): HeaderCheck {
        val ancestors = csp?.let { parseCsp(it)["frame-ancestors"] }
        fun check(g: Grade, note: String, value: String? = xfo) =
            HeaderCheck("Anti-clickjacking", "X-Frame-Options", value, g, note, 15)

        if (ancestors != null) {
            val value = "frame-ancestors ${ancestors.joinToString(" ")}"
            return if ("*" in ancestors) check(Grade.WARN, "frame-ancestors permite cualquier origen", value)
            else check(Grade.GOOD, "Protegido con CSP frame-ancestors", value)
        }
        val x = xfo?.trim()
        return when {
            x == null -> check(Grade.BAD, "Ausente: la página puede embeberse en un iframe ajeno (clickjacking)")
            x.equals("DENY", true) || x.equals("SAMEORIGIN", true) -> check(Grade.GOOD, "Bloquea iframes de terceros")
            x.startsWith("ALLOW-FROM", true) -> check(Grade.WARN, "ALLOW-FROM está obsoleto y los navegadores lo ignoran")
            else -> check(Grade.WARN, "Valor no reconocido")
        }
    }

    private fun contentType(v: String?): HeaderCheck =
        if (v?.trim().equals("nosniff", ignoreCase = true)) {
            HeaderCheck("Anti-MIME sniffing", "X-Content-Type-Options", v, Grade.GOOD, "nosniff activo", 10)
        } else {
            HeaderCheck("Anti-MIME sniffing", "X-Content-Type-Options", v, Grade.BAD,
                "Falta nosniff: el navegador puede reinterpretar el tipo de contenido", 10)
        }

    private fun referrer(v: String?): HeaderCheck {
        fun check(g: Grade, note: String) = HeaderCheck("Referrer-Policy", "Referrer-Policy", v, g, note, 10)
        if (v == null) return check(Grade.WARN, "Ausente: se aplica el valor por defecto del navegador")
        return when (val effective = v.split(',').map { it.trim().lowercase() }.lastOrNull { it.isNotEmpty() }) {
            "no-referrer", "same-origin", "strict-origin", "strict-origin-when-cross-origin" ->
                check(Grade.GOOD, "Restrictiva ($effective)")
            "unsafe-url" -> check(Grade.BAD, "Envía la URL completa a terceros, incluso de HTTPS a HTTP")
            else -> check(Grade.WARN, "Poco restrictiva ($effective)")
        }
    }

    private fun permissions(v: String?, legacy: String?): HeaderCheck = when {
        v != null -> HeaderCheck("Permissions-Policy", "Permissions-Policy", v, Grade.GOOD,
            "Limita APIs del navegador (cámara, micrófono, geolocalización…)", 10)
        legacy != null -> HeaderCheck("Permissions-Policy", "Feature-Policy", legacy, Grade.WARN,
            "Usa Feature-Policy, sustituida por Permissions-Policy", 10)
        else -> HeaderCheck("Permissions-Policy", "Permissions-Policy", null, Grade.WARN,
            "Ausente: no restringe APIs sensibles del navegador", 10)
    }

    private fun disclosure(h: Headers): HeaderCheck {
        val leaks = buildList {
            h["Server"]?.takeIf { Regex("\\d").containsMatchIn(it) }?.let { add("Server: $it") }
            listOf("X-Powered-By", "X-AspNet-Version", "X-AspNetMvc-Version", "X-Generator")
                .forEach { name -> h[name]?.let { add("$name: $it") } }
        }
        return if (leaks.isEmpty()) {
            HeaderCheck("Divulgación", "Server / X-Powered-By", h["Server"], Grade.GOOD,
                "No expone versiones de software en cabeceras", 5)
        } else {
            HeaderCheck("Divulgación", "Server / X-Powered-By", leaks.joinToString("\n"), Grade.WARN,
                "Revela tecnología o versiones: facilita buscar CVE conocidas", 5)
        }
    }

    private fun cookies(isHttps: Boolean, values: List<String>): HeaderCheck? {
        if (values.isEmpty()) return null
        val problems = values.mapNotNull { cookie ->
            val parts = cookie.split(';').map { it.trim() }
            val name = parts.first().substringBefore('=')
            val attrs = parts.drop(1).map { it.substringBefore('=').trim().lowercase() }
            val missing = buildList {
                if (isHttps && "secure" !in attrs) add("Secure")
                if ("httponly" !in attrs) add("HttpOnly")
                if ("samesite" !in attrs) add("SameSite")
            }
            if (missing.isEmpty()) null else "$name: falta ${missing.joinToString(", ")}"
        }
        return if (problems.isEmpty()) {
            HeaderCheck("Cookies", "Set-Cookie", "${values.size} cookie(s)", Grade.GOOD,
                "Todas con Secure, HttpOnly y SameSite", 5)
        } else {
            HeaderCheck("Cookies", "Set-Cookie", problems.joinToString("\n"), Grade.WARN,
                "Cookies sin algunos flags (un token CSRF sin HttpOnly puede ser intencionado)", 5)
        }
    }

    private fun coop(v: String?): HeaderCheck =
        if (v != null) {
            HeaderCheck("COOP", "Cross-Origin-Opener-Policy", v, Grade.GOOD, "Aísla la ventana de otros orígenes", 0)
        } else {
            HeaderCheck("COOP", "Cross-Origin-Opener-Policy", null, Grade.INFO,
                "Ausente (opcional; recomendable en apps con sesión)", 0)
        }

    private fun cors(v: String?): HeaderCheck? = when (v?.trim()) {
        null -> null
        "*" -> HeaderCheck("CORS", "Access-Control-Allow-Origin", v, Grade.WARN,
            "Abierto a cualquier origen: correcto en recursos públicos, revisar si sirve una API", 0)
        else -> HeaderCheck("CORS", "Access-Control-Allow-Origin", v, Grade.INFO, "Restringido a un origen concreto", 0)
    }
}
