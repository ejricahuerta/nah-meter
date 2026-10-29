package dev.edmel.offerchecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.SettingsRepository
import dev.edmel.offerchecker.data.VoiceMode
import dev.edmel.offerchecker.service.NahCopy
import dev.edmel.offerchecker.service.Voice
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val MODES = listOf(
    VoiceMode.WORD_HOURLY to "Word + $/hr",
    VoiceMode.WORD to "Word only",
    VoiceMode.WORD_HOURLY_KM to "Word + $/hr + $/km",
)

private val SPOKEN_VERDICTS = listOf(
    Verdict.GREEN to "Clears all your rules",
    Verdict.YELLOW to "Pays the hour but short on $/km",
    Verdict.RED to "Under your minimum or $/hr",
    Verdict.GREY to "A number couldn't be read",
)

/**
 * Settings → Voice: whether the verdict is read out loud, exactly what it says, which verdicts,
 * and how it sounds. It has its own [Voice], so everything can be tried without Accessibility;
 * the reader's voice uses the same settings.
 */
@Composable
fun VoiceScreen(settings: AppSettings, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val c = Nah.c
    var engineReady by remember { mutableIntStateOf(0) }
    val voice = remember { Voice(ctx, onReady = { engineReady++ }) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    val voices = remember(engineReady) { voice.englishVoices() }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    // The PRD's example offer, scored on the courier's own rules.
    val preview = Scorer.score(EXAMPLE_OFFER, settings.thresholds)
    val line = NahCopy.speech(preview, settings.voiceMode)
    // Settings are saved a moment after a tap, so a test passes the new value along itself.
    fun test(s: AppSettings) = voice.say(NahCopy.speech(preview, s.voiceMode), s)

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BackHeader("Voice", onBack)

        NahGroup {
            NahRow("Read verdicts out loud", "For when you're driving") {
                NahSwitch(settings.voice, { v -> scope.launch { repo.setVoice(v) } }, "Read verdicts out loud")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                SectionLabel("You'll hear")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nah.card)
                        .border(1.dp, Nah.cardEdge, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        "“$line”",
                        style = Nah.style(28.sp, 800, 34.sp, (-0.02).em, Nah.verdictColor(preview.verdict)),
                    )
                    Text(
                        "On a $${EXAMPLE_OFFER.pay.toPlainString()} offer, ${EXAMPLE_OFFER.minutes} min, ${EXAMPLE_OFFER.km.toPlainString()} km",
                        style = Nah.style(15.sp, 400, 22.sp, color = Nah.cardMuted),
                    )
                }
            }
            NahButton("Test voice", { test(settings) }, modifier = Modifier.fillMaxWidth(), kind = NahButtonKind.Secondary)
        }

        Column {
            SectionLabel("What it says")
            NahGroup {
                MODES.forEachIndexed { i, (mode, title) ->
                    if (i > 0) NahDivider()
                    RadioRow(title, "\"${NahCopy.speech(preview, mode)}\"", settings.voiceMode == mode) {
                        scope.launch { repo.setVoiceMode(mode) }
                    }
                }
            }
        }

        Column {
            SectionLabel("Speak these verdicts")
            NahGroup {
                SPOKEN_VERDICTS.forEachIndexed { i, (v, hint) ->
                    if (i > 0) NahDivider()
                    NahRow(
                        NahCopy.word(v), hint,
                        leading = { Box(Modifier.size(12.dp).clip(CircleShape).background(Nah.verdictColor(v))) },
                    ) {
                        NahSwitch(
                            v in settings.voiceVerdicts,
                            { on ->
                                scope.launch { repo.setVoiceVerdicts(if (on) settings.voiceVerdicts + v else settings.voiceVerdicts - v) }
                            },
                            "Say ${NahCopy.word(v)}",
                        )
                    }
                }
            }
        }

        NahGroup {
            fun setVolume(v: Int) {
                val level = v.coerceIn(0, 100)
                scope.launch { repo.setVoiceVolume(level) }
                test(settings.copy(voiceVolume = level))
            }
            ThresholdField(
                "Volume", "${settings.voiceVolume}", "%",
                if (settings.voiceVolume == 0) "Silent: nothing is spoken." else "nah.meter's own level, set just for the verdict.",
                "Volume down 5%", "Volume up 5%",
                { setVolume(settings.voiceVolume - 5) },
                { setVolume(settings.voiceVolume + 5) },
            )
            NahDivider()
            fun setSpeed(tenths: Int) {
                val speed = tenths.coerceIn(5, 20) / 10f
                scope.launch { repo.setVoiceSpeed(speed) }
                test(settings.copy(voiceSpeed = speed))
            }
            val tenths = (settings.voiceSpeed * 10).roundToInt()
            ThresholdField(
                "Speed", String.format(Locale.US, "%.1f", tenths / 10f), "×", "How fast it talks. 1.0 is normal.",
                "Speak slower", "Speak faster",
                { setSpeed(tenths - 1) },
                { setSpeed(tenths + 1) },
            )
        }

        Column {
            SectionLabel("Voice")
            NahGroup {
                val current = voices.firstOrNull { it.first == settings.voiceName }?.second
                Box(Modifier.fillMaxWidth().clickable(enabled = voices.isNotEmpty(), role = Role.Button) { pickerOpen = !pickerOpen }) {
                    NahRow(
                        current ?: "Automatic",
                        if (current != null) "Your pick"
                        else if (voices.isEmpty()) "Your phone's own voice"
                        else "English (Canada) if installed, else your phone's own voice",
                        trailing = {
                            if (voices.isNotEmpty()) {
                                Text(if (pickerOpen) "Done" else "Change", style = Nah.style(15.sp, 600, 20.sp, color = Nah.c.ink))
                            }
                        },
                    )
                }
                if (pickerOpen && voices.isNotEmpty()) {
                    NahDivider()
                    RadioRow("Automatic", "English (Canada) if installed, else your phone's own voice", settings.voiceName == null) {
                        scope.launch { repo.setVoiceName(null) }
                        test(settings.copy(voiceName = null))
                    }
                    voices.forEach { (name, label) ->
                        NahDivider()
                        RadioRow(label, null, settings.voiceName == name) {
                            scope.launch { repo.setVoiceName(name) }
                            test(settings.copy(voiceName = name))
                        }
                    }
                }
            }
        }

        NahGroup {
            NahRow("Lower other audio", "Music and podcasts get quieter while it speaks") {
                NahSwitch(settings.voiceDuck, { v -> scope.launch { repo.setVoiceDuck(v) } }, "Lower other audio")
            }
        }

        Text(
            "Volume is nah.meter's own level, set just for the verdict. Uber can't change it. " +
                "Nothing is spoken during a phone call.",
            style = Nah.style(13.sp, 400, 18.sp, color = c.inkMuted),
        )
    }
}

/** A row with a radio mark on the right: one choice out of a few. */
@Composable
private fun RadioRow(title: String, desc: String?, selected: Boolean, onSelect: () -> Unit) {
    val c = Nah.c
    Box(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)) {
        NahRow(
            title, desc,
            trailing = {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).border(2.dp, if (selected) c.ink else c.controlBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(c.ink))
                }
            },
        )
    }
}
