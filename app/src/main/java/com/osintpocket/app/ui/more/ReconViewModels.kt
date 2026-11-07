package com.osintpocket.app.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osintpocket.app.core.network.safeCall
import com.osintpocket.app.core.network.toUserMessage
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.EmailReconRepository
import com.osintpocket.app.data.GravatarResult
import com.osintpocket.app.data.PhoneInfo
import com.osintpocket.app.data.PhoneRepository
import com.osintpocket.app.data.history.HistoryModule
import com.osintpocket.app.data.history.HistoryRepository
import com.osintpocket.app.domain.dorks.Dork
import com.osintpocket.app.domain.dorks.DorkTarget
import com.osintpocket.app.domain.dorks.Dorks
import com.osintpocket.app.domain.security.EmailValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ───────────────────────── Teléfono ─────────────────────────

data class PhoneUiState(
    val query: String = "",
    val region: String = "ES",
    val result: PhoneInfo? = null,
    val error: String? = null,
)

@HiltViewModel
class PhoneViewModel @Inject constructor(
    private val repo: PhoneRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PhoneUiState())
    val state: StateFlow<PhoneUiState> = _state.asStateFlow()

    fun onQuery(v: String) = _state.update { it.copy(query = v, error = null) }
    fun onRegion(v: String) = _state.update { it.copy(region = v.uppercase().take(2)) }

    fun lookup() {
        val raw = _state.value.query.trim()
        if (raw.isEmpty()) return
        // Todo local: no hay red, el resultado es inmediato.
        val info = repo.lookup(raw, _state.value.region.ifBlank { null })
        _state.update { it.copy(result = info, error = info.note.takeIf { _ -> !info.valid && !info.possible }) }
        viewModelScope.launch {
            history.record(
                HistoryModule.PHONE, raw,
                if (info.valid) "${info.type} · ${info.country ?: info.regionCode ?: ""}".trim() else "No válido",
                info.asRows().joinToString("\n") { "${it.key}: ${it.value}" },
            )
        }
    }
}

// ───────────────────────── Dorks ─────────────────────────

data class DorksUiState(
    val target: DorkTarget = DorkTarget.USERNAME,
    val value: String = "",
    val dorks: List<Dork> = emptyList(),
)

@HiltViewModel
class DorksViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(DorksUiState())
    val state: StateFlow<DorksUiState> = _state.asStateFlow()

    fun onTarget(t: DorkTarget) = _state.update { it.copy(target = t, dorks = Dorks.generate(t, it.value)) }
    fun onValue(v: String) = _state.update { it.copy(value = v, dorks = Dorks.generate(it.target, v)) }
}

// ───────────────────────── Email / Gravatar ─────────────────────────

data class EmailReconUiState(
    val email: String = "",
    val inputError: String? = null,
    val gravatar: SectionState<GravatarResult> = SectionState.Idle,
)

@HiltViewModel
class EmailReconViewModel @Inject constructor(
    private val repo: EmailReconRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EmailReconUiState())
    val state: StateFlow<EmailReconUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v, inputError = null) }

    fun check() {
        val email = _state.value.email.trim()
        if (!EmailValidator.isValid(email)) {
            _state.update { it.copy(inputError = "Email no válido") }
            return
        }
        _state.update { it.copy(gravatar = SectionState.Loading) }
        viewModelScope.launch {
            val r = safeCall { repo.gravatar(email) }
            _state.update { s -> s.copy(gravatar = r.fold({ SectionState.Success(it) }, { e -> SectionState.Error(e.toUserMessage()) })) }
            r.getOrNull()?.let { g ->
                history.record(
                    HistoryModule.EMAIL, email,
                    if (g.hasAvatar || g.fields.isNotEmpty()) "Perfil de Gravatar encontrado" else "Sin perfil público de Gravatar",
                    (g.fields + g.accounts).joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "Sin datos públicos" },
                )
            }
        }
    }
}
