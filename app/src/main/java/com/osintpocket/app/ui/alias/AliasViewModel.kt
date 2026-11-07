package com.osintpocket.app.ui.alias

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osintpocket.app.core.network.safeCall
import com.osintpocket.app.data.InvalidSiteListException
import com.osintpocket.app.data.SiteRepository
import com.osintpocket.app.data.UsernameChecker
import com.osintpocket.app.data.history.HistoryModule
import com.osintpocket.app.data.history.HistoryRepository
import com.osintpocket.app.domain.alias.AliasClassifier
import com.osintpocket.app.domain.alias.AliasStatus
import com.osintpocket.app.domain.alias.Site
import com.osintpocket.app.domain.alias.SiteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AliasFilter { ALL, FOUND, NOT_FOUND, UNKNOWN }

data class AliasUiState(
    val query: String = "",
    val inputError: String? = null,
    val sites: List<Site> = emptyList(),
    val source: SiteRepository.Source = SiteRepository.Source.DEFAULT,
    val listWarning: String? = null,
    val running: Boolean = false,
    val checkedFor: String? = null,
    val results: Map<String, SiteResult> = emptyMap(),
    val filter: AliasFilter = AliasFilter.ALL,
    val message: String? = null,
) {
    fun count(f: AliasFilter): Int = results.values.count { it.matches(f) }

    val visible: List<SiteResult>
        get() = results.values
            .filter { it.matches(filter) }
            .sortedWith(compareBy<SiteResult> { it.status.ordinal }.thenBy { it.site.name.lowercase() })

    val foundUrls: List<String>
        get() = results.values.filter { it.status == AliasStatus.FOUND }.map { it.profileUrl }.sorted()

    private fun SiteResult.matches(f: AliasFilter) = when (f) {
        AliasFilter.ALL -> true
        AliasFilter.FOUND -> status == AliasStatus.FOUND
        AliasFilter.NOT_FOUND -> status == AliasStatus.NOT_FOUND
        AliasFilter.UNKNOWN -> status == AliasStatus.UNKNOWN || status == AliasStatus.INVALID
    }
}

@HiltViewModel
class AliasViewModel @Inject constructor(
    private val siteRepo: SiteRepository,
    private val checker: UsernameChecker,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AliasUiState())
    val state: StateFlow<AliasUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        reloadSites()
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q, inputError = null) }

    fun setFilter(f: AliasFilter) = _state.update { it.copy(filter = f) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun search() {
        val username = _state.value.query.trim().removePrefix("@")
        if (!AliasClassifier.isValidUsername(username)) {
            _state.update { it.copy(inputError = "Usa letras, números, punto, guion o guion bajo (máx. 64)") }
            return
        }
        val sites = _state.value.sites
        if (sites.isEmpty()) {
            _state.update { it.copy(message = "No hay sitios cargados") }
            return
        }
        job?.cancel()
        _state.update {
            it.copy(running = true, checkedFor = username, results = emptyMap(), filter = AliasFilter.ALL)
        }
        job = viewModelScope.launch {
            try {
                checker.check(username, sites).collect { r ->
                    _state.update { it.copy(results = it.results + (r.site.name to r)) }
                }
                val s = _state.value
                history.record(
                    HistoryModule.ALIAS, username,
                    "${s.count(AliasFilter.FOUND)} encontrados · ${s.count(AliasFilter.UNKNOWN)} inciertos de ${s.results.size}",
                    buildString {
                        appendLine("Encontrado:")
                        s.foundUrls.forEach { appendLine("  $it") }
                        val unknown = s.results.values.filter { it.status == AliasStatus.UNKNOWN }
                        if (unknown.isNotEmpty()) {
                            appendLine("Incierto:")
                            unknown.sortedBy { it.site.name }.forEach { appendLine("  ${it.site.name}: ${it.detail}") }
                        }
                    }.trim(),
                )
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun importList(uri: Uri) {
        viewModelScope.launch {
            val r = safeCall { siteRepo.import(uri) }
            val msg = r.fold(
                onSuccess = { n -> "Lista importada: $n sitios" },
                onFailure = { e -> (e as? InvalidSiteListException)?.message ?: "Error al importar: ${e.message}" },
            )
            _state.update { it.copy(message = msg) }
            if (r.isSuccess) reloadSites()
        }
    }

    fun exportList(uri: Uri) {
        viewModelScope.launch {
            val r = safeCall { siteRepo.export(uri) }
            _state.update {
                it.copy(message = r.fold({ "Lista exportada" }, { e -> "Error al exportar: ${e.message}" }))
            }
        }
    }

    fun restoreDefault() {
        viewModelScope.launch {
            siteRepo.restoreDefault()
            _state.update { it.copy(message = "Restaurada la lista de fábrica") }
            reloadSites()
        }
    }

    private fun reloadSites() {
        viewModelScope.launch {
            val r = safeCall { siteRepo.load() }
            _state.update { s ->
                r.fold(
                    onSuccess = { l -> s.copy(sites = l.sites, source = l.source, listWarning = l.warning) },
                    onFailure = { e -> s.copy(sites = emptyList(), listWarning = "No se pudo cargar la lista: ${e.message}") },
                )
            }
        }
    }
}
