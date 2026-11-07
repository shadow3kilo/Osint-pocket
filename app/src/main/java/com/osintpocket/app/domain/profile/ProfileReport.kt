package com.osintpocket.app.domain.profile

import com.osintpocket.app.data.GravatarResult
import com.osintpocket.app.data.ProviderResult

/** Construye el informe de texto de una ficha de email. Sin Android: testeable en JVM. */
object ProfileReport {

    /** Nº de fuentes (brechas + Gravatar) en las que aparece el email. */
    fun hitCount(gravatar: GravatarResult?, providers: List<ProviderResult>): Int {
        val gravatarHit = if (gravatar != null && (gravatar.hasAvatar || gravatar.fields.isNotEmpty())) 1 else 0
        return gravatarHit + providers.count { it.found }
    }

    fun summary(gravatar: GravatarResult?, providers: List<ProviderResult>): String {
        val hits = hitCount(gravatar, providers)
        val sources = providers.size + if (gravatar != null) 1 else 0
        return if (hits == 0) "Sin rastro en $sources fuentes consultadas"
        else "Aparece en $hits de $sources fuentes"
    }

    fun build(email: String, gravatar: GravatarResult?, providers: List<ProviderResult>): String = buildString {
        appendLine("Ficha OSINT de $email")
        appendLine(summary(gravatar, providers))
        appendLine()

        if (gravatar != null) {
            appendLine("== Gravatar ==")
            if (!gravatar.hasAvatar && gravatar.fields.isEmpty()) {
                appendLine("Sin perfil público")
            } else {
                if (gravatar.hasAvatar) appendLine("Avatar público: sí")
                (gravatar.fields + gravatar.accounts).forEach { appendLine("${it.key}: ${it.value}") }
            }
            appendLine()
        }

        providers.forEach { r ->
            appendLine("== ${r.provider.title} ==")
            appendLine(r.summary)
            r.items.forEach { item ->
                append("- ${item.name}")
                if (item.fields.isNotEmpty()) append(item.fields.joinToString("; ", prefix = " (", postfix = ")") { "${it.key}: ${it.value}" })
                appendLine()
            }
            appendLine()
        }

        append("Fuentes gratuitas: Gravatar, XposedOrNot, LeakCheck, Hudson Rock")
        if (providers.any { it.provider.name == "HIBP" }) append(", Have I Been Pwned")
        append(". Datos de brechas bajo sus respectivas licencias.")
    }.trim()
}
