package com.gabriel.tvmando.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gabriel.tvmando.domain.Scene
import com.gabriel.tvmando.domain.SceneCodec
import com.gabriel.tvmando.domain.SceneLibrary
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

    // --- escenas -----------------------------------------------------------

    /**
     * Escenas guardadas. La primera vez no hay nada persistido y se sirven las de
     * fabrica; en cuanto el usuario toca algo se guarda su lista entera, incluidas
     * las de fabrica que haya modificado o borrado.
     */
    val scenes: Flow<List<Scene>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_SCENES]
        if (raw == null) SceneLibrary.defaults() else SceneCodec.decode(raw)
    }

    suspend fun saveScenes(scenes: List<Scene>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCENES] = SceneCodec.encode(scenes)
        }
    }

    /** Vuelve a las tres escenas de la especificacion. */
    suspend fun restoreDefaultScenes() {
        context.dataStore.edit { prefs -> prefs.remove(KEY_SCENES) }
    }

    // --- historial de busquedas --------------------------------------------

    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SEARCH_HISTORY]
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    /** Guarda la busqueda arriba del todo, sin repetidas y con tope. */
    suspend fun rememberSearch(query: String) {
        val clean = query.trim().replace('\n', ' ')
        if (clean.isEmpty()) return
        context.dataStore.edit { prefs ->
            val previous = prefs[KEY_SEARCH_HISTORY]?.split('\n').orEmpty()
            val updated = (listOf(clean) + previous)
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_HISTORY)
            prefs[KEY_SEARCH_HISTORY] = updated.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs -> prefs.remove(KEY_SEARCH_HISTORY) }
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
        const val MAX_HISTORY = 12

        val KEY_HOST = stringPreferencesKey("tv_host")
        val KEY_SCENES = stringPreferencesKey("scenes")
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
        val KEY_PORT = intPreferencesKey("tv_port")
        val KEY_MODEL = stringPreferencesKey("tv_model")
        val KEY_ADB_PRIVATE = stringPreferencesKey("adb_private_key")
        val KEY_ADB_PUBLIC = stringPreferencesKey("adb_public_key")
    }
}
