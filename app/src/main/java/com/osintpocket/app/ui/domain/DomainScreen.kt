package com.osintpocket.app.ui.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.data.DnsRecords
import com.osintpocket.app.data.remote.InternetDbHost
import com.osintpocket.app.data.remote.IpGeo
import com.osintpocket.app.ui.common.KeyValueList
import com.osintpocket.app.ui.common.MonoText
import com.osintpocket.app.ui.common.QueryField
import com.osintpocket.app.ui.common.SectionCard
import com.osintpocket.app.ui.common.asPlainText

private const val SUBDOMAIN_PREVIEW = 150

@Composable
fun DomainScreen(vm: DomainViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    DomainContent(state, vm::onQueryChange, vm::search, vm::cancel)
}

@Composable
private fun DomainContent(
    state: DomainUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            QueryField(
                value = state.query,
                onValueChange = onQueryChange,
                label = "Dominio o IP",
                placeholder = "ejemplo.com · 1.1.1.1",
                error = state.inputError,
                busy = state.isBusy,
                onSubmit = onSearch,
                onCancel = onCancel,
            )
        }

        item {
            SectionCard("Registro (RDAP)", state.rdap, copyText = { it.asPlainText() }) {
                KeyValueList(it)
            }
        }
        item {
            SectionCard("DNS", state.dns, subtitle = "Cloudflare DoH", copyText = ::dnsText) {
                DnsContent(it)
            }
        }
        item {
            SectionCard(
                "Subdominios", state.subdomains,
                subtitle = "Certificate Transparency (crt.sh)",
                copyText = { it.joinToString("\n") },
            ) { SubdomainContent(it) }
        }
        item {
            SectionCard(
                "Exposición", state.exposure,
                subtitle = listOfNotNull("Shodan InternetDB", state.resolvedIp).joinToString(" · "),
                copyText = { it?.let(::exposureText) },
            ) { ExposureContent(it) }
        }
        item {
            SectionCard(
                "Geolocalización aproximada", state.geo,
                subtitle = "ip-api.com · nivel ISP/ciudad, no ubicación exacta",
                copyText = { geoRows(it).asPlainText() },
            ) { KeyValueList(geoRows(it)) }
        }
    }
}

@Composable
private fun DnsContent(dns: DnsRecords) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dns.records.isEmpty()) Text("Sin registros")
        dns.records.forEach { (type, values) ->
            Row {
                Text(type, Modifier.fillMaxWidth(0.18f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) { values.forEach { MonoText(it) } }
            }
        }
        dns.errors.forEach { (type, msg) ->
            Text("$type: $msg", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SubdomainContent(subs: List<String>) {
    var expanded by rememberSaveable(subs) { mutableStateOf(false) }
    Text("${subs.size} nombres únicos", style = MaterialTheme.typography.labelLarge)
    if (subs.isEmpty()) return
    val shown = if (expanded) subs else subs.take(SUBDOMAIN_PREVIEW)
    Column { shown.forEach { MonoText(it) } }
    if (subs.size > SUBDOMAIN_PREVIEW) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Mostrar menos" else "Mostrar los ${subs.size}")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExposureContent(host: InternetDbHost?) {
    if (host == null) {
        Text("Sin datos indexados: InternetDB no ha visto puertos públicos en esta IP.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Puertos abiertos", style = MaterialTheme.typography.labelLarge)
        if (host.ports.isEmpty()) Text("Ninguno")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            host.ports.sorted().forEach { port ->
                SuggestionChip(onClick = {}, label = { Text(port.toString(), fontFamily = FontFamily.Monospace) })
            }
        }
        if (host.vulns.isNotEmpty()) {
            Text("CVE asociadas (${host.vulns.size}) — por versión detectada, verificar",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            MonoText(host.vulns.sorted().joinToString("  "))
        }
        if (host.hostnames.isNotEmpty()) {
            Text("Hostnames", style = MaterialTheme.typography.labelLarge)
            host.hostnames.forEach { MonoText(it) }
        }
        if (host.cpes.isNotEmpty()) {
            Text("Software (CPE)", style = MaterialTheme.typography.labelLarge)
            host.cpes.forEach { MonoText(it) }
        }
        if (host.tags.isNotEmpty()) {
            Text("Etiquetas: ${host.tags.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun geoRows(g: IpGeo): List<KeyValue> = buildList {
    g.query?.let { add(KeyValue("IP", it)) }
    listOfNotNull(g.city, g.regionName, g.country).takeIf { it.isNotEmpty() }
        ?.let { add(KeyValue("Ubicación", it.joinToString(", "))) }
    g.isp?.let { add(KeyValue("ISP", it)) }
    g.org?.takeIf { it.isNotBlank() && it != g.isp }?.let { add(KeyValue("Organización", it)) }
    g.asn?.let { add(KeyValue("ASN", it)) }
    g.timezone?.let { add(KeyValue("Zona horaria", it)) }
    if (g.lat != null && g.lon != null) add(KeyValue("Coordenadas", "%.3f, %.3f".format(g.lat, g.lon)))
    if (g.hosting == true) add(KeyValue("Tipo", "Hosting / datacenter"))
    if (g.proxy == true) add(KeyValue("Proxy/VPN", "Sí"))
}

internal fun dnsText(d: DnsRecords): String =
    d.records.entries.joinToString("\n") { (t, v) -> v.joinToString("\n") { "$t\t$it" } }

internal fun exposureText(h: InternetDbHost): String = buildString {
    appendLine("IP: ${h.ip}")
    appendLine("Puertos: ${h.ports.sorted().joinToString(", ")}")
    if (h.vulns.isNotEmpty()) appendLine("CVE: ${h.vulns.sorted().joinToString(", ")}")
    if (h.hostnames.isNotEmpty()) appendLine("Hostnames: ${h.hostnames.joinToString(", ")}")
    if (h.cpes.isNotEmpty()) append("CPE: ${h.cpes.joinToString(", ")}")
}.trim()
