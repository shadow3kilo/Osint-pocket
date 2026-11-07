package com.osintpocket.app.ui.more

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osintpocket.app.core.AppPrefs
import com.osintpocket.app.core.SecretStore
import com.osintpocket.app.core.network.safeCall
import com.osintpocket.app.core.network.toUserMessage
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.HashedFile
import com.osintpocket.app.data.LeakProvider
import com.osintpocket.app.data.ProviderResult
import com.osintpocket.app.data.SecurityRepository
import com.osintpocket.app.data.history.ExportFormat
import com.osintpocket.app.data.history.HistoryEntry
import com.osintpocket.app.data.history.HistoryModule
import com.osintpocket.app.data.history.HistoryRepository
import com.osintpocket.app.domain.security.EmailValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ───────────────────────── Brechas ─────────────────────────

data class BreachUiState(
    val email: String = "",
    val emailError: String? = null,
    val hasKey: Boolean = false,
    val checkedEmail: String? = null,
    val providers: Map<LeakProvider, SectionState<ProviderResult>> = emptyMap(),
    val password: String = "",
    val useXonForPassword: Boolean = false,
    val pwnedResult: SectionState<Long> = SectionState.Idle,
    val xonPasswordResult: SectionState<Long> = SectionState.Idle,
) {
    val emailBusy: Boolean get() = providers.values.any { it is SectionState.Loading }
    val passwordBusy: Boolean get() = pwnedResult is SectionState.Loading || xonPasswordResult is SectionState.Loading
}

@HiltViewModel
class BreachViewModel @Inject constructor(
    private val repo: SecurityRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BreachUiState(hasKey = repo.hasHibpKey()))
    val state: StateFlow<BreachUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun refreshKey() = _state.update { it.copy(hasKey = repo.hasHibpKey()) }

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, emailError = null) }

    fun onPasswordChange(v: String) = _state.update {
        it.copy(password = v, pwnedResult = SectionState.Idle, xonPasswordResult = SectionState.Idle)
    }

    fun setUseXonForPassword(v: Boolean) = _state.update { it.copy(useXonForPassword = v) }

    /** Consulta todas las fuentes gratuitas a la vez (y HIBP si hay API key). */
    fun checkEmail() {
        val email = _state.value.email.trim()
        if (!EmailValidator.isValid(email)) {
            _state.update { it.copy(emailError = "Email no válido") }
            return
        }
        val providers = buildList {
            add(LeakProvider.XON)
            add(LeakProvider.LEAKCHECK)
            add(LeakProvider.HUDSONROCK)
            if (repo.hasHibpKey()) add(LeakProvider.HIBP)
        }
        job?.cancel()
        _state.update { s -> s.copy(checkedEmail = email, providers = providers.associateWith { SectionState.Loading }) }
        job = viewModelScope.launch {
            coroutineScope {
                providers.forEach { p ->
                    launch {
                        val r = safeCall { repo.lookup(p, email) }
                        val section: SectionState<ProviderResult> =
                            r.fold({ SectionState.Success(it) }, { SectionState.Error(it.toUserMessage()) })
                        _state.update { s -> s.copy(providers = s.providers + (p to section)) }
                    }
                }
            }
            recordEmailHistory(email)
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { s -> s.copy(providers = s.providers.filterValues { it !is SectionState.Loading }) }
    }

    private suspend fun recordEmailHistory(email: String) {
        val results = _state.value.providers.mapNotNull { (p, st) -> (st as? SectionState.Success)?.data?.let { p to it } }
        if (results.isEmpty()) return
        val hits = results.filter { it.second.found }
        history.record(
            HistoryModule.BREACH, email,
            if (hits.isEmpty()) "Sin filtraciones en ${results.size} fuentes"
            else "Aparece en " + hits.joinToString(", ") { it.first.title },
            results.joinToString("\n\n") { (p, r) ->
                buildString {
                    append("[${p.title}] ${r.summary}")
                    r.items.forEach { item ->
                        append("\n- ${item.name}")
                        item.fields.forEach { f -> append(" | ${f.key}: ${f.value}") }
                    }
                }
            },
        )
    }

    /** La contraseña no se guarda en historial ni sale del móvil: solo un prefijo de su hash. */
    fun checkPassword() {
        val pw = _state.value.password
        if (pw.isEmpty()) return
        val useXon = _state.value.useXonForPassword
        _state.update {
            it.copy(
                password = "",
                pwnedResult = SectionState.Loading,
                xonPasswordResult = if (useXon) SectionState.Loading else SectionState.Idle,
            )
        }
        viewModelScope.launch {
            val r = safeCall { repo.passwordCount(pw).toLong() }
            _state.update { it.copy(pwnedResult = r.fold({ c -> SectionState.Success(c) }, { e -> SectionState.Error(e.toUserMessage()) })) }
        }
        if (useXon) {
            viewModelScope.launch {
                val r = safeCall { repo.passwordCountXon(pw) }
                _state.update { it.copy(xonPasswordResult = r.fold({ c -> SectionState.Success(c) }, { e -> SectionState.Error(e.toUserMessage()) })) }
            }
        }
    }
}

