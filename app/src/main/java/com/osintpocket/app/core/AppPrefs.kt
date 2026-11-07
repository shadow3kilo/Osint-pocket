package com.osintpocket.app.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Preferencias no sensibles. La API key de HIBP va cifrada en SecretStore. */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("osint_prefs", Context.MODE_PRIVATE)

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER, value).apply()

    var historyEnabled: Boolean
        get() = prefs.getBoolean(KEY_HISTORY, true)
        set(value) = prefs.edit().putBoolean(KEY_HISTORY, value).apply()

    private val _torEnabled = MutableStateFlow(prefs.getBoolean(KEY_TOR, false))

    /** Observable para la UI (indicador "Tor" en la barra superior). */
    val torEnabledFlow: StateFlow<Boolean> = _torEnabled.asStateFlow()

    /** Se lee en cada conexión: el cambio aplica al instante a todas las peticiones nuevas. */
    var torEnabled: Boolean
        get() = _torEnabled.value
        set(value) {
            prefs.edit().putBoolean(KEY_TOR, value).commit()
            _torEnabled.value = value
        }

    /** Puerto SOCKS de Orbot (9050 por defecto). */
    var torSocksPort: Int
        get() = prefs.getInt(KEY_TOR_PORT, 9050)
        set(value) = prefs.edit().putInt(KEY_TOR_PORT, value).apply()

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_accepted_v1"
        const val KEY_HISTORY = "history_enabled"
        const val KEY_TOR = "tor_enabled"
        const val KEY_TOR_PORT = "tor_socks_port"
    }
}
