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
 * Uses the phone's own text-to-speech engine (on the device, no network). Plays like a navigation
 * prompt: music ducks under it, and it sits beside Uber's own voice. If the engine can't start,
 * it stays silent and says so once in the diagnostics log.
 */
class Voice(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    @Volatile private var ready = false
    private var failed = false
    private lateinit var tts: TextToSpeech

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

    fun say(text: String) {
        if (!ready) return
        runCatching {
            audio?.requestAudioFocus(focus)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "verdict")
        }.onFailure { fail("${it.javaClass.simpleName}: ${it.message}") }
    }

    fun shutdown() {
        ready = false
        release()
        runCatching { tts.stop(); tts.shutdown() }
    }

    private fun release() {
        runCatching { audio?.abandonAudioFocusRequest(focus) }
    }

    private fun fail(why: String) {
        ready = false
        if (failed) return
        failed = true
        ServiceState.log("Voice unavailable", why)
    }
}
