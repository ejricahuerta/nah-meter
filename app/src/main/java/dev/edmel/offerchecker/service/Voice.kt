package dev.edmel.offerchecker.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.edmel.offerchecker.data.AppSettings
import java.util.Locale
import android.speech.tts.Voice as TtsVoice

/**
 * Says the verdict out loud ("Nah. 14 an hour") so a courier who is driving doesn't have to look.
 * Uses the phone's own text-to-speech engine (on the device, no network).
 *
 * It plays on the media volume, not the navigation one: Uber raises and lowers navigation volume
 * around its own prompts, which made the verdict too quiet or too loud. Just for the verdict, media
 * volume is set to the user's chosen level, then put back; music ducks under it meanwhile (unless
 * turned off). It stays silent at 0%, and during a phone call. If the engine can't start, it stays
 * silent and says so once in the diagnostics log.
 *
 * [onReady] is called once the engine has started, when [englishVoices] has something to list.
 */
class Voice(context: Context, private val onReady: () -> Unit = {}) {
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

    /** The voice name last handed to the engine ([NOTHING] until the first verdict). */
    private var appliedVoice: String? = NOTHING

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
                    .onSuccess { runCatching { onReady() } }
            } else {
                fail("text-to-speech engine didn't start (status $status)")
            }
        }
    }

    /**
     * Says [text] with the voice settings in [s]: at [AppSettings.voiceVolume] percent of the
     * phone's media volume range (0 says nothing), at [AppSettings.voiceSpeed], in the picked voice.
     */
    fun say(text: String, s: AppSettings) {
        if (!ready || s.voiceVolume <= 0) return
        if (onCall()) {
            ServiceState.log("Verdict not spoken", "a phone call is in progress")
            return
        }
        runCatching {
            applyVoice(s.voiceName)
            tts.setSpeechRate(s.voiceSpeed)
            setVolume(s.voiceVolume)
            if (s.voiceDuck) audio?.requestAudioFocus(focus)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "verdict")
        }.onFailure { release(); fail("${it.javaClass.simpleName}: ${it.message}") }
    }

    /** English voices that work without a network, as (name, label) pairs; empty until the engine has started. */
    fun englishVoices(): List<Pair<String, String>> {
        if (!ready) return emptyList()
        return runCatching {
            val voices = tts.voices.orEmpty()
                .filter { it.locale.language == "en" && usable(it) }
                .sortedWith(compareBy({ it.locale.toString() }, { it.name }))
            val perLocale = voices.groupingBy { it.locale }.eachCount()
            val seen = mutableMapOf<Locale, Int>()
            voices.map { v ->
                val n = (seen[v.locale] ?: 0) + 1
                seen[v.locale] = n
                val label = v.locale.getDisplayName(Locale.getDefault())
                v.name to (if ((perLocale[v.locale] ?: 1) > 1) "$label · $n" else label)
            }
        }.getOrDefault(emptyList())
    }

    fun shutdown() {
        ready = false
        release()
        runCatching { tts.stop(); tts.shutdown() }
    }

    /** Hands the engine the picked voice; with none picked, English (Canada) if installed, else the phone's own. */
    private fun applyVoice(name: String?) {
        if (name == appliedVoice) return
        val all = tts.voices.orEmpty()
        val picked = if (name == null) null else all.firstOrNull { it.name == name && usable(it) }
        val chosen = picked ?: all.firstOrNull { it.locale.language == "en" && it.locale.country == "CA" && usable(it) }
        if (chosen != null) tts.voice = chosen
        else if (tts.isLanguageAvailable(Locale.getDefault()) >= TextToSpeech.LANG_AVAILABLE) tts.language = Locale.getDefault()
        appliedVoice = name
    }

    /** Installed, and not one that needs the network to speak. */
    private fun usable(v: TtsVoice) =
        !v.isNetworkConnectionRequired && !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    /** On a phone or VoIP call: the verdict would talk over the other person. */
    private fun onCall(): Boolean {
        val mode = audio?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
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

    private companion object {
        /** A name no voice has, so the first verdict always applies the picked voice. */
        const val NOTHING = "\u0000"
    }
}
