package dev.edmel.offerchecker.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.edmel.offerchecker.core.Verdict

/** Vibration per colour: green 1 short, yellow 2 short, red 1 long. Grey: none. */
class Alerts(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    fun vibrate(verdict: Verdict) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val timings = when (verdict) {
            Verdict.GREEN -> longArrayOf(0, 80)
            Verdict.YELLOW -> longArrayOf(0, 80, 120, 80)
            Verdict.RED -> longArrayOf(0, 500)
            Verdict.GREY -> return
        }
        v.vibrate(VibrationEffect.createWaveform(timings, -1))
    }
}
