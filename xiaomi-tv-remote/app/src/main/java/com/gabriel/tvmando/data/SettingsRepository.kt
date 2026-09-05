package com.gabriel.tvmando.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    /** Mando siempre visible en la barra de notificaciones. */
    val persistentRemote: Boolean = false,
    /** Oscuro, claro o el que tenga el movil. */
    val theme: ThemeMode = ThemeMode.DARK,
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
            persistentRemote = prefs[KEY_PERSISTENT_REMOTE] ?: false,
            theme = ThemeMode.from(prefs[KEY_THEME]),
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

    suspend fun setPersistentRemote(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_PERSISTENT_REMOTE] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME] = mode.name }
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

    // --- favoritos ----------------------------------------------------------

    /**
     * Apps fijadas arriba en la rejilla.
     *
     * Con la tele entera a la vista son cuarenta y pico fichas, y las cuatro de
     * siempre se pierden entre ellas. Se guardan los paquetes y no un indice porque
     * la lista cambia sola cada vez que se instala algo en la TV.
     */
    val favorites: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_FAVORITES]?.split('\n')?.filter { it.isNotBlank() }?.toSet().orEmpty()
    }

    suspend fun toggleFavorite(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            val updated = if (packageName in current) current - packageName else current + packageName
            prefs[KEY_FAVORITES] = updated.joinToString("\n")
        }
    }

    // --- modo de escritura por destino --------------------------------------

    /**
     * Destinos donde se manda el texto de golpe en vez de tecla a tecla.
     *
     * Lo normal es lo segundo, que es lo unico que entienden los buscadores caseros
     * de las apps de TV, asi que aqui solo se apuntan las excepciones: si Netflix
     * acepta el texto entero y Prime Video no, cada uno se recuerda como es y no hay
     * que cambiarlo a mano cada vez.
     */
    val fastTypingTargets: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_FAST_TYPING]?.split('\n')?.filter { it.isNotBlank() }?.toSet().orEmpty()
    }

    suspend fun setFastTyping(targetKey: String, fast: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FAST_TYPING]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            val updated = if (fast) current + targetKey else current - targetKey
            prefs[KEY_FAST_TYPING] = updated.distinct().joinToString("\n")
        }
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
        val KEY_FAVORITES = stringPreferencesKey("favorite_apps")
        val KEY_FAST_TYPING = stringPreferencesKey("fast_typing_targets")
        val KEY_PORT = intPreferencesKey("tv_port")
        val KEY_MODEL = stringPreferencesKey("tv_model")
        val KEY_PERSISTENT_REMOTE = booleanPreferencesKey("persistent_remote")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_ADB_PRIVATE = stringPreferencesKey("adb_private_key")
        val KEY_ADB_PUBLIC = stringPreferencesKey("adb_public_key")
    }
}
