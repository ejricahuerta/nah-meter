package dev.edmel.offerchecker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.edmel.offerchecker.core.Thresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

data class AppSettings(
    val thresholds: Thresholds = Thresholds(),
    /** The Status screen's switch: when off, no offers are read and no badge shows. */
    val readingEnabled: Boolean = true,
    val vibration: Boolean = true,
    /** Say the verdict out loud ("nah. 14 an hour"), for couriers who are driving. */
    val voice: Boolean = true,
    /** Compact pill at the top of the screen instead of the card docked above the offer. */
    val compactBadge: Boolean = false,
    /** The welcome screen has been seen. */
    val onboarded: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val minPay = stringPreferencesKey("min_pay")
        val minHourly = stringPreferencesKey("min_hourly")
        val minPerKm = stringPreferencesKey("min_per_km")
        val waitBuffer = intPreferencesKey("wait_buffer_min")
        val readingEnabled = booleanPreferencesKey("reading_enabled")
        val vibration = booleanPreferencesKey("vibration")
        val voice = booleanPreferencesKey("voice")
        val compactBadge = booleanPreferencesKey("compact_badge")
        val onboarded = booleanPreferencesKey("onboarded")
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
            ),
            readingEnabled = this[Keys.readingEnabled] ?: d.readingEnabled,
            vibration = this[Keys.vibration] ?: d.vibration,
            voice = this[Keys.voice] ?: d.voice,
            compactBadge = this[Keys.compactBadge] ?: d.compactBadge,
            onboarded = this[Keys.onboarded] ?: d.onboarded,
        )
    }

    suspend fun setMinPay(v: BigDecimal) = store.edit { it[Keys.minPay] = v.toPlainString() }
    suspend fun setMinHourly(v: BigDecimal) = store.edit { it[Keys.minHourly] = v.toPlainString() }
    suspend fun setMinPerKm(v: BigDecimal) = store.edit { it[Keys.minPerKm] = v.toPlainString() }
    suspend fun setWaitBuffer(v: Int) = store.edit { it[Keys.waitBuffer] = v }
    suspend fun setReadingEnabled(v: Boolean) = store.edit { it[Keys.readingEnabled] = v }
    suspend fun setVibration(v: Boolean) = store.edit { it[Keys.vibration] = v }
    suspend fun setVoice(v: Boolean) = store.edit { it[Keys.voice] = v }
    suspend fun setCompactBadge(v: Boolean) = store.edit { it[Keys.compactBadge] = v }
    suspend fun setOnboarded() = store.edit { it[Keys.onboarded] = true }
}
