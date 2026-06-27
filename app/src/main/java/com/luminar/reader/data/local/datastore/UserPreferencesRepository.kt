// app/src/main/java/com/luminar/reader/data/local/datastore/UserPreferencesRepository.kt
package com.luminar.reader.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luminar.reader.data.model.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val DEFAULT_OLLAMA_BASE_URL = "http://192.168.1.1:11434"
private const val DEFAULT_OLLAMA_MODEL = "deepseek-r1:7b"

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

data class UserPreferences(
    val selectedTheme: AppTheme = AppTheme.DARK_AMOLED,
    val keepScreenOn: Boolean = true,
    val volumeButtonsPageTurn: Boolean = true,
    val ollamaBaseUrl: String = DEFAULT_OLLAMA_BASE_URL,
    val ollamaModel: String = DEFAULT_OLLAMA_MODEL,
    val fontScale: Float = 1.0f
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val userPreferences: Flow<UserPreferences> = context.userPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                selectedTheme = preferences[PreferenceKeys.SELECTED_THEME].toAppTheme(),
                keepScreenOn = preferences[PreferenceKeys.KEEP_SCREEN_ON] ?: true,
                volumeButtonsPageTurn = preferences[PreferenceKeys.VOLUME_BUTTONS_PAGE_TURN] ?: true,
                ollamaBaseUrl = preferences[PreferenceKeys.OLLAMA_BASE_URL] ?: DEFAULT_OLLAMA_BASE_URL,
                ollamaModel = preferences[PreferenceKeys.OLLAMA_MODEL] ?: DEFAULT_OLLAMA_MODEL,
                fontScale = preferences[PreferenceKeys.FONT_SCALE] ?: 1.0f
            )
        }

    suspend fun setFontScale(scale: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.FONT_SCALE] = scale
        }
    }

    suspend fun setSelectedTheme(theme: AppTheme) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.SELECTED_THEME] = theme.name
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setVolumeButtonsPageTurn(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.VOLUME_BUTTONS_PAGE_TURN] = enabled
        }
    }

    suspend fun setOllamaBaseUrl(url: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.OLLAMA_BASE_URL] = url
        }
    }

    suspend fun setOllamaModel(model: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.OLLAMA_MODEL] = model
        }
    }

    private fun String?.toAppTheme(): AppTheme {
        return this
            ?.let { value -> runCatching { AppTheme.valueOf(value) }.getOrNull() }
            ?: AppTheme.DARK_AMOLED
    }

    private object PreferenceKeys {
        val SELECTED_THEME = stringPreferencesKey("selected_theme")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VOLUME_BUTTONS_PAGE_TURN = booleanPreferencesKey("volume_buttons_page_turn")
        val OLLAMA_BASE_URL = stringPreferencesKey("ollama_base_url")
        val OLLAMA_MODEL = stringPreferencesKey("ollama_model")
        val FONT_SCALE = floatPreferencesKey("font_scale")
    }
}
