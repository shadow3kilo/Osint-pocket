package com.osintpocket.app.ui.more

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.osintpocket.app.core.network.safeCall
import com.osintpocket.app.core.network.toUserMessage
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.Catalog
import com.osintpocket.app.data.HibpCatalogRepository
import com.osintpocket.app.data.history.HistoryExporter
import com.osintpocket.app.data.history.HistoryModule
import com.osintpocket.app.data.history.HistoryRepository
import com.osintpocket.app.data.remote.HibpBreach
import com.osintpocket.app.domain.security.CatalogSort
import com.osintpocket.app.domain.security.HibpCatalog
import com.osintpocket.app.ui.common.ErrorRow
import com.osintpocket.app.ui.common.InfoCard
import com.osintpocket.app.ui.common.KeyValueList
import com.osintpocket.app.ui.common.LoadingRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

private const val HIBP_URL = "https://haveibeenpwned.com"
private const val PAGE = 60

// ───────────────────────── ViewModel ─────────────────────────

data class CatalogUiState(
    val catalog: SectionState<Catalog> = SectionState.Idle,
    val refreshing: Boolean = false,
    val query: String = "",
    val sort: CatalogSort = CatalogSort.ADDED,
    val dataClass: String? = null,
    val shown: Int = PAGE,
    val servicesInput: String = "",
    val services: Map<String, List<HibpBreach>>? = null,
) {
    private val all: List<HibpBreach> get() = (catalog as? SectionState.Success)?.data?.breaches.orEmpty()
    val filtered: List<HibpBreach> get() = HibpCatalog.filter(all, query, dataClass, sort)
    val dataClasses: List<Pair<String, Int>> get() = HibpCatalog.dataClasses(all)
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repo: HibpCatalogRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        load(force = false)
    }

    fun load(force: Boolean) {
        _state.update {
            if (it.catalog is SectionState.Success) it.copy(refreshing = true) else it.copy(catalog = SectionState.Loading)
        }
        viewModelScope.launch {
            val r = safeCall { repo.get(forceRefresh = force) }
            _state.update { s ->
                r.fold(
                    { s.copy(catalog = SectionState.Success(it), refreshing = false) },
                    { e ->
                        // Si ya había catálogo, se conserva y solo falla la actualización.
                        if (s.catalog is SectionState.Success) s.copy(refreshing = false)
                        else s.copy(catalog = SectionState.Error(e.toUserMessage()), refreshing = false)
                    },
                )
            }
        }
    }

    fun onQuery(v: String) = _state.update { it.copy(query = v, shown = PAGE) }
    fun onSort(v: CatalogSort) = _state.update { it.copy(sort = v, shown = PAGE) }
    fun onDataClass(v: String?) = _state.update { it.copy(dataClass = v, shown = PAGE) }
    fun showMore() = _state.update { it.copy(shown = it.shown + PAGE) }
    fun onServicesInput(v: String) = _state.update { it.copy(servicesInput = v) }

    fun checkServices() {
        val s = _state.value
        val all = (s.catalog as? SectionState.Success)?.data?.breaches ?: return
        val result = HibpCatalog.forServices(all, s.servicesInput)
        _state.update { it.copy(services = result) }
        if (result.isEmpty()) return
        viewModelScope.launch {
            val hits = result.filterValues { it.isNotEmpty() }
            history.record(
                HistoryModule.BREACH,
                result.keys.joinToString(", "),
                if (hits.isEmpty()) "Ningún servicio en el catálogo de HIBP"
                else "${hits.size} de ${result.size} servicios con brechas (catálogo HIBP)",
                result.entries.joinToString("\n") { (d, list) ->
                    if (list.isEmpty()) "$d: sin brechas" else "$d: " + list.joinToString("; ") { "${it.title} (${it.breachDate}, ${it.pwnCount} cuentas)" }
                } + "\n\nFuente: Have I Been Pwned (CC BY 4.0)",
            )
        }
    }

    fun clearServices() = _state.update { it.copy(services = null, servicesInput = "") }
}

// ───────────────────────── Pantalla ─────────────────────────

