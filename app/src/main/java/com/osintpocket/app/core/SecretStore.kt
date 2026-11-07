package com.osintpocket.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Secretos (API keys) cifrados con una clave del Android Keystore. */
@Singleton
class SecretStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences? by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
    }

    /** false si el Keystore del dispositivo falla: en ese caso no se guarda nada (nunca en claro). */
    val isAvailable: Boolean get() = prefs != null

    var hibpKey: String?
        get() = prefs?.getString(KEY_HIBP, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs?.edit()?.apply {
                if (value.isNullOrBlank()) remove(KEY_HIBP) else putString(KEY_HIBP, value.trim())
            }?.apply()
        }

    private companion object {
        const val FILE = "osint_secrets"
        const val KEY_HIBP = "hibp_api_key"
    }
}
