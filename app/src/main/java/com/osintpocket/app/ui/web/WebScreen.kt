package com.osintpocket.app.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.TlsInfo
import com.osintpocket.app.data.WebResult
import com.osintpocket.app.domain.web.Grade
import com.osintpocket.app.domain.web.HeaderCheck
import com.osintpocket.app.domain.web.Tech
import com.osintpocket.app.ui.common.ErrorRow
import com.osintpocket.app.ui.common.InfoCard
import com.osintpocket.app.ui.common.KeyValueList
import com.osintpocket.app.ui.common.LoadingRow
import com.osintpocket.app.ui.common.MonoText
import com.osintpocket.app.ui.common.QueryField
import com.osintpocket.app.ui.common.asPlainText

@Composable
fun WebScreen(vm: WebViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            QueryField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = "URL o dominio",
                placeholder = "https://ejemplo.com",
                error = state.inputError,
                busy = state.result is SectionState.Loading,
                onSubmit = vm::scan,
                onCancel = vm::cancel,
            )
        }
        when (val r = state.result) {
            SectionState.Idle -> Unit
            SectionState.Loading -> item { InfoCard("Analizando", selectable = false) { LoadingRow("Descargando cabeceras…") } }
            is SectionState.Error -> item { InfoCard("Error") { ErrorRow(r.message) } }
            is SectionState.Success -> results(r.data)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.results(res: WebResult) {
    item { SummaryCard(res) }
    item {
        InfoCard(
            "Cabeceras de seguridad",
            subtitle = "Puntúan las marcadas con peso; COOP y CORS son informativas",
            copyText = res.security.checks.joinToString("\n") { "[${it.grade.label()}] ${it.title}: ${it.note}" },
        ) {
            res.security.checks.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider()
                CheckRow(c)
            }
        }
    }
    item {
        InfoCard(
            "Tecnologías detectadas",
            subtitle = "Huella pasiva por cabeceras, cookies y HTML",
            copyText = res.tech.takeIf { it.isNotEmpty() }?.joinToString("\n") { "${it.name} (${it.category}) — ${it.evidence}" },
        ) { TechList(res.tech) }
    }
    res.scan.tls?.let { tls ->
        item {
            val rows = tlsRows(tls)
            InfoCard("TLS y certificado", copyText = rows.asPlainText()) { KeyValueList(rows) }
        }
    }
    item { RawHeadersCard(res) }
}

@Composable
private fun SummaryCard(res: WebResult) {
    val scan = res.scan
    val sec = res.security
    InfoCard("Resumen", copyText = "${scan.finalUrl}\nHTTP ${scan.code}\nNota ${sec.letter} (${sec.score}/100)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(sec.light.color()),
                contentAlignment = Alignment.Center,
            ) {
                Text(sec.letter.toString(), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("${sec.score}/100", style = MaterialTheme.typography.titleLarge)
                Text(
                    when (sec.light) {
                        Grade.GOOD -> "Buena configuración"
                        Grade.WARN -> "Mejorable"
                        else -> "Deficiente"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = sec.light.color(),
                )
            }
        }
        val rows = buildList {
            add(KeyValue("URL final", scan.finalUrl))
            add(KeyValue("Estado", "HTTP ${scan.code} ${scan.message}".trim()))
            add(KeyValue("Tiempo", "${scan.timeMs} ms"))
            scan.contentType?.let { add(KeyValue("Tipo", it)) }
            add(KeyValue("security.txt", when (scan.securityTxt) {
                true -> "Presente"
                false -> "No encontrado"
                null -> "No comprobado"
            }))
        }
        KeyValueList(rows)
        if (scan.redirects.isNotEmpty()) {
            Text("Redirecciones", style = MaterialTheme.typography.labelLarge)
            scan.redirects.forEach { MonoText("${it.code} ← ${it.url}") }
        }
        if (scan.code >= 400) {
            Text("La respuesta es un error: una página de error o un WAF pueden enviar cabeceras distintas a las del sitio real.",
                style = MaterialTheme.typography.bodySmall, color = Grade.WARN.color())
        }
        if (!scan.isHttps) {
            Text("El destino final se sirve por HTTP sin cifrar.",
                style = MaterialTheme.typography.bodySmall, color = Grade.BAD.color())
        }
    }
}

@Composable
private fun CheckRow(c: HeaderCheck) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(c.grade.color()))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (c.weight > 0) "${c.grade.label()} · ${c.weight}" else c.grade.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.grade.color(),
                )
            }
            Text(c.note, style = MaterialTheme.typography.bodySmall)
            c.value?.let { MonoText(it, maxLines = 4) }
        }
    }
}

@Composable
private fun TechList(tech: List<Tech>) {
    if (tech.isEmpty()) {
        Text("No se detectaron tecnologías conocidas", style = MaterialTheme.typography.bodyMedium)
        return
    }
    tech.groupBy { it.category }.forEach { (category, items) ->
        Text(category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        items.forEach { t ->
            Column(Modifier.padding(start = 8.dp)) {
                Text(t.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                MonoText(t.evidence, maxLines = 2)
            }
        }
    }
}

@Composable
private fun RawHeadersCard(res: WebResult) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val lines = res.scan.headers.map { (k, v) -> "$k: $v" }
    InfoCard("Todas las cabeceras (${lines.size})", copyText = lines.joinToString("\n")) {
        if (expanded) lines.forEach { MonoText(it) }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar" else "Mostrar") }
    }
}

private fun tlsRows(t: TlsInfo): List<KeyValue> = buildList {
    add(KeyValue("Protocolo", t.protocol))
    add(KeyValue("Cifrado", t.cipher))
    t.subject?.let { add(KeyValue("Certificado", it)) }
    t.issuer?.let { add(KeyValue("Emisor", it)) }
    if (t.notAfter != null && t.daysLeft != null) {
        val status = when {
            t.daysLeft < 0 -> " · CADUCADO"
            t.daysLeft < 15 -> " · caduca pronto"
            else -> ""
        }
        add(KeyValue("Caduca", "${t.notAfter} (${t.daysLeft} días)$status"))
    }
    if (t.sans.isNotEmpty()) {
        val shown = t.sans.take(12).joinToString("\n")
        add(KeyValue("SAN (${t.sans.size})", if (t.sans.size > 12) "$shown\n…" else shown))
    }
}

@Composable
private fun Grade.color(): Color = when (this) {
    Grade.GOOD -> Color(0xFF3FB950)
    Grade.WARN -> Color(0xFFD29922)
    Grade.BAD -> Color(0xFFF85149)
    Grade.INFO -> MaterialTheme.colorScheme.outline
}

private fun Grade.label(): String = when (this) {
    Grade.GOOD -> "OK"
    Grade.WARN -> "Mejorable"
    Grade.BAD -> "Falta"
    Grade.INFO -> "Info"
}
