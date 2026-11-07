package com.osintpocket.app.ui.more

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osintpocket.app.BuildConfig
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.history.ExportFormat
import com.osintpocket.app.data.history.HistoryEntry
import com.osintpocket.app.data.history.HistoryExporter
import com.osintpocket.app.data.LeakProvider
import com.osintpocket.app.data.ProviderResult
import com.osintpocket.app.ui.common.ErrorRow
import com.osintpocket.app.ui.common.InfoCard
import com.osintpocket.app.ui.common.KeyValueList
import com.osintpocket.app.ui.common.LoadingRow
import com.osintpocket.app.ui.common.MonoText
import com.osintpocket.app.ui.common.QueryField
import java.text.NumberFormat

private val GREEN = Color(0xFF3FB950)
private val RED = Color(0xFFF85149)

// ───────────────────────── Menú "Más" ─────────────────────────

enum class MoreDest(val route: String, val title: String, val subtitle: String, val icon: ImageVector) {
    BREACH("more/breach", "Brechas", "Email y contraseñas en fuentes gratuitas de filtraciones", Icons.Outlined.WarningAmber),
    HASH("more/hash", "Hash de archivo", "MD5 / SHA-1 / SHA-256 y búsqueda en VirusTotal", Icons.Outlined.Fingerprint),
    HISTORY("more/history", "Historial", "Búsquedas guardadas y exportación JSON/Markdown", Icons.Outlined.History),
    SETTINGS("more/settings", "Ajustes", "API key de HIBP e historial", Icons.Outlined.Settings),
}

@Composable
fun MoreHomeScreen(onOpen: (MoreDest) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(MoreDest.entries) { d ->
            ListItem(
                headlineContent = { Text(d.title) },
                supportingContent = { Text(d.subtitle) },
                leadingContent = { Icon(d.icon, null) },
                modifier = Modifier.clickable { onOpen(d) },
            )
            HorizontalDivider()
        }
    }
}

// ───────────────────────── Brechas ─────────────────────────

@Composable
fun BreachScreen(onOpenSettings: () -> Unit, vm: BreachViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshKey()
        onPauseOrDispose { }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Email en filtraciones", style = MaterialTheme.typography.titleMedium) }
        item {
            QueryField(
                value = state.email,
                onValueChange = vm::onEmailChange,
                label = "Email",
                placeholder = "tu@correo.com",
                error = state.emailError,
                busy = state.emailBusy,
                onSubmit = vm::checkEmail,
                onCancel = vm::cancel,
                hint = "Comprueba tus propias cuentas",
                keyboardType = KeyboardType.Email,
            )
        }
        item {
            Text(
                "Se consulta a la vez en XposedOrNot, LeakCheck y Hudson Rock, que son gratis y no piden registro." +
                    if (state.hasKey) " También en HIBP con tu API key." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.hasKey) {
                TextButton(onClick = onOpenSettings) { Text("Añadir HIBP (opcional, de pago)") }
            }
        }

        if (state.providers.isNotEmpty()) {
            val done = state.providers.values.mapNotNull { (it as? SectionState.Success)?.data }
            if (done.isNotEmpty()) {
                item {
                    val hits = done.count { it.found }
                    Text(
                        if (hits == 0) "Sin filtraciones en ${done.size} de ${state.providers.size} fuentes"
                        else "Aparece en $hits de ${done.size} fuentes consultadas",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (hits == 0) GREEN else RED,
                    )
                }
            }
            LeakProvider.entries.filter { it in state.providers }.forEach { p ->
                item(key = p.name) { ProviderCard(p, state.providers.getValue(p)) }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { PasswordCheck(state, vm::onPasswordChange, vm::setUseXonForPassword, vm::checkPassword) }
    }
}

@Composable
private fun ProviderCard(p: LeakProvider, state: SectionState<ProviderResult>) {
    var expanded by rememberSaveable(p) { mutableStateOf(true) }
    InfoCard(p.title, subtitle = p.subtitle, copyText = (state as? SectionState.Success)?.data?.let(::providerText)) {
        when (state) {
            SectionState.Idle -> Unit
            SectionState.Loading -> LoadingRow()
            is SectionState.Error -> ErrorRow(state.message)
            is SectionState.Success -> {
                val r = state.data
                Text(r.summary, color = if (r.found) RED else GREEN, style = MaterialTheme.typography.bodyMedium)
                if (r.items.isNotEmpty()) {
                    val shown = if (expanded) r.items else emptyList()
                    shown.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider()
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        if (item.fields.isNotEmpty()) KeyValueList(item.fields)
                    }
                    if (r.items.size > 1) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Ocultar detalle" else "Ver las ${r.items.size} entradas")
                        }
                    }
                }
            }
        }
    }
}

private fun providerText(r: ProviderResult): String = buildString {
    append(r.provider.title).append(": ").append(r.summary)
    r.items.forEach { item ->
        append("\n- ").append(item.name)
        item.fields.forEach { append(" | ${it.key}: ${it.value}") }
    }
}

@Composable
private fun PasswordCheck(
    state: BreachUiState,
    onChange: (String) -> Unit,
    onToggleXon: (Boolean) -> Unit,
    onCheck: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    InfoCard("Contraseña filtrada", subtitle = "Gratis · la contraseña nunca sale del móvil", selectable = false) {
        Text(
            "Se calcula un hash en el móvil y solo se envía un prefijo. La contraseña no se guarda.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onCheck() }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Mostrar u ocultar")
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Comprobar también en XposedOrNot", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Envía 10 caracteres del hash en vez de 5, así que es menos anónimo que Pwned Passwords.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.useXonForPassword, onCheckedChange = onToggleXon)
        }
        Button(onClick = onCheck, enabled = state.password.isNotEmpty() && !state.passwordBusy) {
            Text("Comprobar")
        }
        PasswordResultRow("Pwned Passwords", state.pwnedResult)
        PasswordResultRow("XposedOrNot", state.xonPasswordResult)
    }
}

