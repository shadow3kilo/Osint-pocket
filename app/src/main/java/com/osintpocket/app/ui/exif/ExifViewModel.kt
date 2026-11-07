package com.osintpocket.app.ui.exif

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osintpocket.app.core.network.safeCall
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.CleanResult
import com.osintpocket.app.data.ExifRepository
import com.osintpocket.app.data.ImageAnalysis
import com.osintpocket.app.data.LoadedImage
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

data class ExifUiState(
    val analysis: SectionState<ImageAnalysis> = SectionState.Idle,
    val clean: SectionState<CleanResult> = SectionState.Idle,
    val message: String? = null,
)

@HiltViewModel
class ExifViewModel @Inject constructor(
    private val repo: ExifRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExifUiState())
    val state: StateFlow<ExifUiState> = _state.asStateFlow()

    private var loaded: LoadedImage? = null
    private var job: Job? = null

    fun open(uri: Uri) {
        job?.cancel()
        loaded = null
        _state.update { ExifUiState(analysis = SectionState.Loading) }
        job = viewModelScope.launch {
            val r = safeCall { repo.load(uri) }
            loaded = r.getOrNull()
            _state.update {
                it.copy(analysis = r.fold({ img -> SectionState.Success(img.analysis) }, { e -> SectionState.Error(e.message ?: "No se pudo leer la imagen") }))
            }
            // Solo títulos de riesgo y bloques: nunca coordenadas ni valores concretos.
            r.getOrNull()?.analysis?.let { a ->
                history.record(
                    HistoryModule.EXIF, a.name,
                    if (a.flags.isEmpty()) "Sin metadatos sensibles" else a.flags.joinToString(", ") { it.title },
                    buildString {
                        appendLine("${a.mime} · ${a.width}x${a.height} · ${a.sizeBytes} bytes")
                        a.flags.forEach { appendLine("[${it.risk}] ${it.title}") }
                        if (a.blocks.isNotEmpty()) appendLine("Bloques: ${a.blocks.joinToString(", ")}")
                    }.trim(),
                )
            }
        }
    }

    fun clean() {
        val img = loaded ?: return
        _state.update { it.copy(clean = SectionState.Loading) }
        viewModelScope.launch {
            val r = safeCall { repo.clean(img) }
            _state.update {
                it.copy(clean = r.fold({ c -> SectionState.Success(c) }, { e -> SectionState.Error(e.message ?: "No se pudo limpiar la imagen") }))
            }
        }
    }

    fun save(target: Uri) {
        val c = (_state.value.clean as? SectionState.Success)?.data ?: return
        viewModelScope.launch {
            val r = safeCall { repo.saveTo(c.file, target) }
            _state.update { it.copy(message = r.fold({ "Copia limpia guardada" }, { e -> "Error al guardar: ${e.message}" })) }
        }
    }

    fun shareUri(): Pair<Uri, String>? {
        val c = (_state.value.clean as? SectionState.Success)?.data ?: return null
        return runCatching { repo.shareUri(c.file) to c.mime }.getOrNull()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
