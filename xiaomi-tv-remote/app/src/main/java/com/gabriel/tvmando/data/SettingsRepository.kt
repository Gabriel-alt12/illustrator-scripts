package com.gabriel.tvmando.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Endpoint ADB de la TV mas lo ultimo que sabemos de ella. */
data class TvSettings(
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val lastKnownModel: String? = null,
) {
    val isConfigured: Boolean get() = host.isNotBlank()
    val endpoint: String get() = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 5555
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tvmando")

/**
 * Persistencia de la app. Guarda dos cosas distintas:
 *
 *  - La configuracion visible (IP, puerto, ultimo modelo detectado).
 *  - El par de claves ADB, pero SOLO como respaldo: en condiciones normales la clave
 *    privada vive en el AndroidKeyStore y aqui no se guarda nada. Ver [AdbKeyProvider].
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<TvSettings> = context.dataStore.data.map { prefs ->
        TvSettings(
            host = prefs[KEY_HOST].orEmpty(),
            port = prefs[KEY_PORT] ?: TvSettings.DEFAULT_PORT,
            lastKnownModel = prefs[KEY_MODEL],
        )
    }

    suspend fun current(): TvSettings = settings.first()

    suspend fun setEndpoint(host: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = host.trim()
            prefs[KEY_PORT] = port
        }
    }

    suspend fun setLastKnownModel(model: String?) {
        context.dataStore.edit { prefs ->
            if (model.isNullOrBlank()) prefs.remove(KEY_MODEL) else prefs[KEY_MODEL] = model
        }
    }

    // --- respaldo del par de claves ADB ------------------------------------

    suspend fun storedKeyPair(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val private = prefs[KEY_ADB_PRIVATE] ?: return null
        val public = prefs[KEY_ADB_PUBLIC] ?: return null
        return private to public
    }

    suspend fun storeKeyPair(privateBase64: String, publicBase64: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ADB_PRIVATE] = privateBase64
            prefs[KEY_ADB_PUBLIC] = publicBase64
        }
    }

    suspend fun clearKeyPair() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ADB_PRIVATE)
            prefs.remove(KEY_ADB_PUBLIC)
        }
    }

    private companion object {
        val KEY_HOST = stringPreferencesKey("tv_host")
        val KEY_PORT = intPreferencesKey("tv_port")
        val KEY_MODEL = stringPreferencesKey("tv_model")
        val KEY_ADB_PRIVATE = stringPreferencesKey("adb_private_key")
        val KEY_ADB_PUBLIC = stringPreferencesKey("adb_public_key")
    }
}