// ───────────────────────── Hash ─────────────────────────

data class HashUiState(
    val progress: Float = 0f,
    val result: SectionState<HashedFile> = SectionState.Idle,
)

@HiltViewModel
class HashViewModel @Inject constructor(
    private val repo: SecurityRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HashUiState())
    val state: StateFlow<HashUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun hash(uri: Uri) {
        job?.cancel()
        _state.value = HashUiState(result = SectionState.Loading)
        job = viewModelScope.launch {
            val r = safeCall { repo.hashFile(uri) { p -> _state.update { it.copy(progress = p) } } }
            _state.update { s ->
                s.copy(result = r.fold({ SectionState.Success(it) }, { SectionState.Error(it.message ?: "No se pudo leer el archivo") }))
            }
            r.getOrNull()?.let { f ->
                history.record(HistoryModule.HASH, f.name, "SHA-256 ${f.hashes.sha256.take(16)}…", hashText(f))
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = HashUiState()
    }
}

fun hashText(f: HashedFile) =
    "Archivo: ${f.name}\nTamaño: ${f.hashes.bytes} bytes\nMD5: ${f.hashes.md5}\nSHA-1: ${f.hashes.sha1}\nSHA-256: ${f.hashes.sha256}"

// ───────────────────────── Historial ─────────────────────────

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: HistoryRepository,
) : ViewModel() {

    val entries: StateFlow<List<HistoryEntry>> =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun clear() {
        viewModelScope.launch {
            repo.clear()
            _message.value = "Historial borrado"
        }
    }

    fun export(uri: Uri, format: ExportFormat) {
        viewModelScope.launch {
            val r = safeCall { repo.export(uri, format) }
            _message.value = r.fold({ "Exportadas $it entradas" }, { "Error al exportar: ${it.message}" })
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

// ───────────────────────── Ajustes ─────────────────────────

data class SettingsUiState(
    val keyInput: String = "",
    val savedKeyHint: String? = null,
    val keystoreAvailable: Boolean = true,
    val historyEnabled: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secrets: SecretStore,
    private val prefs: AppPrefs,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private fun load() = SettingsUiState(
        savedKeyHint = secrets.hibpKey?.let { "…" + it.takeLast(4) },
        keystoreAvailable = secrets.isAvailable,
        historyEnabled = prefs.historyEnabled,
    )

    fun onKeyChange(v: String) = _state.update { it.copy(keyInput = v) }

    fun saveKey() {
        val k = _state.value.keyInput.trim()
        if (k.isEmpty()) return
        secrets.hibpKey = k
        _state.value = load().copy(message = if (secrets.hibpKey != null) "API key guardada (cifrada)" else "No se pudo guardar la clave")
    }

    fun deleteKey() {
        secrets.hibpKey = null
        _state.value = load().copy(message = "API key eliminada")
    }

    fun setHistoryEnabled(v: Boolean) {
        prefs.historyEnabled = v
        _state.update { it.copy(historyEnabled = v) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            history.clear()
            _state.update { it.copy(message = "Historial borrado") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
