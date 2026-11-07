package com.osintpocket.app.domain.security

import com.osintpocket.app.data.remote.HibpBreach

enum class CatalogSort(val label: String) {
    ADDED("Recién añadidas"),
    BREACH_DATE("Fecha de la brecha"),
    SIZE("Más cuentas"),
    NAME("A-Z"),
}

/** Búsqueda y filtros sobre el catálogo público de HIBP. Sin Android: testeable en JVM. */
object HibpCatalog {

    fun filter(
        all: List<HibpBreach>,
        query: String = "",
        dataClass: String? = null,
        sort: CatalogSort = CatalogSort.ADDED,
    ): List<HibpBreach> {
        val q = query.trim()
        return all.asSequence()
            .filter { b ->
                q.isEmpty() || b.title.contains(q, true) || b.name.contains(q, true) || b.domain.contains(q, true)
            }
            .filter { b -> dataClass == null || b.dataClasses.any { it.equals(dataClass, true) } }
            .let { seq ->
                when (sort) {
                    CatalogSort.ADDED -> seq.sortedByDescending { it.addedDate }
                    CatalogSort.BREACH_DATE -> seq.sortedByDescending { it.breachDate }
                    CatalogSort.SIZE -> seq.sortedByDescending { it.pwnCount }
                    CatalogSort.NAME -> seq.sortedBy { it.title.lowercase() }
                }
            }
            .toList()
    }

    /** "https://www.Ejemplo.com/login" → "ejemplo.com". null si no parece un dominio. */
    fun normalizeDomain(input: String): String? {
        val d = input.trim().lowercase()
            .substringAfter("://")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .removePrefix("www.")
            .trimEnd('.')
        return d.takeIf { it.contains('.') && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
    }

    /** Brechas de un servicio: mismo dominio o subdominio (mail.ejemplo.com coincide con ejemplo.com). */
    fun forDomain(all: List<HibpBreach>, domain: String): List<HibpBreach> {
        val d = normalizeDomain(domain) ?: return emptyList()
        return all.filter { b ->
            val bd = b.domain.lowercase().removePrefix("www.")
            bd.isNotEmpty() && (bd == d || d.endsWith(".$bd") || bd.endsWith(".$d"))
        }.sortedByDescending { it.breachDate }
    }

    /** Varios servicios separados por comas, espacios o saltos de línea. */
    fun forServices(all: List<HibpBreach>, input: String): Map<String, List<HibpBreach>> =
        input.split(Regex("[,;\\s]+"))
            .mapNotNull { normalizeDomain(it) }
            .distinct()
            .associateWith { forDomain(all, it) }

    /** Tipos de dato con su número de brechas, de más a menos frecuente. */
    fun dataClasses(all: List<HibpBreach>): List<Pair<String, Int>> =
        all.flatMap { it.dataClasses }.groupingBy { it }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    fun latest(all: List<HibpBreach>): HibpBreach? = all.maxByOrNull { it.addedDate }

    fun totalAccounts(all: List<HibpBreach>): Long = all.sumOf { it.pwnCount }
}
