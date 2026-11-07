package com.osintpocket.app.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osintpocket.app.core.network.safeCall
import com.osintpocket.app.core.network.toUserMessage
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.WebRepository
import com.osintpocket.app.data.WebResult
import com.osintpocket.app.data.history.HistoryModule
import com.osintpocket.app.data.history.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WebUiState(
    val query: String = "",
    val inputError: String? = null,
    val result: SectionState<WebResult> = SectionState.Idle,
)

@HiltViewModel
class WebViewModel @Inject constructor(
    private val repo: WebRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WebUiState())
    val state: StateFlow<WebUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(q: String) = _state.update { it.copy(query = q, inputError = null) }

    fun scan() {
        val url = WebRepository.normalizeUrl(_state.value.query)
        if (url == null) {
            _state.update { it.copy(inputError = "Introduce una URL o dominio válidos") }
            return
        }
        job?.cancel()
        _state.update { it.copy(result = SectionState.Loading) }
        job = viewModelScope.launch {
            val r = safeCall { repo.scan(url) }
            _state.update {
                it.copy(result = r.fold({ res -> SectionState.Success(res) }, { e -> SectionState.Error(e.toUserMessage()) }))
            }
            r.getOrNull()?.let { res ->
                val sec = res.security
                history.record(
                    HistoryModule.WEB, res.scan.finalUrl,
                    "Nota ${sec.letter} (${sec.score}/100) · HTTP ${res.scan.code} · ${res.tech.size} tecnologías",
                    buildString {
                        appendLine("URL final: ${res.scan.finalUrl}")
                        res.scan.redirects.forEach { appendLine("  ${it.code} <- ${it.url}") }
                        appendLine()
                        sec.checks.forEach { appendLine("[${it.grade}] ${it.title}: ${it.note}") }
                        if (res.tech.isNotEmpty()) {
                            appendLine()
                            res.tech.forEach { appendLine("${it.name} (${it.category}) - ${it.evidence}") }
                        }
                    }.trim(),
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { if (it.result is SectionState.Loading) it.copy(result = SectionState.Error("Cancelado")) else it }
    }
}
