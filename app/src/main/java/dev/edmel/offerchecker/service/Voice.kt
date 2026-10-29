package dev.edmel.offerchecker.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Says the verdict out loud ("nah. 14 an hour") so a courier who is driving doesn't have to look.
 * Uses the phone's own text-to-speech engine (on the device, no network).
 *
 * It plays on the media volume, not the navigation one: Uber raises and lowers navigation volume
 * around its own prompts, which made the verdict too quiet or too loud. Just for the verdict, media
 * volume is set to the user's chosen level, then put back; music ducks under it meanwhile. If the
 * engine can't start, it stays silent and says so once in the diagnostics log.
 */
class Voice(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    @Volatile private var ready = false
    private var failed = false
    private lateinit var tts: TextToSpeech

    /** Media volume before the verdict raised or lowered it, to put back after; null when untouched. */
    @Volatile private var savedVolume: Int? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching {
                    tts.setAudioAttributes(attributes)
                    if (tts.isLanguageAvailable(Locale.getDefault()) >= TextToSpeech.LANG_AVAILABLE) tts.language = Locale.getDefault()
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) { release() }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) { release() }
                    })
                    ready = true
                }.onFailure { fail("${it.javaClass.simpleName}: ${it.message}") }
            } else {
                fail("text-to-speech engine didn't start (status $status)")
            }
        }
    }

    /** Says [text] at [volumePercent] of the phone's media volume range (1–100). */
    fun say(text: String, volumePercent: Int) {
        if (!ready) return
        runCatching {
            setVolume(volumePercent)
            audio?.requestAudioFocus(focus)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "verdict")
        }.onFailure { release(); fail("${it.javaClass.simpleName}: ${it.message}") }
    }

    fun shutdown() {
        ready = false
        release()
        runCatching { tts.stop(); tts.shutdown() }
    }

    private fun setVolume(percent: Int) {
        val am = audio ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val wanted = (max * percent.coerceIn(1, 100) + 50) / 100
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (savedVolume == null) savedVolume = current
        if (wanted != current) runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, wanted.coerceAtLeast(1), 0) }
    }

    /** After the verdict: media volume back to what it was, and music back up. */
    private fun release() {
        savedVolume?.let { v -> runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) } }
        savedVolume = null
        runCatching { audio?.abandonAudioFocusRequest(focus) }
    }

    private fun fail(why: String) {
        ready = false
        if (failed) return
        failed = true
        ServiceState.log("Voice unavailable", why)
    }
}
