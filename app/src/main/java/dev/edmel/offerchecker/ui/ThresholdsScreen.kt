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
            Text("Thresholds", style = Wi.title)
            Text("Checked in order. The first one missed sets the verdict.", style = Wi.label)
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Wi.surface)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${if (isExample) "Example offer" else "Your last offer"} would be " +
                        "${Wi.word(preview.verdict)}: ${preview.ratesText}"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    append(if (isExample) "Example offer\n" else "Your last offer\n")
                    withStyle(SpanStyle(color = Wi.ink, fontWeight = FontWeight.W600)) {
                        append("$${sample.pay.toPlainString()} · ${sample.minutes} min · ${sample.km.toPlainString()} km")
                    }
                },
                style = Wi.style(14.sp, 400, 18.sp, color = Wi.inkMuted),
                modifier = Modifier.weight(1f),
            )
            VerdictPill(preview.verdict, background = Wi.surfaceRaised)
        }

        WiGroup {
            ThresholdField(
                "1 · Minimum pay", money(t.minPay), null, "Below this is always SKIP",
                "Decrease minimum pay by $0.50", "Increase minimum pay by $0.50",
                { scope.launch { repo.setMinPay(step(t.minPay, "-0.50", "0", "50")) } },
                { scope.launch { repo.setMinPay(step(t.minPay, "0.50", "0", "50")) } },
            )
            WiDivider()
            val mins = sample.minutes + t.waitBufferMin
            ThresholdField(
                "2 · Hourly target", money(t.minHourly), "/hr",
                "$mins min offer needs ${money(t.minHourly * BigDecimal(mins) / BigDecimal(60))}",
                "Decrease hourly target by $0.50", "Increase hourly target by $0.50",
                { scope.launch { repo.setMinHourly(step(t.minHourly, "-0.50", "5", "80")) } },
                { scope.launch { repo.setMinHourly(step(t.minHourly, "0.50", "5", "80")) } },
            )
            WiDivider()
            ThresholdField(
                "3 · Per-km target", money(t.minPerKm), "/km",
                "${sample.km.toPlainString()} km offer needs ${money(t.minPerKm * sample.km)}",
                "Decrease per-km target by $0.05", "Increase per-km target by $0.05",
                { scope.launch { repo.setMinPerKm(step(t.minPerKm, "-0.05", "0", "5")) } },
                { scope.launch { repo.setMinPerKm(step(t.minPerKm, "0.05", "0", "5")) } },
            )
            WiDivider()
            ThresholdField(
                "Restaurant wait buffer", "${t.waitBufferMin}", " min", "Added to Uber's time",
                "Decrease wait buffer by 1 minute", "Increase wait buffer by 1 minute",
                { scope.launch { repo.setWaitBuffer((t.waitBufferMin - 1).coerceIn(0, 30)) } },
                { scope.launch { repo.setWaitBuffer((t.waitBufferMin + 1).coerceIn(0, 30)) } },
            )
        }

        WiGroup {
            WiRow("Vibrate on verdict", "GO short · MAYBE double · SKIP long") {
                WiSwitch(settings.vibration, { v -> scope.launch { repo.setVibration(v) } }, "Vibrate on verdict")
            }
            WiDivider()
            WiRow("Compact badge", "Pill at the top instead of the card") {
                WiSwitch(settings.compactBadge, { v -> scope.launch { repo.setCompactBadge(v) } }, "Compact badge")
            }
        }
    }
}

private fun money(v: BigDecimal) = "$" + v.setScale(2, RoundingMode.HALF_UP).toPlainString()
