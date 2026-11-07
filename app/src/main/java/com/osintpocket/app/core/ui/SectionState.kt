package com.osintpocket.app.core.ui

/** Estado independiente por sección: un servicio caído no tumba el resto del informe. */
sealed interface SectionState<out T> {
    data object Idle : SectionState<Nothing>
    data object Loading : SectionState<Nothing>
    data class Success<T>(val data: T) : SectionState<T>
    data class Error(val message: String) : SectionState<Nothing>
}

data class KeyValue(val key: String, val value: String)
