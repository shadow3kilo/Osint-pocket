package com.osintpocket.app.ui.alias

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osintpocket.app.data.SiteRepository
import com.osintpocket.app.domain.alias.AliasStatus
import com.osintpocket.app.domain.alias.SiteResult
import com.osintpocket.app.ui.common.QueryField

@Composable
fun AliasScreen(vm: AliasViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importList)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportList)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            QueryField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = "Alias / username",
                placeholder = "johndoe",
                error = state.inputError,
                busy = state.running,
                onSubmit = vm::search,
                onCancel = vm::cancel,
                hint = "Comprueba tus propios alias o con autorización",
                keyboardType = KeyboardType.Ascii,
            )
        }

        item {
            ListInfoRow(
                count = state.sites.size,
                source = state.source,
                warning = state.listWarning,
                onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                onExport = { exportLauncher.launch("osint-pocket-sites.json") },
                onRestore = vm::restoreDefault,
            )
        }

        if (state.checkedFor != null) {
            item {
                val total = state.sites.size.coerceAtLeast(1)
                val done = state.results.size
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "@${state.checkedFor} · $done/$total sitios" + if (state.running) "" else " · terminado",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (state.running) {
                        LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AliasFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { vm.setFilter(f) },
                            label = { Text("${f.label()} ${state.count(f)}") },
                        )
                    }
                }
            }
            if (state.foundUrls.isNotEmpty()) {
                item {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(state.foundUrls.joinToString("\n")))
                        Toast.makeText(context, "Enlaces copiados", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copiar ${state.foundUrls.size} enlaces encontrados")
                    }
                }
            }
            items(state.visible, key = { it.site.name }) { r ->
                ResultRow(r, onOpen = { runCatching { uriHandler.openUri(r.profileUrl) } })
                HorizontalDivider()
            }
            item {
                Text(
                    "Resultado heurístico: «encontrado» significa que la URL de perfil existe, " +
                        "no que pertenezca a la misma persona. «Incierto» suele ser un bloqueo anti-bot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ListInfoRow(
    count: Int,
    source: SiteRepository.Source,
    warning: String?,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count sitios · " + if (source == SiteRepository.Source.CUSTOM) "lista personalizada" else "lista de fábrica",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Opciones de la lista") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Importar lista JSON…") }, onClick = { menu = false; onImport() })
                    DropdownMenuItem(text = { Text("Exportar lista actual…") }, onClick = { menu = false; onExport() })
                    if (source == SiteRepository.Source.CUSTOM) {
                        DropdownMenuItem(text = { Text("Restaurar lista de fábrica") }, onClick = { menu = false; onRestore() })
                    }
                }
            }
        }
        warning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ResultRow(r: SiteResult, onOpen: () -> Unit) {
    val (icon, tint) = r.status.visual()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = r.status == AliasStatus.FOUND, onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = r.status.name, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.site.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(r.site.category, r.detail, r.timeMs?.let { "$it ms" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (r.status == AliasStatus.FOUND) {
            IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir perfil") }
        }
    }
}

@Composable
private fun AliasStatus.visual(): Pair<ImageVector, Color> = when (this) {
    AliasStatus.FOUND -> Icons.Filled.CheckCircle to Color(0xFF3FB950)
    AliasStatus.NOT_FOUND -> Icons.Outlined.Cancel to MaterialTheme.colorScheme.outline
    AliasStatus.UNKNOWN -> Icons.Outlined.Info to Color(0xFFD29922)
    AliasStatus.INVALID -> Icons.Outlined.Block to MaterialTheme.colorScheme.outline
}

private fun AliasFilter.label(): String = when (this) {
    AliasFilter.ALL -> "Todos"
    AliasFilter.FOUND -> "Encontrado"
    AliasFilter.NOT_FOUND -> "No existe"
    AliasFilter.UNKNOWN -> "Incierto"
}
