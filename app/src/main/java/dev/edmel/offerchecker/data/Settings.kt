package dev.edmel.offerchecker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.edmel.offerchecker.core.Priority
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.core.Verdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

/** Which theme to draw: Mono (dark, the default), Daylight (light), or whichever the phone is in. */
enum class Appearance { MONO, DAYLIGHT, PHONE }

/**
 * What the spoken verdict says: the word, the word and $/hr, or the word, $/hr and $/km. When $/km
 * is the strict rate ([Thresholds.priority]) it's said in place of, or before, the $/hr.
 */
enum class VoiceMode { WORD, WORD_HOURLY, WORD_HOURLY_KM }

data class AppSettings(
    val thresholds: Thresholds = Thresholds(),
    /** Home's power button (and the tile and notification): when off, no offers are read and no badge shows. */
    val readingEnabled: Boolean = true,
    val vibration: Boolean = true,
    /** Say the verdict out loud ("nah. 14 an hour"), for couriers who are driving. */
    val voice: Boolean = true,
    /** How loud the spoken verdict is, as a share of the accessibility volume range (0–100; 0 is silent). */
    val voiceVolume: Int = 80,
    val voiceMode: VoiceMode = VoiceMode.WORD_HOURLY,
    /** Which verdicts are spoken; the rest only show. */
    val voiceVerdicts: Set<Verdict> = Verdict.entries.toSet(),
    /** Speech rate, 0.5–2.0 (1.0 is normal). */
    val voiceSpeed: Float = 1f,
    /** The text-to-speech voice picked by name; null means automatic (English Canada if installed). */
    val voiceName: String? = null,
    /** Music and other audio get quieter while the verdict is spoken. */
    val voiceDuck: Boolean = true,
    /** The verdict word in the Toronto voice's recorded lines ("nah fam."); the rates stay the phone's voice. */
    val torontoVoice: Boolean = true,
    /** Compact pill at the top of the screen instead of the card docked above the offer. */
    val compactBadge: Boolean = false,
    /** The welcome screen has been seen. */
    val onboarded: Boolean = false,
    val appearance: Appearance = Appearance.MONO,
    /** The delivery apps the courier drives for ("uber", "doordash", "skip"): the tiles Home shows. */
    val apps: Set<String> = setOf("uber", "doordash", "skip"),
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val minPay = stringPreferencesKey("min_pay")
        val minHourly = stringPreferencesKey("min_hourly")
        val minPerKm = stringPreferencesKey("min_per_km")
        val maxKm = stringPreferencesKey("max_km")
        val extraKmPercent = intPreferencesKey("extra_km_pct")
        val extraKmOverKm = intPreferencesKey("extra_km_over")
        val waitBuffer = intPreferencesKey("wait_buffer_min")
        val priority = stringPreferencesKey("priority")
        val readingEnabled = booleanPreferencesKey("reading_enabled")
        val vibration = booleanPreferencesKey("vibration")
        val voice = booleanPreferencesKey("voice")
        val voiceVolume = intPreferencesKey("voice_volume")
        val voiceMode = stringPreferencesKey("voice_mode")
        val voiceVerdicts = stringSetPreferencesKey("voice_verdicts")
        val voiceSpeed = floatPreferencesKey("voice_speed")
        val voiceName = stringPreferencesKey("voice_name")
        val voiceDuck = booleanPreferencesKey("voice_duck")
        val torontoVoice = booleanPreferencesKey("toronto_voice")
        val compactBadge = booleanPreferencesKey("compact_badge")
        val onboarded = booleanPreferencesKey("onboarded")
        val appearance = stringPreferencesKey("appearance")
        val apps = stringSetPreferencesKey("apps")
    }

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        fun dec(key: Preferences.Key<String>, default: BigDecimal) =
            this[key]?.toBigDecimalOrNull() ?: default
        return AppSettings(
            thresholds = Thresholds(
                minPay = dec(Keys.minPay, d.thresholds.minPay),
                minHourly = dec(Keys.minHourly, d.thresholds.minHourly),
                minPerKm = dec(Keys.minPerKm, d.thresholds.minPerKm),
                waitBufferMin = this[Keys.waitBuffer] ?: d.thresholds.waitBufferMin,
                maxKm = this[Keys.maxKm]?.toBigDecimalOrNull(),
                extraKmPercent = (this[Keys.extraKmPercent] ?: d.thresholds.extraKmPercent).coerceIn(0, 100),
                extraKmOverKm = (this[Keys.extraKmOverKm] ?: d.thresholds.extraKmOverKm).coerceIn(0, 50),
                priority = priority(),
            ),
            readingEnabled = this[Keys.readingEnabled] ?: d.readingEnabled,
            vibration = this[Keys.vibration] ?: d.vibration,
            voice = this[Keys.voice] ?: d.voice,
            voiceVolume = this[Keys.voiceVolume] ?: d.voiceVolume,
            voiceMode = this[Keys.voiceMode]?.let { v -> VoiceMode.entries.firstOrNull { it.name == v } } ?: d.voiceMode,
            voiceVerdicts = this[Keys.voiceVerdicts]?.let { names -> Verdict.entries.filter { it.name in names }.toSet() } ?: d.voiceVerdicts,
            voiceSpeed = (this[Keys.voiceSpeed] ?: d.voiceSpeed).coerceIn(0.5f, 2f),
            voiceName = this[Keys.voiceName]?.takeIf { it.isNotEmpty() },
            voiceDuck = this[Keys.voiceDuck] ?: d.voiceDuck,
            torontoVoice = this[Keys.torontoVoice] ?: d.torontoVoice,
            compactBadge = this[Keys.compactBadge] ?: d.compactBadge,
            onboarded = this[Keys.onboarded] ?: d.onboarded,
            appearance = this[Keys.appearance]?.let { v -> Appearance.entries.firstOrNull { it.name == v } } ?: d.appearance,
            apps = this[Keys.apps] ?: d.apps,
        )
    }

    private fun Preferences.priority() =
        this[Keys.priority]?.let { v -> Priority.entries.firstOrNull { it.name == v } } ?: Thresholds().priority

    suspend fun setMinPay(v: BigDecimal) = store.edit { it[Keys.minPay] = v.toPlainString() }
    suspend fun setMinHourly(v: BigDecimal) = store.edit { it[Keys.minHourly] = v.toPlainString() }
    suspend fun setMinPerKm(v: BigDecimal) = store.edit { it[Keys.minPerKm] = v.toPlainString() }
    /** null turns the limit off. */
    suspend fun setMaxKm(v: BigDecimal?) = store.edit { prefs -> if (v == null) prefs.remove(Keys.maxKm) else prefs[Keys.maxKm] = v.toPlainString() }
    suspend fun setExtraKmPercent(v: Int) = store.edit { it[Keys.extraKmPercent] = v.coerceIn(0, 100) }
    suspend fun setExtraKmOverKm(v: Int) = store.edit { it[Keys.extraKmOverKm] = v.coerceIn(0, 50) }
    suspend fun setWaitBuffer(v: Int) = store.edit { it[Keys.waitBuffer] = v }
    suspend fun setPriority(v: Priority) = store.edit { it[Keys.priority] = v.name }
    /** Flips the strict rate in one edit (two quick taps flip it twice, not once) and returns the new one. */
    suspend fun togglePriority(): Priority = store.edit { prefs ->
        prefs[Keys.priority] = (if (prefs.priority() == Priority.PER_KM) Priority.HOURLY else Priority.PER_KM).name
    }.priority()
    suspend fun setReadingEnabled(v: Boolean) = store.edit { it[Keys.readingEnabled] = v }
    suspend fun setVibration(v: Boolean) = store.edit { it[Keys.vibration] = v }
    suspend fun setVoice(v: Boolean) = store.edit { it[Keys.voice] = v }
    suspend fun setVoiceVolume(v: Int) = store.edit { it[Keys.voiceVolume] = v.coerceIn(0, 100) }
    suspend fun setVoiceMode(v: VoiceMode) = store.edit { it[Keys.voiceMode] = v.name }
    suspend fun setVoiceVerdicts(v: Set<Verdict>) = store.edit { prefs -> prefs[Keys.voiceVerdicts] = v.map { it.name }.toSet() }
    suspend fun setVoiceSpeed(v: Float) = store.edit { it[Keys.voiceSpeed] = v.coerceIn(0.5f, 2f) }
    suspend fun setVoiceName(v: String?) = store.edit { prefs -> if (v == null) prefs.remove(Keys.voiceName) else prefs[Keys.voiceName] = v }
    suspend fun setVoiceDuck(v: Boolean) = store.edit { it[Keys.voiceDuck] = v }
    suspend fun setTorontoVoice(v: Boolean) = store.edit { it[Keys.torontoVoice] = v }
    suspend fun setCompactBadge(v: Boolean) = store.edit { it[Keys.compactBadge] = v }
    suspend fun setOnboarded() = store.edit { it[Keys.onboarded] = true }
    suspend fun setAppearance(v: Appearance) = store.edit { it[Keys.appearance] = v.name }
    suspend fun setApps(v: Set<String>) = store.edit { it[Keys.apps] = v }
}
