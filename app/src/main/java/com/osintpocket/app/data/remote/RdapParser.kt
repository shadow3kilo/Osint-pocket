package com.osintpocket.app.data.remote

import com.osintpocket.app.core.ui.KeyValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Resume una respuesta RDAP (dominio o IP) en pares clave/valor legibles. */
object RdapParser {

    fun summarize(o: JsonObject): List<KeyValue> = buildList {
        o.str("ldhName")?.let { add(KeyValue("Dominio", it.lowercase())) }
        o.str("handle")?.let { add(KeyValue("Handle", it)) }
        o.str("name")?.let { add(KeyValue("Nombre", it)) }
        val start = o.str("startAddress")
        val end = o.str("endAddress")
        if (start != null && end != null) add(KeyValue("Rango", "$start – $end"))
        o.str("country")?.let { add(KeyValue("País", it)) }

        o.arr("status")?.mapNotNull { it.prim() }?.takeIf { it.isNotEmpty() }
            ?.let { add(KeyValue("Estado", it.joinToString(", "))) }

        o.arr("events")?.forEach { ev ->
            val e = ev as? JsonObject ?: return@forEach
            val action = e.str("eventAction") ?: return@forEach
            val date = e.str("eventDate") ?: return@forEach
            add(KeyValue(eventLabel(action), date.take(10)))
        }

        o.arr("nameservers")
            ?.mapNotNull { (it as? JsonObject)?.str("ldhName")?.lowercase() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(KeyValue("Nameservers", it.joinToString("\n"))) }

        o.arr("entities")?.forEach { (it as? JsonObject)?.let { e -> addEntity(e) } }
    }

    private fun MutableList<KeyValue>.addEntity(e: JsonObject) {
        val roles = e.arr("roles")?.mapNotNull { it.prim() }.orEmpty()
        val fn = vcardFn(e)
        if (roles.isNotEmpty() && !fn.isNullOrBlank()) {
            add(KeyValue(roles.joinToString("/") { roleLabel(it) }, fn))
        }
        e.arr("entities")?.forEach { (it as? JsonObject)?.let { child -> addEntity(child) } }
    }

    /** vcardArray = ["vcard", [["fn", {}, "text", "Nombre"], ...]] */
    private fun vcardFn(e: JsonObject): String? {
        val props = (e.arr("vcardArray")?.getOrNull(1) as? JsonArray) ?: return null
        return props.firstNotNullOfOrNull { p ->
            val prop = p as? JsonArray ?: return@firstNotNullOfOrNull null
            if (prop.getOrNull(0)?.prim() == "fn") prop.getOrNull(3)?.prim() else null
        }
    }

    private fun eventLabel(action: String) = when (action) {
        "registration" -> "Registrado"
        "expiration" -> "Expira"
        "last changed" -> "Último cambio"
        "last update of RDAP database" -> "Actualización RDAP"
        "transfer" -> "Transferido"
        else -> action.replaceFirstChar { it.uppercase() }
    }

    private fun roleLabel(role: String) = when (role) {
        "registrar" -> "Registrador"
        "registrant" -> "Titular"
        "abuse" -> "Abuso"
        "administrative" -> "Admin"
        "technical" -> "Técnico"
        "noc" -> "NOC"
        else -> role
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonElement.prim(): String? = (this as? JsonPrimitive)?.content
}