@Composable
fun CatalogScreen(vm: CatalogViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Atribución obligatoria (CC BY 4.0), siempre visible.
        item {
            Row(
                Modifier.fillMaxWidth().clickable { runCatching { uriHandler.openUri(HIBP_URL) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Datos de Have I Been Pwned · haveibeenpwned.com · licencia CC BY 4.0",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir haveibeenpwned.com", tint = MaterialTheme.colorScheme.primary)
            }
        }

        when (val c = state.catalog) {
            SectionState.Idle, SectionState.Loading -> item {
                InfoCard("Catálogo de brechas", selectable = false) { LoadingRow("Descargando el catálogo de HIBP…") }
            }
            is SectionState.Error -> item {
                InfoCard("Catálogo de brechas") {
                    ErrorRow(c.message)
                    TextButton(onClick = { vm.load(force = true) }) { Text("Reintentar") }
                }
            }
            is SectionState.Success -> {
                val cat = c.data
                item { SummaryCard(cat, state.refreshing) { vm.load(force = true) } }
                item { ServicesCard(state, vm::onServicesInput, vm::checkServices, vm::clearServices) }
                item { Filters(state, vm::onQuery, vm::onSort, vm::onDataClass) }

                val list = state.filtered
                item {
                    Text("${list.size} brechas", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(list.take(state.shown), key = { it.name }) { BreachRow(it) }
                if (list.size > state.shown) {
                    item {
                        OutlinedButton(onClick = vm::showMore, modifier = Modifier.fillMaxWidth()) {
                            Text("Mostrar más (${list.size - state.shown} restantes)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(cat: Catalog, refreshing: Boolean, onRefresh: () -> Unit) {
    val latest = HibpCatalog.latest(cat.breaches)
    InfoCard("Catálogo de brechas", subtitle = "API pública y gratuita de HIBP, sin API key", selectable = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                KeyValueList(buildList {
                    add(KeyValue("Brechas", num(cat.breaches.size.toLong())))
                    add(KeyValue("Cuentas", num(HibpCatalog.totalAccounts(cat.breaches))))
                    latest?.let { add(KeyValue("Última añadida", "${it.title} (${it.addedDate.take(10)})")) }
                    add(KeyValue("Actualizado", HistoryExporter.human(cat.fetchedAt) + if (cat.fromCache) " · copia local" else ""))
                })
            }
            if (refreshing) LoadingRow("") else IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Actualizar") }
        }
    }
}

@Composable
private fun ServicesCard(
    state: CatalogUiState,
    onInput: (String) -> Unit,
    onCheck: () -> Unit,
    onClear: () -> Unit,
) {
    InfoCard(
        "¿Han filtrado los servicios que usas?",
        subtitle = "Escribe los dominios separados por comas o espacios",
        selectable = false,
    ) {
        OutlinedTextField(
            value = state.servicesInput,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("linkedin.com, adobe.com, canva.com") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Default, autoCorrectEnabled = false),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCheck, enabled = state.servicesInput.isNotBlank()) { Text("Comprobar") }
            if (state.services != null) OutlinedButton(onClick = onClear) { Text("Limpiar") }
        }
        state.services?.let { res ->
            if (res.isEmpty()) {
                Text("No he reconocido ningún dominio.", color = MaterialTheme.colorScheme.error)
            }
            res.forEach { (domain, list) ->
                if (list.isEmpty()) {
                    Text("$domain: sin brechas en el catálogo", color = GREEN, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("$domain: ${list.size} brecha(s)", color = RED, style = MaterialTheme.typography.titleSmall)
                    list.forEach { b ->
                        Text(
                            "• ${b.title} · ${b.breachDate} · ${num(b.pwnCount)} cuentas · ${b.dataClasses.take(4).joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (res.values.any { it.isNotEmpty() }) {
                Text(
                    "Si tenías cuenta en ese servicio antes de la fecha de la brecha, cambia esa contraseña y cualquier otra igual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Filters(
    state: CatalogUiState,
    onQuery: (String) -> Unit,
    onSort: (CatalogSort) -> Unit,
    onDataClass: (String?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar por nombre o dominio") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Borrar") }
            },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatalogSort.entries.forEach { s ->
                FilterChip(selected = state.sort == s, onClick = { onSort(s) }, label = { Text(s.label) })
            }
        }
        Box {
            OutlinedButton(onClick = { menu = true }) {
                Text(state.dataClass?.let { "Datos: $it" } ?: "Filtrar por dato expuesto")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Todos") }, onClick = { menu = false; onDataClass(null) })
                state.dataClasses.take(40).forEach { (dc, n) ->
                    DropdownMenuItem(text = { Text("$dc ($n)") }, onClick = { menu = false; onDataClass(dc) })
                }
            }
        }
    }
}

@Composable
private fun BreachRow(b: HibpBreach) {
    var expanded by remember(b.name) { mutableStateOf(false) }
    val tags = buildList {
        if (!b.isVerified) add("no verificada")
        if (b.isFabricated) add("posiblemente falsa")
        if (b.isSensitive) add("sensible")
        if (b.isSpamList) add("lista de spam")
        if (b.isMalware) add("malware")
        if (b.isStealerLog) add("infostealer")
        if (b.isRetired) add("retirada")
    }
    Column(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.title.ifBlank { b.name }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(num(b.pwnCount), style = MaterialTheme.typography.labelMedium, color = RED)
        }
        Text(
            listOfNotNull(b.domain.ifBlank { null }, "brecha ${b.breachDate}", tags.joinToString(", ").ifBlank { null })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (tags.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFD29922),
        )
        if (expanded) {
            KeyValueList(listOf(
                KeyValue("Datos expuestos", b.dataClasses.joinToString(", ")),
                KeyValue("Añadida a HIBP", b.addedDate.take(10)),
            ))
            val desc = remember(b.description) {
                HtmlCompat.fromHtml(b.description, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            }
            if (desc.isNotEmpty()) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
    }
}

private fun num(n: Long) = NumberFormat.getIntegerInstance(Locale("es", "ES")).format(n)
