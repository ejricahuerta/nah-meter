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

enum class BadgeSize(val scale: Float) { SMALL(0.8f), MEDIUM(1f), LARGE(1.3f) }

data class AppSettings(
    val thresholds: Thresholds = Thresholds(),
    val vibration: Boolean = true,
    val badgeSize: BadgeSize = BadgeSize.MEDIUM,
    val badgeX: Int = 24,
    val badgeY: Int = 240,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val minPay = stringPreferencesKey("min_pay")
        val minHourly = stringPreferencesKey("min_hourly")
        val minPerKm = stringPreferencesKey("min_per_km")
        val waitBuffer = intPreferencesKey("wait_buffer_min")
        val vibration = booleanPreferencesKey("vibration")
        val badgeSize = stringPreferencesKey("badge_size")
        val badgeX = intPreferencesKey("badge_x")
        val badgeY = intPreferencesKey("badge_y")
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
            vibration = this[Keys.vibration] ?: d.vibration,
            badgeSize = this[Keys.badgeSize]?.let { runCatching { BadgeSize.valueOf(it) }.getOrNull() } ?: d.badgeSize,
            badgeX = this[Keys.badgeX] ?: d.badgeX,
            badgeY = this[Keys.badgeY] ?: d.badgeY,
        )
    }

    suspend fun setMinPay(v: BigDecimal) = store.edit { it[Keys.minPay] = v.toPlainString() }
    suspend fun setMinHourly(v: BigDecimal) = store.edit { it[Keys.minHourly] = v.toPlainString() }
    suspend fun setMinPerKm(v: BigDecimal) = store.edit { it[Keys.minPerKm] = v.toPlainString() }
    suspend fun setWaitBuffer(v: Int) = store.edit { it[Keys.waitBuffer] = v }
    suspend fun setVibration(v: Boolean) = store.edit { it[Keys.vibration] = v }
    suspend fun setBadgeSize(v: BadgeSize) = store.edit { it[Keys.badgeSize] = v.name }
    suspend fun setBadgePosition(x: Int, y: Int) = store.edit {
        it[Keys.badgeX] = x
        it[Keys.badgeY] = y
    }
    suspend fun resetThresholds() = store.edit {
        it.remove(Keys.minPay); it.remove(Keys.minHourly); it.remove(Keys.minPerKm); it.remove(Keys.waitBuffer)
    }
}