@Composable
private fun PasswordResultRow(source: String, result: SectionState<Long>) {
    when (result) {
        SectionState.Idle -> Unit
        SectionState.Loading -> LoadingRow("$source…")
        is SectionState.Error -> ErrorRow("$source: ${result.message}")
        is SectionState.Success -> if (result.data == 0L) {
            Text("$source: no aparece en filtraciones conocidas.", color = GREEN)
        } else {
            Text(
                "$source: aparece ${NumberFormat.getIntegerInstance().format(result.data)} veces. No la uses.",
                color = RED, style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

// ───────────────────────── Hash ─────────────────────────

@Composable
fun HashScreen(vm: HashViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::hash) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text("Elegir archivo")
            }
        }
        item {
            Text(
                "El hash se calcula en el móvil. El archivo no se sube: VirusTotal solo recibe el SHA-256 al abrir el enlace.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (val r = state.result) {
            SectionState.Idle -> Unit
            SectionState.Loading -> item {
                InfoCard("Calculando", selectable = false) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = vm::cancel) { Text("Cancelar") }
                }
            }
            is SectionState.Error -> item { InfoCard("Error") { ErrorRow(r.message) } }
            is SectionState.Success -> item {
                val f = r.data
                InfoCard(f.name, subtitle = "${NumberFormat.getIntegerInstance().format(f.hashes.bytes)} bytes", copyText = hashText(f)) {
                    listOf("MD5" to f.hashes.md5, "SHA-1" to f.hashes.sha1, "SHA-256" to f.hashes.sha256).forEach { (k, v) ->
                        Text(k, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MonoText(v)
                    }
                    TextButton(onClick = {
                        runCatching { uriHandler.openUri("https://www.virustotal.com/gui/file/${f.hashes.sha256}") }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Buscar en VirusTotal")
                    }
                }
            }
        }
    }
}

// ───────────────────────── Historial ─────────────────────────

@Composable
fun HistoryScreen(vm: HistoryViewModel = hiltViewModel()) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expandedId by rememberSaveable { mutableLongStateOf(-1L) }
    var confirmClear by remember { mutableStateOf(false) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)) { uri ->
        uri?.let { vm.export(it, ExportFormat.JSON) }
    }
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.MARKDOWN.mime)) { uri ->
        uri?.let { vm.export(it, ExportFormat.MARKDOWN) }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("Se eliminarán ${entries.size} entradas. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clear() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportJson.launch("osint-pocket-historial.json") }, enabled = entries.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Exportar JSON")
                }
                OutlinedButton(onClick = { exportMd.launch("osint-pocket-historial.md") }, enabled = entries.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Exportar MD")
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text("Aún no hay búsquedas guardadas.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(entries, key = { it.id }) { e ->
            HistoryRow(
                e = e,
                expanded = expandedId == e.id,
                onToggle = { expandedId = if (expandedId == e.id) -1L else e.id },
                onDelete = { vm.delete(e.id) },
            )
        }
        if (entries.isNotEmpty()) {
            item {
                TextButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Outlined.DeleteOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Borrar todo")
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry, expanded: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    InfoCard(
        title = e.query,
        subtitle = "${e.module} · ${HistoryExporter.human(e.createdAt)}",
        copyText = if (expanded) e.details else null,
        selectable = expanded,
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Text(e.summary, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Eliminar entrada") }
        }
        if (expanded && e.details.isNotBlank()) MonoText(e.details)
    }
}

// ───────────────────────── Ajustes ─────────────────────────

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var visible by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar el historial?") },
            text = { Text("No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            InfoCard("API key de Have I Been Pwned", selectable = false) {
                if (!state.keystoreAvailable) {
                    Text("El Keystore de este dispositivo no está disponible: no se puede guardar la clave de forma segura.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    state.savedKeyHint?.let { "Guardada y cifrada ($it)" } ?: "No hay ninguna clave guardada",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = state.keyInput,
                    onValueChange = vm::onKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.savedKeyHint == null) "Pegar API key" else "Sustituir API key") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Key, null) },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Mostrar u ocultar")
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::saveKey, enabled = state.keyInput.isNotBlank() && state.keystoreAvailable) { Text("Guardar") }
                    if (state.savedKeyHint != null) OutlinedButton(onClick = vm::deleteKey) { Text("Eliminar") }
                }
                TextButton(onClick = { runCatching { uriHandler.openUri("https://haveibeenpwned.com/API/Key") } }) {
                    Text("Obtener una API key")
                }
            }
        }
        item {
            InfoCard("Historial", selectable = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Guardar búsquedas", style = MaterialTheme.typography.bodyLarge)
                        Text("Solo en este móvil. Nunca se guardan contraseñas ni coordenadas GPS.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.historyEnabled, onCheckedChange = vm::setHistoryEnabled)
                }
                OutlinedButton(onClick = { confirmClear = true }) { Text("Borrar historial") }
            }
        }
        item {
            InfoCard("Acerca de", selectable = false) {
                KeyValueList(listOf(
                    KeyValue("Versión", BuildConfig.VERSION_NAME),
                    KeyValue("Uso", "Reconocimiento pasivo sobre objetivos propios o con autorización"),
                ))
            }
        }
    }
}
