package dev.edmel.offerchecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.core.Offer
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.OfferDatabase
import dev.edmel.offerchecker.data.SettingsRepository
import dev.edmel.offerchecker.service.NahCopy
import dev.edmel.offerchecker.service.ServiceState
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun ThresholdsScreen(settings: AppSettings, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val t = settings.thresholds
    val c = Nah.c

    // The offer to preview: the last one read, else the last logged, else the PRD's example.
    val lastRead by ServiceState.lastRead.collectAsState()
    val logged by remember { OfferDatabase.get(ctx).offers().observeAll() }.collectAsState(initial = emptyList())
    val sample: Offer = lastRead?.evaluation?.offer
        ?: logged.firstOrNull()?.let { Offer(BigDecimal.valueOf(it.pay), it.minutes, BigDecimal.valueOf(it.km), it.stacked) }
        ?: Offer(BigDecimal("6.07"), 23, BigDecimal("11.5"))
    val isExample = lastRead == null && logged.isEmpty()
    val preview = Scorer.score(sample, t)

    fun step(v: BigDecimal, delta: String, lo: String, hi: String) =
        (v + BigDecimal(delta)).max(BigDecimal(lo)).min(BigDecimal(hi)).setScale(2, RoundingMode.HALF_UP)

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Thresholds", style = Nah.title.copy(color = c.ink))
            Text("Checked in order. The first one missed decides.", style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted))
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${if (isExample) "An example offer" else "Your last offer"} would be " +
                        "${NahCopy.word(preview.verdict)} $${preview.hourly} per hour"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    append(if (isExample) "An example offer would be\n" else "Your last offer would be\n")
                    withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.W600)) {
                        append("$${sample.pay.toPlainString()} · ${sample.minutes} min · ${sample.km.toPlainString()} km")
                    }
                },
                style = Nah.style(14.sp, 400, 19.sp, color = c.inkMuted),
                modifier = Modifier.weight(1f),
            )
            VerdictPill(preview.verdict, preview.hourly?.let { "$${it.toPlainString()}" })
        }

        NahGroup {
            ThresholdField(
                "1 · Minimum pay", money(t.minPay), null, "Below this is always nah.",
                "Decrease minimum pay by $0.50", "Increase minimum pay by $0.50",
                { scope.launch { repo.setMinPay(step(t.minPay, "-0.50", "0", "50")) } },
                { scope.launch { repo.setMinPay(step(t.minPay, "0.50", "0", "50")) } },
            )
            NahDivider()
            val mins = sample.minutes + t.waitBufferMin
            ThresholdField(
                "2 · Hourly floor", money(t.minHourly), "/hr",
                "$mins min offer needs ${money(t.minHourly * BigDecimal(mins) / BigDecimal(60))}",
                "Decrease hourly floor by $0.50", "Increase hourly floor by $0.50",
                { scope.launch { repo.setMinHourly(step(t.minHourly, "-0.50", "5", "80")) } },
                { scope.launch { repo.setMinHourly(step(t.minHourly, "0.50", "5", "80")) } },
            )
            NahDivider()
            ThresholdField(
                "3 · Per-km floor", money(t.minPerKm), "/km",
                "${sample.km.toPlainString()} km offer needs ${money(t.minPerKm * sample.km)}",
                "Decrease per-km floor by $0.05", "Increase per-km floor by $0.05",
                { scope.launch { repo.setMinPerKm(step(t.minPerKm, "-0.05", "0", "5")) } },
                { scope.launch { repo.setMinPerKm(step(t.minPerKm, "0.05", "0", "5")) } },
            )
            NahDivider()
            ThresholdField(
                "Restaurant wait buffer", "${t.waitBufferMin}", " min", "Added to Uber's time",
                "Decrease wait buffer by 1 minute", "Increase wait buffer by 1 minute",
                { scope.launch { repo.setWaitBuffer((t.waitBufferMin - 1).coerceIn(0, 30)) } },
                { scope.launch { repo.setWaitBuffer((t.waitBufferMin + 1).coerceIn(0, 30)) } },
            )
        }

        NahGroup {
            NahRow("Buzz on verdict", "bet short · eh double · nah long") {
                NahSwitch(settings.vibration, { v -> scope.launch { repo.setVibration(v) } }, "Buzz on verdict")
            }
            NahDivider()
            NahRow("Say the verdict out loud", "\"nah. 14 an hour\", for when you're driving") {
                NahSwitch(settings.voice, { v -> scope.launch { repo.setVoice(v) } }, "Say the verdict out loud")
            }
            if (settings.voice) {
                NahDivider()
                // Its own level, on media volume: Uber turns navigation volume up and down.
                fun setVolume(v: Int) {
                    val level = v.coerceIn(10, 100)
                    scope.launch { repo.setVoiceVolume(level) }
                    ServiceState.service?.sayVolumeSample(level)
                }
                ThresholdField(
                    "Voice volume", "${settings.voiceVolume}", "%",
                    "Of your media volume, set just for the verdict. Uber can't change it.",
                    "Voice quieter by 10%", "Voice louder by 10%",
                    { setVolume(settings.voiceVolume - 10) },
                    { setVolume(settings.voiceVolume + 10) },
                )
            }
            NahDivider()
            NahRow("Compact verdict", "Small pill at the top instead") {
                NahSwitch(settings.compactBadge, { v -> scope.launch { repo.setCompactBadge(v) } }, "Compact verdict")
            }
        }
    }
}

private fun money(v: BigDecimal) = "$" + v.setScale(2, RoundingMode.HALF_UP).toPlainString()
