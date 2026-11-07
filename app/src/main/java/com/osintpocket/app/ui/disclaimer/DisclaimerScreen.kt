package com.osintpocket.app.ui.disclaimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val POINTS = listOf(
    "OSINT Pocket hace reconocimiento pasivo: consulta fuentes públicas (RDAP, DNS, logs de certificados, " +
        "InternetDB, ip-api, HIBP). No escanea ni ataca ningún sistema.",
    "Úsala solo sobre dominios, IPs, cuentas e imágenes propias o para las que tengas autorización expresa.",
    "Perfilar o rastrear a personas sin su consentimiento puede vulnerar el RGPD y otras leyes. " +
        "Tú eres responsable del uso que hagas de la app.",
    "Las consultas salen desde tu conexión hacia servicios de terceros, cada uno con sus propios términos y límites.",
)

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.Gavel, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Uso legal y ético", style = MaterialTheme.typography.headlineSmall)
            POINTS.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { checked = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Text("Entiendo y acepto estas condiciones", Modifier.padding(start = 8.dp))
            }
            Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) {
                Text("Continuar")
            }
        }
    }
}
