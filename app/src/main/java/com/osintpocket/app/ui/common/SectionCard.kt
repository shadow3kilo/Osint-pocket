package com.osintpocket.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.core.ui.SectionState

/** Tarjeta base: título, subtítulo opcional, botón de copiar y contenido seleccionable. */
@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    copyText: String? = null,
    selectable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                copyText?.let { text ->
                    IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar $title")
                    }
                }
            }
            if (selectable) {
                SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

/** Tarjeta ligada a un SectionState: no se pinta en Idle, muestra carga/error y el contenido en éxito. */
@Composable
fun <T> SectionCard(
    title: String,
    state: SectionState<T>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    copyText: (T) -> String? = { null },
    content: @Composable (T) -> Unit,
) {
    when (state) {
        SectionState.Idle -> Unit
        SectionState.Loading -> InfoCard(title, modifier, subtitle, selectable = false) { LoadingRow() }
        is SectionState.Error -> InfoCard(title, modifier, subtitle) { ErrorRow(state.message) }
        is SectionState.Success -> InfoCard(title, modifier, subtitle, copyText = copyText(state.data)) {
            content(state.data)
        }
    }
}

@Composable
fun LoadingRow(text: String = "Consultando…") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorRow(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun KeyValueList(items: List<KeyValue>) {
    if (items.isEmpty()) {
        Text("Sin datos", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { kv ->
            Row {
                Text(kv.key, Modifier.width(112.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(kv.value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

fun List<KeyValue>.asPlainText(): String = joinToString("\n") { "${it.key}: ${it.value}" }
