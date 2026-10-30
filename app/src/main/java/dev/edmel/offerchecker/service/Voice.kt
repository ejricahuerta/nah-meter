package dev.edmel.offerchecker.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.edmel.offerchecker.R
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.AppSettings
import java.util.Locale
import android.speech.tts.Voice as TtsVoice

/**
 * Says the verdict out loud ("nah. 14 an hour") so a courier who is driving doesn't have to look.
 * Uses the phone's own text-to-speech engine (on the device, no network). With the Toronto voice
 * on, the word is a recorded line from res/raw ("nah fam.", [NahCopy.clipLine]) and the engine says
 * the rest; if a recording won't play, the engine says its line instead.
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
    private val app = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    // Another app taking the audio (Uber's offer sound) only goes in the log, for now.
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change -> note("audio focus: ${focusChange(change)}") }, main)
        .build()

    @Volatile private var ready = false
    private var failed = false
    private lateinit var tts: TextToSpeech

    /** Media volume before the verdict raised or lowered it, to put back after; null when untouched. */
    @Volatile private var savedVolume: Int? = null

    /** The voice name last handed to the engine ([NOTHING] until the first verdict). */
    private var appliedVoice: String? = NOTHING

    // The verdict being said, part by part. Touched only on the main thread.
    private val queue = ArrayDeque<NahCopy.SpeechPart>()
    private var player: MediaPlayer? = null
    private var clipSpeed = 1f
    /** Bumped for each verdict, so a late callback from the one before can't move this one on. */
    private var generation = 0

    /** The verdict being said (null when quiet), when it started, and what got in its way. */
    private var saying: List<NahCopy.SpeechPart>? = null
    private var startedAt = 0L
    private val events = mutableListOf<String>()
    /** The media volume this verdict set, to notice another app changing it. */
    private var volumeSet: Int? = null

    init {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching {
                    tts.setAudioAttributes(attributes)
                    if (tts.isLanguageAvailable(Locale.getDefault()) >= TextToSpeech.LANG_AVAILABLE) tts.language = Locale.getDefault()
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) { main.post { if (id == utterance()) next() } }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            main.post { if (id == utterance()) { note("phone voice error"); next() } }
                        }
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
     * Says [parts] ([NahCopy.speechParts]) with the voice settings in [s]: at
     * [AppSettings.voiceVolume] percent of the phone's media volume range (0 says nothing), at
     * [AppSettings.voiceSpeed], in the picked voice. A new verdict cuts off the one being said; the
     * same verdict again while it's still being said (the card read again) lets it finish.
     */
    fun say(parts: List<NahCopy.SpeechPart>, s: AppSettings) {
        if (!ready || s.voiceVolume <= 0 || parts.isEmpty()) return
        if (onCall()) {
            ServiceState.log("Verdict not spoken", "a phone call is in progress")
            return
        }
        main.post {
            if (parts == saying) {
                note("same verdict again, not restarted")
                return@post
            }
            runCatching {
                if (saying != null) logSpoken("cut off by the next verdict")
                stopSpeaking()
                saying = parts
                startedAt = SystemClock.elapsedRealtime()
                events.clear()
                applyVoice(s.voiceName)
                tts.setSpeechRate(s.voiceSpeed)
                clipSpeed = s.voiceSpeed
                setVolume(s.voiceVolume)
                if (s.voiceDuck) {
                    val granted = audio?.requestAudioFocus(focus)
                    if (granted != null && granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) note("audio focus not granted ($granted)")
                }
                queue.addAll(parts)
                next()
            }.onFailure { stopSpeaking(); release(); fail("${it.javaClass.simpleName}: ${it.message}") }
        }
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
        main.removeCallbacksAndMessages(null)
        if (saying != null) logSpoken("stopped: the voice shut down")
        stopSpeaking()
        release()
        runCatching { tts.shutdown() }
    }

    private fun utterance() = "verdict-$generation"

    /**
     * Says the next part: its recorded line if it has one, else through the engine. When nothing is
     * left, media volume and music go back.
     */
    private fun next() {
        checkVolume()
        val part = queue.removeFirstOrNull() ?: return finish("finished")
        val clip = part.clip
        if (clip != null && playClip(clip, part.text)) return
        // Parts without a recording go to the engine as one line, so they sound as they always have.
        val line = StringBuilder(part.text)
        while (queue.isNotEmpty() && queue.first().clip == null) line.append(' ').append(queue.removeFirst().text)
        if (tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, utterance()) != TextToSpeech.SUCCESS) {
            queue.clear()
            finish("the phone voice didn't start")
        }
    }

    private fun finish(outcome: String) {
        logSpoken(outcome)
        release()
    }

    /** Plays [v]'s recorded line; false if it can't start, so the engine says [text] instead. */
    private fun playClip(v: Verdict, text: String): Boolean {
        val am = audio ?: return false
        val p = runCatching { MediaPlayer.create(app, clipRes(v), attributes, am.generateAudioSessionId()) }.getOrNull()
        if (p == null) {
            note("recording didn't open, phone voice instead")
            return false
        }
        return runCatching {
            p.setOnCompletionListener { finished(it) }
            p.setOnErrorListener { mp, what, extra ->
                note("recording stopped with error $what/$extra, phone voice instead")
                queue.addFirst(NahCopy.SpeechPart(text))
                finished(mp)
                true
            }
            player = p
            // A speed other than 0 also starts a prepared player.
            runCatching { p.playbackParams = p.playbackParams.setSpeed(clipSpeed) }
            if (!p.isPlaying) p.start()
            true
        }.getOrElse {
            player = null
            p.release()
            note("recording didn't play (${it.javaClass.simpleName}), phone voice instead")
            false
        }
    }

    private fun finished(mp: MediaPlayer) {
        mp.release()
        if (player !== mp) return
        player = null
        next()
    }

    private fun clipRes(v: Verdict) = when (v) {
        Verdict.GREEN -> R.raw.verdict_bet
        Verdict.YELLOW -> R.raw.verdict_meh
        Verdict.RED -> R.raw.verdict_nah
        Verdict.GREY -> R.raw.verdict_cant_read
    }

    /** Cuts off whatever is being said, recording or engine, so the next verdict starts at once. */
    private fun stopSpeaking() {
        generation++
        queue.clear()
        saying = null
        player?.let { p -> runCatching { p.stop() }; p.release() }
        player = null
        runCatching { tts.stop() }
    }

    /** Something that got in the way of the verdict being said, for its "Spoke" log entry. */
    private fun note(event: String) {
        if (saying == null) return
        val at = (SystemClock.elapsedRealtime() - startedAt) / 100 / 10.0
        events += "at $at s: $event"
    }

    /** One diagnostics entry per spoken verdict: what was said, how, how long it took, and what got in the way. */
    private fun logSpoken(outcome: String) {
        val parts = saying ?: return
        val seconds = (SystemClock.elapsedRealtime() - startedAt) / 100 / 10.0
        ServiceState.log(
            "Spoke: ${parts.joinToString(" ") { it.text }}",
            parts.joinToString(" + ") { if (it.clip != null) "recording" else "phone voice" } + " · $outcome after $seconds s",
            *events.toTypedArray(),
        )
        events.clear()
        saying = null
    }

    /** Notes another app changing the media volume while the verdict is said (Uber does its own volume). */
    private fun checkVolume() {
        val set = volumeSet ?: return
        val now = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
        if (now == set) return
        note("media volume changed by another app: $set → $now")
        volumeSet = now
    }

    private fun focusChange(change: Int) = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> "back"
        AudioManager.AUDIOFOCUS_LOSS -> "taken by another app"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "taken by another app for a moment"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "another app is playing over it"
        else -> "change $change"
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
        volumeSet = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    /** After the verdict: media volume back to what it was, and music back up. */
    private fun release() {
        savedVolume?.let { v -> runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) } }
        savedVolume = null
        volumeSet = null
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
