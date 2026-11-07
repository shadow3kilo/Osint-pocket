package com.osintpocket.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/** Campo de búsqueda común: teclado URI, acción Buscar y botón que alterna Buscar/Cancelar. */
@Composable
fun QueryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String?,
    busy: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Solo objetivos propios o con autorización",
    keyboardType: KeyboardType = KeyboardType.Uri,
) {
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        onSubmit()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = error != null,
        supportingText = { Text(error ?: hint) },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Search,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        trailingIcon = {
            if (busy) {
                IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, "Cancelar") }
            } else {
                IconButton(onClick = submit) { Icon(Icons.Outlined.Search, "Buscar") }
            }
        },
    )
}
