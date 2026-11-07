package com.osintpocket.app.domain

import java.net.IDN

/** Objetivo normalizado a partir de lo que escribe el usuario (acepta URLs, host:puerto, IDN). */
sealed interface Target {
    val value: String

    data class Domain(override val value: String) : Target
    data class Ip(override val value: String, val isV6: Boolean) : Target

    companion object {
        private const val OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
        private val IPV4 = Regex("^($OCTET\\.){3}$OCTET$")
        private val IPV6_CHARS = Regex("^[0-9a-f:.]+$")
        private val DOMAIN = Regex(
            "^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(xn--[a-z0-9-]{1,59}|[a-z]{2,63})$"
        )

        fun parse(raw: String): Target? {
            var s = raw.trim().lowercase()
            if (s.isEmpty()) return null
            s = s.substringAfter("://")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('@')

            s = when {
                s.startsWith("[") -> s.substringAfter('[').substringBefore(']') // [ipv6]:puerto
                s.count { it == ':' } == 1 -> s.substringBefore(':')            // host:puerto
                else -> s
            }.trimEnd('.')

            if (IPV4.matches(s)) return Ip(s, isV6 = false)
            if (isIpv6(s)) return Ip(s, isV6 = true)

            val ascii = runCatching { IDN.toASCII(s, IDN.ALLOW_UNASSIGNED) }.getOrNull()?.lowercase()
                ?: return null
            return if (DOMAIN.matches(ascii)) Domain(ascii) else null
        }

        private fun isIpv6(s: String): Boolean {
            if (!IPV6_CHARS.matches(s)) return false
            val colons = s.count { it == ':' }
            if (colons < 2 || colons > 7) return false
            if (s.indexOf("::") != s.lastIndexOf("::")) return false
            return s.split(':').all { it.length <= 4 || IPV4.matches(it) }
        }
    }
}
