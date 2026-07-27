package dev.recto.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("reader_settings")

/**
 * Reading preferences, persisted with DataStore.
 *
 * These are app-wide rather than per-book on purpose: a reader who likes
 * 20sp sepia wants 20sp sepia in every book, and Kindle behaves the same way.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val font = stringPreferencesKey("font")
        val fontSize = intPreferencesKey("font_size")
        val lineSpacing = stringPreferencesKey("line_spacing")
        val margin = stringPreferencesKey("margin")
        val justify = booleanPreferencesKey("justify")
        val followSystemDark = booleanPreferencesKey("follow_system_dark")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val volumeKeys = booleanPreferencesKey("volume_keys")
    }

    val settings: Flow<ReaderSettings> = context.settingsStore.data
        // A corrupt or unreadable preferences file must not stop the user
        // reading; fall back to defaults instead.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            ReaderSettings(
                theme = ReaderTheme.fromName(prefs[Keys.theme]),
                font = ReaderFont.entries.firstOrNull { it.name == prefs[Keys.font] }
                    ?: ReaderFont.SERIF,
                fontSizeSp = (prefs[Keys.fontSize] ?: 18)
                    .coerceIn(ReaderSettings.MIN_FONT_SP, ReaderSettings.MAX_FONT_SP),
                lineSpacing = LineSpacing.fromName(prefs[Keys.lineSpacing]),
                margin = PageMargin.fromName(prefs[Keys.margin]),
                justify = prefs[Keys.justify] ?: true,
                followSystemDark = prefs[Keys.followSystemDark] ?: false,
                keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
                volumeKeysTurnPages = prefs[Keys.volumeKeys] ?: true
            )
        }

    suspend fun setTheme(theme: ReaderTheme) = edit { it[Keys.theme] = theme.name }

    suspend fun setFont(font: ReaderFont) = edit { it[Keys.font] = font.name }

    suspend fun setFontSize(sp: Int) = edit {
        it[Keys.fontSize] = sp.coerceIn(ReaderSettings.MIN_FONT_SP, ReaderSettings.MAX_FONT_SP)
    }

    suspend fun setLineSpacing(value: LineSpacing) = edit { it[Keys.lineSpacing] = value.name }

    suspend fun setMargin(value: PageMargin) = edit { it[Keys.margin] = value.name }

    suspend fun setJustify(value: Boolean) = edit { it[Keys.justify] = value }

    suspend fun setFollowSystemDark(value: Boolean) = edit { it[Keys.followSystemDark] = value }

    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepScreenOn] = value }

    suspend fun setVolumeKeys(value: Boolean) = edit { it[Keys.volumeKeys] = value }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }
}
