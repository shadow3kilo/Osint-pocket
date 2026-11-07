package com.osintpocket.app.ui.exif

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.CleanMode
import com.osintpocket.app.data.CleanResult
import com.osintpocket.app.data.ImageAnalysis
import com.osintpocket.app.data.Risk
import com.osintpocket.app.ui.common.ErrorRow
import com.osintpocket.app.ui.common.InfoCard
import com.osintpocket.app.ui.common.KeyValueList
import com.osintpocket.app.ui.common.LoadingRow
import com.osintpocket.app.ui.common.asPlainText
import java.util.Locale

@Composable
fun ExifScreen(vm: ExifViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::open)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::open)
    }
    // CreateDocument necesita un MIME fijo: un lanzador por formato de salida.
    val saveJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { it?.let(vm::save) }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { it?.let(vm::save) }
    val saveWebp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp")) { it?.let(vm::save) }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Galería")
                }
                OutlinedButton(onClick = { pickFile.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Folder, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Archivos")
                }
            }
        }

        when (val a = state.analysis) {
            SectionState.Idle -> item {
                Text(
                    "Elige una foto para ver qué revela (ubicación, dispositivo, fecha…) y generar una copia sin metadatos. " +
                        "La imagen se procesa en el móvil: no se sube a ningún sitio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionState.Loading -> item { InfoCard("Leyendo imagen", selectable = false) { LoadingRow("Analizando metadatos…") } }
            is SectionState.Error -> item { InfoCard("Error") { ErrorRow(a.message) } }
            is SectionState.Success -> {
                val img = a.data
                item { PreviewCard(img) }
                item { PrivacyCard(img) }
                img.gps?.let { gps ->
                    item {
                        val uriHandler = LocalUriHandler.current
                        val rows = buildList {
                            add(KeyValue("Latitud", coord(gps.lat)))
                            add(KeyValue("Longitud", coord(gps.lon)))
                            gps.altitude?.let { add(KeyValue("Altitud", "${String.format(Locale.ROOT, "%.0f", it)} m")) }
                            gps.timestamp?.let { add(KeyValue("Hora GPS (UTC)", it)) }
                        }
                        InfoCard("Ubicación GPS", copyText = "${coord(gps.lat)}, ${coord(gps.lon)}") {
                            KeyValueList(rows)
                            TextButton(onClick = {
                                runCatching {
                                    uriHandler.openUri(
                                        "https://www.openstreetmap.org/?mlat=${coord(gps.lat)}&mlon=${coord(gps.lon)}#map=17/${coord(gps.lat)}/${coord(gps.lon)}"
                                    )
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Ver en OpenStreetMap")
                            }
                        }
                    }
                }
                img.groups.forEach { g ->
                    item {
                        val rows = g.fields.map { KeyValue(it.label, it.value) }
                        InfoCard(g.title, copyText = rows.asPlainText()) { KeyValueList(rows) }
                    }
                }
                if (img.blocks.isNotEmpty()) {
                    item {
                        InfoCard("Bloques de metadatos en el archivo") {
                            img.blocks.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                item {
                    CleanCard(
                        state = state.clean,
                        onClean = vm::clean,
                        onSave = { c ->
                            when (c.mime) {
                                "image/png" -> savePng.launch(c.suggestedName)
                                "image/webp" -> saveWebp.launch(c.suggestedName)
                                else -> saveJpeg.launch(c.suggestedName)
                            }
                        },
                        onShare = {
                            vm.shareUri()?.let { (uri, mime) ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = ClipData.newRawUri("", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Compartir copia limpia"))
                            }
                        },
                    )
                }
                item {
                    Text(
                        "Android puede ocultar la ubicación GPS de las fotos a las apps sin el permiso de ubicación " +
                            "multimedia. Si una foto que sabes que tiene GPS aparece sin él, se debe a eso. " +
                            "La copia limpia elimina los metadatos igualmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(img: ImageAnalysis) {
    InfoCard(img.name, subtitle = listOfNotNull(
        img.mime,
        "${img.width}×${img.height}".takeIf { img.width > 0 },
        humanSize(img.sizeBytes),
    ).joinToString(" · ")) {
        img.preview?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Vista previa",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } ?: Text("Sin vista previa para este formato", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PrivacyCard(img: ImageAnalysis) {
    InfoCard("Qué revela esta imagen") {
        if (img.flags.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Color(0xFF3FB950))
                Spacer(Modifier.width(10.dp))
                Text("No se han encontrado metadatos sensibles", style = MaterialTheme.typography.bodyMedium)
            }
        }
        img.flags.forEach { f ->
            Row {
                Box(Modifier.padding(top = 5.dp)) { Dot(f.risk.color()) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.title, style = MaterialTheme.typography.titleSmall)
                    Text(f.detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CleanCard(
    state: SectionState<CleanResult>,
    onClean: () -> Unit,
    onSave: (CleanResult) -> Unit,
    onShare: () -> Unit,
) {
    InfoCard("Copia limpia", subtitle = "El original no se modifica", selectable = false) {
        when (state) {
            SectionState.Idle -> FilledTonalButton(onClick = onClean, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CleaningServices, null)
                Spacer(Modifier.width(8.dp))
                Text("Generar copia sin metadatos")
            }
            SectionState.Loading -> LoadingRow("Limpiando…")
            is SectionState.Error -> {
                ErrorRow(state.message)
                TextButton(onClick = onClean) { Text("Reintentar") }
            }
            is SectionState.Success -> {
                val c = state.data
                val rows = buildList {
                    add(KeyValue("Método", if (c.mode == CleanMode.LOSSLESS) "Sin pérdida (píxeles intactos)" else "Recodificada a JPEG"))
                    add(KeyValue("Tamaño", "${humanSize(c.originalBytes)} → ${humanSize(c.cleanBytes)}"))
                    add(KeyValue("Eliminado", c.removed.ifEmpty { listOf("Nada: no tenía metadatos") }.joinToString("\n")))
                }
                KeyValueList(rows)
                c.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (c.sensitiveRemaining.isEmpty()) {
                    Text("Verificado: sin GPS ni datos de dispositivo en la copia", style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3FB950))
                } else {
                    Text("Aún quedan: ${c.sensitiveRemaining.joinToString(", ")}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(c) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar")
                    }
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Compartir")
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun Risk.color(): Color = when (this) {
    Risk.HIGH -> Color(0xFFF85149)
    Risk.MEDIUM -> Color(0xFFD29922)
    Risk.LOW -> MaterialTheme.colorScheme.outline
}

private fun coord(d: Double) = String.format(Locale.ROOT, "%.6f", d)

private fun humanSize(b: Long): String = when {
    b >= 1_048_576 -> String.format(Locale.ROOT, "%.1f MB", b / 1_048_576.0)
    b >= 1024 -> String.format(Locale.ROOT, "%.0f KB", b / 1024.0)
    else -> "$b B"
}
