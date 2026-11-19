package app.sauti.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.sauti.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How the app may use the network. Offline-first: the default never sends anything silently. */
enum class NetworkMode { OFFLINE_ONLY, ASK, AUTO_WIFI }

data class Settings(
    val mode: NetworkMode = NetworkMode.ASK,
    val serverUrl: String = BuildConfig.DEFAULT_SERVER_URL,
    val modelPath: String? = null,
    val loraPath: String? = null,
    /** Voice mode: speak to Sauti and hear the reply. Text mode: type and read. */
    val voiceMode: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "sauti_settings")

class SettingsRepository(private val context: Context) {
    private val kMode = stringPreferencesKey("mode")
    private val kServer = stringPreferencesKey("server_url")
    private val kModel = stringPreferencesKey("model_path")
    private val kLora = stringPreferencesKey("lora_path")
    private val kVoice = booleanPreferencesKey("voice_mode")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            mode = p[kMode]?.let { runCatching { NetworkMode.valueOf(it) }.getOrNull() } ?: NetworkMode.ASK,
            serverUrl = p[kServer] ?: BuildConfig.DEFAULT_SERVER_URL,
            modelPath = p[kModel],
            loraPath = p[kLora],
            voiceMode = p[kVoice] ?: true,
        )
    }

    suspend fun setMode(m: NetworkMode) = context.dataStore.edit { it[kMode] = m.name }
    suspend fun setServerUrl(u: String) = context.dataStore.edit { it[kServer] = u.trim() }
    suspend fun setModelPath(p: String) = context.dataStore.edit { it[kModel] = p }
    suspend fun setVoiceMode(on: Boolean) = context.dataStore.edit { it[kVoice] = on }
    suspend fun setLoraPath(p: String?) = context.dataStore.edit {
        if (p == null) it.remove(kLora) else it[kLora] = p
    }
}
