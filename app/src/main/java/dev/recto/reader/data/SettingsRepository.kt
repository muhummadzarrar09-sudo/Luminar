package dev.recto.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
        val warmth = floatPreferencesKey("warmth")
        val dim = floatPreferencesKey("dim")
        val useReaderBrightness = booleanPreferencesKey("use_reader_brightness")
        val brightness = floatPreferencesKey("brightness")
        val dailyGoal = intPreferencesKey("daily_goal_minutes")
        val reminders = booleanPreferencesKey("reminders_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val streakAlerts = booleanPreferencesKey("streak_alerts")
        val defaultHighlight = intPreferencesKey("default_highlight_colour")
        val ttsSpeed = floatPreferencesKey("tts_speed")
        val ttsPitch = floatPreferencesKey("tts_pitch")
        val ttsVoice = stringPreferencesKey("tts_voice")
        val ttsSleep = intPreferencesKey("tts_sleep_minutes")
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
                volumeKeysTurnPages = prefs[Keys.volumeKeys] ?: true,
                warmth = (prefs[Keys.warmth] ?: 0f).coerceIn(0f, 1f),
                dim = (prefs[Keys.dim] ?: 0f).coerceIn(0f, 1f),
                useReaderBrightness = prefs[Keys.useReaderBrightness] ?: false,
                brightness = (prefs[Keys.brightness] ?: 0.5f).coerceIn(0f, 1f),
                dailyGoalMinutes = (prefs[Keys.dailyGoal] ?: 0).coerceIn(0, 240),
                remindersEnabled = prefs[Keys.reminders] ?: false,
                reminderHour = (prefs[Keys.reminderHour] ?: 20).coerceIn(0, 23),
                reminderMinute = (prefs[Keys.reminderMinute] ?: 0).coerceIn(0, 59),
                streakAlertsEnabled = prefs[Keys.streakAlerts] ?: true,
                // Clamped: a colour was removed once already and an
                // out-of-range index would crash fromIndex' callers.
                defaultHighlightColour = (prefs[Keys.defaultHighlight] ?: 0)
                    .coerceIn(0, HighlightColour.entries.size - 1),
                ttsSpeed = (prefs[Keys.ttsSpeed] ?: 1.0f)
                    .coerceIn(ReaderSettings.MIN_TTS_SPEED, ReaderSettings.MAX_TTS_SPEED),
                ttsPitch = (prefs[Keys.ttsPitch] ?: 1.0f)
                    .coerceIn(ReaderSettings.MIN_TTS_PITCH, ReaderSettings.MAX_TTS_PITCH),
                ttsVoiceId = prefs[Keys.ttsVoice],
                ttsSleepMinutes = (prefs[Keys.ttsSleep] ?: 0).coerceIn(0, 120)
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

    suspend fun setWarmth(value: Float) = edit { it[Keys.warmth] = value.coerceIn(0f, 1f) }

    suspend fun setDim(value: Float) = edit { it[Keys.dim] = value.coerceIn(0f, 1f) }

    suspend fun setUseReaderBrightness(value: Boolean) =
        edit { it[Keys.useReaderBrightness] = value }

    suspend fun setBrightness(value: Float) =
        edit { it[Keys.brightness] = value.coerceIn(0f, 1f) }

    suspend fun setDailyGoal(minutes: Int) =
        edit { it[Keys.dailyGoal] = minutes.coerceIn(0, 240) }

    suspend fun setRemindersEnabled(value: Boolean) =
        edit { it[Keys.reminders] = value }

    suspend fun setReminderTime(hour: Int, minute: Int) = edit {
        it[Keys.reminderHour] = hour.coerceIn(0, 23)
        it[Keys.reminderMinute] = minute.coerceIn(0, 59)
    }

    suspend fun setStreakAlerts(value: Boolean) = edit { it[Keys.streakAlerts] = value }

    suspend fun setDefaultHighlightColour(index: Int) = edit {
        it[Keys.defaultHighlight] = index.coerceIn(0, HighlightColour.entries.size - 1)
    }

    suspend fun setTtsSpeed(value: Float) = edit {
        it[Keys.ttsSpeed] =
            value.coerceIn(ReaderSettings.MIN_TTS_SPEED, ReaderSettings.MAX_TTS_SPEED)
    }

    suspend fun setTtsPitch(value: Float) = edit {
        it[Keys.ttsPitch] =
            value.coerceIn(ReaderSettings.MIN_TTS_PITCH, ReaderSettings.MAX_TTS_PITCH)
    }

    suspend fun setTtsVoice(id: String?) = edit {
        if (id == null) it.remove(Keys.ttsVoice) else it[Keys.ttsVoice] = id
    }

    suspend fun setTtsSleepMinutes(minutes: Int) = edit {
        it[Keys.ttsSleep] = minutes.coerceIn(0, 120)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }
}
