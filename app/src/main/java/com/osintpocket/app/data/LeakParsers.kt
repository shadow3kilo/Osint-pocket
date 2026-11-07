package com.osintpocket.app.data

import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.data.remote.HibpBreach
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.NumberFormat
import java.util.Locale

enum class LeakProvider(val title: String, val subtitle: String) {
    XON("XposedOrNot", "Gratis, sin key · 25 consultas/hora, 100/día"),
    LEAKCHECK("LeakCheck", "Gratis, sin key · nombres de fuentes y tipos de datos"),
    HUDSONROCK("Hudson Rock", "Gratis, sin key · equipos infectados por infostealers"),
    HIBP("Have I Been Pwned", "Con tu API key"),
}

/** Una filtración o un registro de infostealer, con sus datos ya listos para mostrar. */
data class LeakItem(val name: String, val fields: List<KeyValue>)

data class ProviderResult(
    val provider: LeakProvider,
    val found: Boolean,
    val summary: String,
    val items: List<LeakItem>,
)

/**
 * Convierte las respuestas JSON de cada servicio en un formato común.
 * Tolerante: si un campo falta o cambia de tipo, se omite en vez de fallar.
 */
object LeakParsers {

    // ───────────────────────── XposedOrNot ─────────────────────────

    /** null o sin "ExposedBreaches" = el email no aparece. */
    fun xon(o: JsonObject?): ProviderResult {
        val details = o?.obj("ExposedBreaches")?.arr("breaches_details").orEmpty()
        val items = details.mapNotNull { el ->
            val b = el as? JsonObject ?: return@mapNotNull null
            val name = b.text("breach") ?: return@mapNotNull null
            LeakItem(name, buildList {
                b.text("xposed_date")?.let { add(KeyValue("Fecha", it)) }
                b.text("domain")?.takeIf { it.isNotBlank() }?.let { add(KeyValue("Dominio", it)) }
                b.long("xposed_records")?.let { add(KeyValue("Cuentas", num(it))) }
                b.text("xposed_data")?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() }?.let { add(KeyValue("Datos expuestos", it.joinToString(", "))) }
                passwordRisk(b.text("password_risk"))?.let { add(KeyValue("Contraseñas", it)) }
            })
        }
        val pastes = o?.obj("PastesSummary")?.long("cnt") ?: 0L
        val found = items.isNotEmpty() || pastes > 0
        val summary = when {
            !found -> "No aparece en las filtraciones que indexa XposedOrNot"
            pastes > 0 -> "${items.size} filtraciones · $pastes pastes públicos"
            else -> "${items.size} filtraciones"
        }
        return ProviderResult(LeakProvider.XON, found, summary, items)
    }

    /** Respuesta de /pass/anon: veces que aparece la contraseña (0 si no está). */
    fun xonPasswordCount(o: JsonObject?): Long =
        o?.obj("SearchPassAnon")?.long("count") ?: 0L

    private fun passwordRisk(v: String?): String? = when (v?.lowercase()) {
        "plaintext" -> "En texto plano"
        "easytocrack" -> "Hash fácil de descifrar"
        "hardtocrack" -> "Hash robusto"
        else -> null
    }

    // ───────────────────────── LeakCheck ─────────────────────────

    /** {"success":true,"found":N,"fields":[…],"sources":[{"name":…,"date":…}]} · no encontrado: success=false. */
    fun leakCheck(o: JsonObject?): ProviderResult {
        val success = o?.bool("success")
        if (o == null || success == false) {
            val error = o?.text("error").orEmpty()
            if (o == null || error.contains("not found", ignoreCase = true)) {
                return ProviderResult(LeakProvider.LEAKCHECK, false, "No aparece en las fuentes de LeakCheck", emptyList())
            }
            throw IllegalStateException("LeakCheck: $error")
        }
        val found = o.long("found") ?: 0L
        val fields = o.arr("fields").orEmpty().mapNotNull { it.prim() }
        val sources = o.arr("sources").orEmpty().mapNotNull { el ->
            val s = el as? JsonObject ?: return@mapNotNull null
            val name = s.text("name") ?: return@mapNotNull null
            LeakItem(name, listOfNotNull(s.text("date")?.takeIf { it.isNotBlank() }?.let { KeyValue("Fecha", it) }))
        }
        if (found == 0L && sources.isEmpty()) {
            return ProviderResult(LeakProvider.LEAKCHECK, false, "No aparece en las fuentes de LeakCheck", emptyList())
        }
        val summary = buildString {
            append("${num(found)} registros en ${sources.size} fuentes")
            if (fields.isNotEmpty()) append(" · datos: ${fields.joinToString(", ")}")
        }
        return ProviderResult(LeakProvider.LEAKCHECK, true, summary, sources)
    }

    // ───────────────────────── Hudson Rock ─────────────────────────

    /** {"message":…,"stealers":[{date_compromised, stealer_family, operating_system, top_passwords (enmascaradas)…}]} */
    fun hudsonRock(o: JsonObject?): ProviderResult {
        val stealers = o?.arr("stealers").orEmpty().mapNotNull { it as? JsonObject }
        if (stealers.isEmpty()) {
            return ProviderResult(LeakProvider.HUDSONROCK, false, "No aparece en registros de infostealers", emptyList())
        }
        val items = stealers.map { s ->
            LeakItem(s.text("stealer_family") ?: "Infostealer", buildList {
                s.text("date_compromised")?.let { add(KeyValue("Infección", it.take(10))) }
                s.text("operating_system")?.let { add(KeyValue("Sistema", it)) }
                s.text("computer_name")?.takeIf { it.isNotBlank() && !it.equals("Not Found", true) }?.let { add(KeyValue("Equipo", it)) }
                val user = s.long("total_user_services")
                val corp = s.long("total_corporate_services")
                if (user != null || corp != null) {
                    add(KeyValue("Credenciales", listOfNotNull(user?.let { "$it personales" }, corp?.let { "$it corporativas" }).joinToString(", ")))
                }
                s.arr("top_passwords").orEmpty().mapNotNull { it.prim() }.takeIf { it.isNotEmpty() }
                    ?.let { add(KeyValue("Contraseñas (enmascaradas)", it.joinToString(", "))) }
            })
        }
        return ProviderResult(
            LeakProvider.HUDSONROCK, true,
            "Un equipo con este email guardado fue infectado. Cambia las contraseñas que tenía guardadas el navegador de ese equipo.",
            items,
        )
    }

    // ───────────────────────── HIBP ─────────────────────────

    fun hibp(breaches: List<HibpBreach>): ProviderResult = ProviderResult(
        LeakProvider.HIBP,
        breaches.isNotEmpty(),
        if (breaches.isEmpty()) "No aparece en las brechas de HIBP" else "${breaches.size} brechas",
        breaches.map { b ->
            LeakItem(b.title.ifBlank { b.name }, buildList {
                add(KeyValue("Fecha", b.breachDate))
                if (b.domain.isNotBlank()) add(KeyValue("Dominio", b.domain))
                add(KeyValue("Cuentas", num(b.pwnCount)))
                if (b.dataClasses.isNotEmpty()) add(KeyValue("Datos expuestos", b.dataClasses.joinToString(", ")))
            })
        },
    )

    // ───────────────────────── utilidades ─────────────────────────

    private fun num(n: Long) = NumberFormat.getIntegerInstance(Locale("es", "ES")).format(n)

    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject

    private fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray

    private fun JsonObject.text(k: String): String? = this[k]?.prim()?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.long(k: String): Long? = text(k)?.toDoubleOrNull()?.toLong()

    private fun JsonObject.bool(k: String): Boolean? = when (text(k)?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun JsonElement.prim(): String? =
        if (this is JsonPrimitive && this !is JsonNull) content else null
}
