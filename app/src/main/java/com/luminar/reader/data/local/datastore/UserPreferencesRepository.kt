// app/src/main/java/com/luminar/reader/data/local/datastore/UserPreferencesRepository.kt
package com.luminar.reader.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.data.model.FontScale
import com.luminar.reader.data.model.ScrollMode
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
    val fontScale: FontScale = FontScale.NORMAL,
    val defaultScrollMode: ScrollMode = ScrollMode.VERTICAL_SCROLL,
    val ollamaBaseUrl: String = DEFAULT_OLLAMA_BASE_URL,
    val ollamaModel: String = DEFAULT_OLLAMA_MODEL,
    val hasSeenOnboarding: Boolean = false,
    // Reading stats
    val totalReadingTimeMinutes: Long = 0,
    val totalBooksOpened: Int = 0,
    val currentStreak: Int = 0,
    val lastReadDate: String = "" // yyyy-MM-dd
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
                fontScale = preferences[PreferenceKeys.FONT_SCALE].toFontScale(),
                defaultScrollMode = preferences[PreferenceKeys.DEFAULT_SCROLL_MODE].toScrollMode(),
                ollamaBaseUrl = preferences[PreferenceKeys.OLLAMA_BASE_URL] ?: DEFAULT_OLLAMA_BASE_URL,
                ollamaModel = preferences[PreferenceKeys.OLLAMA_MODEL] ?: DEFAULT_OLLAMA_MODEL,
                hasSeenOnboarding = preferences[PreferenceKeys.HAS_SEEN_ONBOARDING] ?: false,
                totalReadingTimeMinutes = preferences[PreferenceKeys.TOTAL_READING_TIME] ?: 0L,
                totalBooksOpened = preferences[PreferenceKeys.TOTAL_BOOKS_OPENED] ?: 0,
                currentStreak = preferences[PreferenceKeys.CURRENT_STREAK] ?: 0,
                lastReadDate = preferences[PreferenceKeys.LAST_READ_DATE] ?: ""
            )
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

    suspend fun setFontScale(scale: FontScale) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.FONT_SCALE] = scale.name
        }
    }

    suspend fun setDefaultScrollMode(mode: ScrollMode) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_SCROLL_MODE] = mode.name
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

    suspend fun recordReadingSession(durationMinutes: Long) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

        context.userPreferencesDataStore.edit { preferences ->
            val prevTime = preferences[PreferenceKeys.TOTAL_READING_TIME] ?: 0L
            preferences[PreferenceKeys.TOTAL_READING_TIME] = prevTime + durationMinutes

            val lastDate = preferences[PreferenceKeys.LAST_READ_DATE] ?: ""
            if (lastDate != today) {
                // New day — check if streak continues
                val prevStreak = preferences[PreferenceKeys.CURRENT_STREAK] ?: 0
                val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date(System.currentTimeMillis() - 86400000))

                preferences[PreferenceKeys.CURRENT_STREAK] = if (lastDate == yesterday) {
                    prevStreak + 1
                } else if (lastDate.isEmpty()) {
                    1
                } else {
                    1 // streak broken
                }
                preferences[PreferenceKeys.LAST_READ_DATE] = today
            }
        }
    }

    suspend fun incrementBooksOpened() {
        context.userPreferencesDataStore.edit { preferences ->
            val prev = preferences[PreferenceKeys.TOTAL_BOOKS_OPENED] ?: 0
            preferences[PreferenceKeys.TOTAL_BOOKS_OPENED] = prev + 1
        }
    }

    private fun String?.toAppTheme(): AppTheme {
        return this
            ?.let { value -> runCatching { AppTheme.valueOf(value) }.getOrNull() }
            ?: AppTheme.DARK_AMOLED
    }

    private fun String?.toFontScale(): FontScale {
        return this
            ?.let { value -> runCatching { FontScale.valueOf(value) }.getOrNull() }
            ?: FontScale.NORMAL
    }

    private fun String?.toScrollMode(): ScrollMode {
        return this
            ?.let { value -> runCatching { ScrollMode.valueOf(value) }.getOrNull() }
            ?: ScrollMode.VERTICAL_SCROLL
    }

    private object PreferenceKeys {
        val SELECTED_THEME = stringPreferencesKey("selected_theme")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VOLUME_BUTTONS_PAGE_TURN = booleanPreferencesKey("volume_buttons_page_turn")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val DEFAULT_SCROLL_MODE = stringPreferencesKey("default_scroll_mode")
        val OLLAMA_BASE_URL = stringPreferencesKey("ollama_base_url")
        val OLLAMA_MODEL = stringPreferencesKey("ollama_model")
        val TOTAL_READING_TIME = longPreferencesKey("total_reading_time_minutes")
        val TOTAL_BOOKS_OPENED = intPreferencesKey("total_books_opened")
        val CURRENT_STREAK = intPreferencesKey("current_streak")
        val LAST_READ_DATE = stringPreferencesKey("last_read_date")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }

    suspend fun setOnboardingComplete() {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.HAS_SEEN_ONBOARDING] = true
        }
    }
}
