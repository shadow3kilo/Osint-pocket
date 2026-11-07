package com.osintpocket.app.ui.more

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.GravatarResult
import com.osintpocket.app.domain.dorks.Dork
import com.osintpocket.app.domain.dorks.DorkTarget
import com.osintpocket.app.domain.dorks.SearchEngine
import com.osintpocket.app.ui.common.ErrorRow
import com.osintpocket.app.ui.common.InfoCard
import com.osintpocket.app.ui.common.KeyValueList
import com.osintpocket.app.ui.common.LoadingRow
import com.osintpocket.app.ui.common.QueryField
import com.osintpocket.app.ui.common.asPlainText

// ───────────────────────── Teléfono ─────────────────────────

@Composable
fun PhoneScreen(vm: PhoneViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    QueryField(
                        value = state.query,
                        onValueChange = vm::onQuery,
                        label = "Número de teléfono",
                        placeholder = "+34 612 345 678",
                        error = state.error,
                        busy = false,
                        onSubmit = vm::lookup,
                        onCancel = {},
                        hint = "Con prefijo + o indica el país",
                        keyboardType = KeyboardType.Phone,
                    )
                }
                OutlinedTextField(
                    value = state.region,
                    onValueChange = vm::onRegion,
                    modifier = Modifier.width(88.dp),
                    label = { Text("País") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                )
            }
        }
        item {
            Text(
                "Todo se resuelve en el móvil con la librería de Google, sin conexión. El operador es el de " +
                    "asignación original del rango; si el número tiene portabilidad, no se refleja.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.result?.let { info ->
            item {
                InfoCard(
                    if (info.valid) "Número válido" else "Número no válido",
                    copyText = info.asRows().asPlainText(),
                ) {
                    KeyValueList(info.asRows())
                    info.note?.takeIf { info.valid || info.possible }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ───────────────────────── Dorks ─────────────────────────

@Composable
fun DorksScreen(vm: DorksViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var engine by remember { mutableStateOf(SearchEngine.GOOGLE) }
    var engineMenu by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DorkTarget.entries.forEach { t ->
                    FilterChip(selected = state.target == t, onClick = { vm.onTarget(t) }, label = { Text(t.label) })
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.value,
                onValueChange = vm::onValue,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(state.target.label) },
                placeholder = { Text(state.target.hint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buscador:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { engineMenu = true }) { Text(engine.label) }
                    DropdownMenu(expanded = engineMenu, onDismissRequest = { engineMenu = false }) {
                        SearchEngine.entries.forEach { e ->
                            DropdownMenuItem(text = { Text(e.label) }, onClick = { engine = e; engineMenu = false })
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Genera consultas de buscador sobre fuentes públicas. Ábrelas con Tor activado si quieres anonimato; " +
                    "algunos buscadores piden captcha al salir por Tor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val byCategory = state.dorks.groupBy { it.category }
        byCategory.forEach { (category, list) ->
            item(key = category) {
                InfoCard(category) {
                    list.forEach { dork ->
                        DorkRow(dork) { runCatching { uriHandler.openUri(engine.url(dork.query)) } }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DorkRow(dork: Dork, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dork.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                Spacer(Modifier.width(4.dp))
                Text("Abrir")
            }
        }
        Text(dork.query, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ───────────────────────── Email / Gravatar ─────────────────────────

@Composable
fun EmailReconScreen(vm: EmailReconViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            QueryField(
                value = state.email,
                onValueChange = vm::onEmail,
                label = "Email",
                placeholder = "tu@correo.com",
                error = state.inputError,
                busy = state.gravatar is SectionState.Loading,
                onSubmit = vm::check,
                onCancel = {},
                hint = "Perfil público de Gravatar (gratis, sin key)",
                keyboardType = KeyboardType.Email,
            )
        }
        item {
            Text(
                "Gravatar vincula un email con un perfil público. Si existe, revela avatar y, a veces, nombre, " +
                    "ubicación y cuentas enlazadas. Que no haya perfil no significa nada malo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (val g = state.gravatar) {
            SectionState.Idle -> Unit
            SectionState.Loading -> item { InfoCard("Consultando Gravatar", selectable = false) { LoadingRow() } }
            is SectionState.Error -> item { InfoCard("Error") { ErrorRow(g.message) } }
            is SectionState.Success -> item { GravatarCard(g.data, onOpen = { runCatching { uriHandler.openUri(it) } }) }
        }
    }
}

@Composable
private fun GravatarCard(g: GravatarResult, onOpen: (String) -> Unit) {
    val hasProfile = g.fields.isNotEmpty() || g.accounts.isNotEmpty()
    InfoCard(
        if (g.hasAvatar || hasProfile) "Perfil de Gravatar encontrado" else "Sin perfil público de Gravatar",
        copyText = (g.fields + g.accounts).asPlainText().ifBlank { null },
    ) {
        if (!g.hasAvatar && !hasProfile) {
            Text("Este email no tiene avatar ni perfil público en Gravatar.", color = GREEN)
            return@InfoCard
        }
        Text(if (g.hasAvatar) "Tiene avatar público" else "Sin avatar", color = if (g.hasAvatar) RED else GREEN,
            style = MaterialTheme.typography.bodyMedium)
        if (g.fields.isNotEmpty()) KeyValueList(g.fields)
        if (g.accounts.isNotEmpty()) {
            Text("Cuentas enlazadas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            g.accounts.forEach { acc ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(acc.key, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (acc.value.startsWith("http")) {
                        TextButton(onClick = { onOpen(acc.value) }) { Text("Abrir") }
                    }
                }
            }
        }
        AssistChip(onClick = { onOpen(g.profileUrl) }, label = { Text("Ver perfil en Gravatar") })
    }
}
