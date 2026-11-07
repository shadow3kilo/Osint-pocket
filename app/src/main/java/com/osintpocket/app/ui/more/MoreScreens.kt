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
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osintpocket.app.BuildConfig
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.history.ExportFormat
import com.osintpocket.app.data.history.HistoryEntry
import com.osintpocket.app.data.history.HistoryExporter
import com.osintpocket.app.data.remote.HibpBreach
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
    BREACH("more/breach", "Brechas", "Email en Have I Been Pwned y contraseñas filtradas", Icons.Outlined.WarningAmber),
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
        item {
            Text("Email en brechas (HIBP)", style = MaterialTheme.typography.titleMedium)
        }
        if (!state.hasKey) {
            item {
                InfoCard("Falta la API key") {
                    Text(
                        "La búsqueda por email de Have I Been Pwned requiere una API key de pago. " +
                            "Guárdala en Ajustes; se almacena cifrada en el Keystore.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onOpenSettings) { Text("Ir a Ajustes") }
                }
            }
        }
        item {
            QueryField(
                value = state.email,
                onValueChange = vm::onEmailChange,
                label = "Email",
                placeholder = "tu@correo.com",
                error = state.emailError,
                busy = state.breaches is SectionState.Loading,
                onSubmit = vm::checkEmail,
                onCancel = vm::cancel,
                hint = "Comprueba tus propias cuentas",
                keyboardType = KeyboardType.Email,
            )
        }
        when (val b = state.breaches) {
            SectionState.Idle -> Unit
            SectionState.Loading -> item { InfoCard("Consultando", selectable = false) { LoadingRow() } }
            is SectionState.Error -> item { InfoCard("Error") { ErrorRow(b.message) } }
            is SectionState.Success -> {
                if (b.data.isEmpty()) {
                    item {
                        InfoCard("Sin brechas conocidas") {
                            Text("Este email no aparece en las brechas públicas que indexa HIBP.", color = GREEN)
                        }
                    }
                } else {
                    item {
                        Text("Aparece en ${b.data.size} brechas", style = MaterialTheme.typography.titleSmall, color = RED)
                    }
                    items(b.data, key = { it.name }) { BreachCard(it) }
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { PasswordCheck(state, vm::onPasswordChange, vm::checkPassword) }
    }
}

@Composable
private fun BreachCard(b: HibpBreach) {
    val desc = remember(b.description) {
        HtmlCompat.fromHtml(b.description, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    }
    val rows = buildList {
        add(KeyValue("Fecha", b.breachDate))
        if (b.domain.isNotBlank()) add(KeyValue("Dominio", b.domain))
        add(KeyValue("Cuentas", NumberFormat.getIntegerInstance().format(b.pwnCount)))
        add(KeyValue("Datos expuestos", b.dataClasses.joinToString(", ")))
        val tags = listOfNotNull("no verificada".takeUnless { b.isVerified }, "sensible".takeIf { b.isSensitive }, "lista de spam".takeIf { b.isSpamList })
        if (tags.isNotEmpty()) add(KeyValue("Notas", tags.joinToString(", ")))
    }
    InfoCard(b.title.ifBlank { b.name }) {
        KeyValueList(rows)
        if (desc.isNotEmpty()) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PasswordCheck(state: BreachUiState, onChange: (String) -> Unit, onCheck: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    InfoCard("Contraseña filtrada", subtitle = "Pwned Passwords · gratis, sin API key", selectable = false) {
        Text(
            "Se calcula el SHA-1 en el móvil y solo se envían sus 5 primeros caracteres (k-anonimato). " +
                "La contraseña no se guarda ni se envía.",
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
        Button(onClick = onCheck, enabled = state.password.isNotEmpty() && state.passwordResult !is SectionState.Loading) {
            Text("Comprobar")
        }
        when (val r = state.passwordResult) {
            SectionState.Idle -> Unit
            SectionState.Loading -> LoadingRow()
            is SectionState.Error -> ErrorRow(r.message)
            is SectionState.Success -> if (r.data == 0) {
                Text("No aparece en filtraciones conocidas.", color = GREEN)
            } else {
                Text(
                    "Aparece ${NumberFormat.getIntegerInstance().format(r.data)} veces en filtraciones. No la uses.",
                    color = RED, style = MaterialTheme.typography.titleSmall,
                )
            }
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
