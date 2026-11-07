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
import com.osintpocket.app.data.SecurityRepository
import com.osintpocket.app.data.history.ExportFormat
import com.osintpocket.app.data.history.HistoryEntry
import com.osintpocket.app.data.history.HistoryModule
import com.osintpocket.app.data.history.HistoryRepository
import com.osintpocket.app.data.remote.HibpBreach
import com.osintpocket.app.domain.security.EmailValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val breaches: SectionState<List<HibpBreach>> = SectionState.Idle,
    val password: String = "",
    val passwordResult: SectionState<Int> = SectionState.Idle,
)

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

    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, passwordResult = SectionState.Idle) }

    fun checkEmail() {
        val email = _state.value.email.trim()
        if (!EmailValidator.isValid(email)) {
            _state.update { it.copy(emailError = "Email no válido") }
            return
        }
        job?.cancel()
        _state.update { it.copy(breaches = SectionState.Loading) }
        job = viewModelScope.launch {
            val r = safeCall { repo.breaches(email) }
            _state.update { s ->
                s.copy(breaches = r.fold({ SectionState.Success(it) }, { SectionState.Error(it.toUserMessage()) }))
            }
            r.getOrNull()?.let { list ->
                history.record(
                    HistoryModule.BREACH, email,
                    if (list.isEmpty()) "Sin brechas conocidas" else "${list.size} brechas",
                    list.joinToString("\n") { "${it.title} (${it.breachDate}): ${it.dataClasses.joinToString(", ")}" },
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { if (it.breaches is SectionState.Loading) it.copy(breaches = SectionState.Idle) else it }
    }

    /** La contraseña no se guarda en historial ni sale del móvil (solo 5 caracteres de su SHA-1). */
    fun checkPassword() {
        val pw = _state.value.password
        if (pw.isEmpty()) return
        _state.update { it.copy(passwordResult = SectionState.Loading) }
        viewModelScope.launch {
            val r = safeCall { repo.passwordCount(pw) }
            _state.update { s ->
                s.copy(
                    password = "",
                    passwordResult = r.fold({ SectionState.Success(it) }, { SectionState.Error(it.toUserMessage()) }),
                )
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
