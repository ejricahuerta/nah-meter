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
import dev.edmel.offerchecker.core.Thresholds
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

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Rules", style = Nah.title.copy(color = c.ink))

        // The real verdict card, scored with the rules below as they change.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VerdictCard(
                preview.verdict, preview.hourly?.let { "$${it.toPlainString()}" }, NahCopy.cardReason(preview, t),
                spoken = NahCopy.spoken(preview, t), kmChip = NahCopy.kmChip(preview),
            )
            Text(
                (if (isExample) "An example offer" else "Your last offer") +
                    " · $${sample.pay.toPlainString()} · ${sample.minutes} min · ${sample.km.toPlainString()} km",
                style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted),
            )
        }

        NahGroup { RuleSteppers(t, showBuffer = true) }

        Column {
            SectionLabel("Per app (optional)")
            NahGroup {
                NahRow("Uber Eats") {
                    Text("Default", style = Nah.style(14.sp, 600, 20.sp, color = c.inkMuted))
                }
                NahDivider()
                NahRow("DoorDash") {
                    Text("Soon", style = Nah.style(14.sp, 600, 20.sp, color = c.inkMuted))
                }
            }
        }
    }
}

private fun step(v: BigDecimal, delta: String, lo: String, hi: String) =
    (v + BigDecimal(delta)).max(BigDecimal(lo)).min(BigDecimal(hi)).setScale(2, RoundingMode.HALF_UP)

/** The rule steppers, as rows to put in a [NahGroup]: minimum pay, per hour, per km and (optionally) the wait buffer. */
@Composable
fun RuleSteppers(t: Thresholds, showBuffer: Boolean) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    ThresholdField(
        "Minimum pay", money(t.minPay), null, "Below this is always nah.",
        "Decrease minimum pay by $0.25", "Increase minimum pay by $0.25",
        { scope.launch { repo.setMinPay(step(t.minPay, "-0.25", "0", "50")) } },
        { scope.launch { repo.setMinPay(step(t.minPay, "0.25", "0", "50")) } },
    )
    NahDivider()
    ThresholdField(
        "Per hour", money(t.minHourly), "/hr", "Under this is nah.",
        "Decrease per hour by $0.50", "Increase per hour by $0.50",
        { scope.launch { repo.setMinHourly(step(t.minHourly, "-0.50", "5", "80")) } },
        { scope.launch { repo.setMinHourly(step(t.minHourly, "0.50", "5", "80")) } },
    )
    NahDivider()
    ThresholdField(
        "Per km", money(t.minPerKm), "/km", "Under this is eh.",
        "Decrease per km by $0.05", "Increase per km by $0.05",
        { scope.launch { repo.setMinPerKm(step(t.minPerKm, "-0.05", "0", "5")) } },
        { scope.launch { repo.setMinPerKm(step(t.minPerKm, "0.05", "0", "5")) } },
    )
    if (showBuffer) {
        NahDivider()
        ThresholdField(
            "Restaurant wait buffer", "${t.waitBufferMin}", " min", "Added to Uber's time",
            "Decrease wait buffer by 1 minute", "Increase wait buffer by 1 minute",
            { scope.launch { repo.setWaitBuffer((t.waitBufferMin - 1).coerceIn(0, 30)) } },
            { scope.launch { repo.setWaitBuffer((t.waitBufferMin + 1).coerceIn(0, 30)) } },
        )
    }
}

private fun money(v: BigDecimal) = "$" + v.setScale(2, RoundingMode.HALF_UP).toPlainString()
