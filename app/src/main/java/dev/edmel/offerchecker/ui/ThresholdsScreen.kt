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
import androidx.compose.runtime.produceState
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
import dev.edmel.offerchecker.core.Priority
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.IdleMode
import dev.edmel.offerchecker.data.OfferDatabase
import dev.edmel.offerchecker.data.SettingsRepository
import dev.edmel.offerchecker.service.OnlineClock
import dev.edmel.offerchecker.service.ServiceState
import dev.edmel.offerchecker.service.TripClock
import kotlinx.coroutines.delay
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

    // The offer to preview: the last one read, else the last logged, else the PRD's example. A
    // logged offer is rescored from Uber's minutes (cardMinutes), so the rules aren't added twice.
    val lastRead by ServiceState.lastRead.collectAsState()
    val logged by remember { OfferDatabase.get(ctx).offers().observeAll() }.collectAsState(initial = emptyList())
    val sample: Offer = lastRead?.evaluation?.offer
        ?: logged.firstOrNull()?.let {
            Offer(
                BigDecimal.valueOf(it.pay), it.cardMinutes ?: it.minutes, BigDecimal.valueOf(it.km), it.stacked,
                dropoff = it.dropoff, orders = it.orders, addOn = it.addOn,
            )
        }
        ?: Offer(BigDecimal("6.07"), 23, BigDecimal("11.5"))
    val isExample = lastRead == null && logged.isEmpty()
    // Scored as a card would be now: with the idle-aware rules when they're on, at today's wait.
    val uber by remember { OnlineClock.status(ctx) }.collectAsState()
    val pay by remember { TripClock.pay(ctx) }.collectAsState()
    val minute by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    val waitCtx = remember(uber, pay, minute) { TripClock.scoreContext(ctx, maxOf(minute, System.currentTimeMillis())) }
    val preview = Scorer.score(sample, settings.mainThresholds, waitCtx)

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Rules", style = Nah.title.copy(color = c.ink))

        // The real verdict card, scored with the rules below as they change.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VerdictCard(preview, t)
            Text(
                (if (isExample) "An example offer" else "Your last offer") +
                    " · $${sample.pay.toPlainString()} · ${sample.minutes} min · ${sample.km.toPlainString()} km" +
                    (if (sample.addOn) " · add-on" else if (sample.orders > 1) " · ${sample.orders} orders" else ""),
                style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted),
            )
        }

        NahGroup { RuleSteppers(t, showExtras = true) }

        Column {
            SectionLabel("Minimum pay")
            NahGroup { MinPaySteppers(t) }
        }

        Column {
            SectionLabel("Idle-aware rules")
            NahGroup { IdleRuleSteppers(settings) }
        }

        if (settings.idleMode != IdleMode.OFF) {
            Column {
                SectionLabel("Drive back by area")
                NahGroup { AreaSteppers(settings) }
            }
        }

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

/**
 * The rule steppers, as rows to put in a [NahGroup]: which rate is strict, per hour, per km and,
 * with [showExtras], max km, the unpaid extra km and the wait buffer. Without [showExtras] (Setup)
 * it also has the one-order minimum pay; the Rules tab has every minimum in [MinPaySteppers].
 */
@Composable
fun RuleSteppers(t: Thresholds, showExtras: Boolean) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val perKmFirst = t.priority == Priority.PER_KM
    SegmentedRow(
        "Strict on", "The other one only gives meh.", listOf("$/hr", "$/km"), if (perKmFirst) 1 else 0,
    ) { i -> scope.launch { repo.setPriority(if (i == 1) Priority.PER_KM else Priority.HOURLY) } }
    NahDivider()
    if (!showExtras) {
        MoneyStepper(
            "Minimum pay", "minimum pay", t.minPay, hint = "For one order. Below this is nah.", max = "80",
        ) { repo.setMinPay(it) }
        NahDivider()
    }
    ThresholdField(
        "Per hour", money(t.minHourly), "/hr", if (perKmFirst) "Under this is meh." else "Under this is nah.",
        "Decrease per hour by $0.50", "Increase per hour by $0.50",
        { scope.launch { repo.setMinHourly(step(t.minHourly, "-0.50", "5", "80")) } },
        { scope.launch { repo.setMinHourly(step(t.minHourly, "0.50", "5", "80")) } },
    )
    NahDivider()
    ThresholdField(
        "Per km", money(t.minPerKm), "/km", if (perKmFirst) "Under this is nah." else "Under this is meh.",
        "Decrease per km by $0.05", "Increase per km by $0.05",
        { scope.launch { repo.setMinPerKm(step(t.minPerKm, "-0.05", "0", "5")) } },
        { scope.launch { repo.setMinPerKm(step(t.minPerKm, "0.05", "0", "5")) } },
    )
    if (showExtras) {
        // Max km: Off, then 5 to 100 km in 1 km steps.
        fun setMax(km: Int?) {
            scope.launch { repo.setMaxKm(km?.takeIf { it >= 5 }?.coerceAtMost(100)?.let { BigDecimal(it) }) }
        }
        val max = t.maxKm?.setScale(0, RoundingMode.HALF_UP)?.toInt()
        NahDivider()
        ThresholdField(
            "Max km", max?.toString() ?: "Off", if (max == null) null else " km", "Over this is nah.",
            "Decrease max km by 1 km", "Increase max km by 1 km",
            { setMax(max?.minus(1)) },
            { setMax(if (max == null) 5 else max + 1) },
        )
        NahDivider()
        val pct = t.extraKmPercent
        ThresholdField(
            "Extra km (unpaid)", if (pct == 0) "Off" else "$pct", if (pct == 0) null else "%",
            "The drive back. Lowers $/km and $/hr.",
            "Decrease extra km by 10%", "Increase extra km by 10%",
            { scope.launch { repo.setExtraKmPercent(pct - 10) } },
            { scope.launch { repo.setExtraKmPercent(pct + 10) } },
        )
        if (pct > 0) {
            NahDivider()
            OverKmField(t)
        }
        NahDivider()
        ThresholdField(
            "Restaurant wait buffer", "${t.waitBufferMin}", " min", "Added to Uber's time",
            "Decrease wait buffer by 1 minute", "Increase wait buffer by 1 minute",
            { scope.launch { repo.setWaitBuffer((t.waitBufferMin - 1).coerceIn(0, 30)) } },
            { scope.launch { repo.setWaitBuffer((t.waitBufferMin + 1).coerceIn(0, 30)) } },
        )
    }
}

/** The drive back (either rule) only counts on trips over this many km. */
@Composable
private fun OverKmField(t: Thresholds) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val over = t.extraKmOverKm
    ThresholdField(
        "Only on trips over", if (over == 0) "Any trip" else "$over", if (over == 0) null else " km",
        "Shorter trips stay in your area.",
        "Decrease by 1 km", "Increase by 1 km",
        { scope.launch { repo.setExtraKmOverKm(over - 1) } },
        { scope.launch { repo.setExtraKmOverKm(over + 1) } },
    )
}

/**
 * The idle-aware rules (the findings PRD, 2026-11-05) as rows to put in a [NahGroup]: off,
 * compared with the fixed rules, or on; then the bar's two drops, how much sooner they come late at
 * night, the meh. band and the stack bonus.
 */
@Composable
private fun IdleRuleSteppers(settings: AppSettings) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val mode = settings.idleMode
    val r = settings.idleRules
    SegmentedRow(
        "Bar drops while you wait",
        when (mode) {
            IdleMode.OFF -> "Your $/hr bar comes down the longer Uber hasn't paid."
            IdleMode.COMPARE -> "The card shows the new rules' verdict under yours."
            IdleMode.ON -> "The new rules set the verdict."
        },
        listOf("Off", "Compare", "On"), mode.ordinal,
    ) { i -> scope.launch { repo.setIdleMode(IdleMode.entries[i]) } }
    if (mode == IdleMode.OFF) return
    r.steps.forEachIndexed { i, step ->
        val which = if (i == 0) "first" else "second"
        NahDivider()
        ThresholdField(
            "${which.replaceFirstChar { it.uppercase() }} drop after", "${step.afterMin}", " min", "Without pay from Uber",
            "Decrease the $which drop's wait by 5 minutes", "Increase the $which drop's wait by 5 minutes",
            { scope.launch { repo.setIdleStepMin(i, step.afterMin - 5) } },
            { scope.launch { repo.setIdleStepMin(i, step.afterMin + 5) } },
        )
        NahDivider()
        MoneyStepper(
            "Bar then", "the bar after the $which drop", step.hourly, unit = "/hr",
            hint = if (i == r.steps.lastIndex) "The floor: it never goes lower." else null,
            max = "80", by = "0.50",
        ) { repo.setIdleStepHourly(i, it) }
    }
    NahDivider()
    val sooner = r.lateSoonerMin
    ThresholdField(
        "Late night, from 10pm", if (sooner == 0) "Off" else "$sooner", if (sooner == 0) null else " min sooner",
        "Each drop comes sooner: good offers are rarer then.",
        "Decrease by 1 minute", "Increase by 1 minute",
        { scope.launch { repo.setLateSoonerMin(sooner - 1) } },
        { scope.launch { repo.setLateSoonerMin(sooner + 1) } },
    )
    NahDivider()
    MoneyStepper(
        "Meh band", "the meh band", r.mehBand, unit = "/hr", hint = "This close under the bar is meh., not nah.",
        max = "10", offAtZero = true,
    ) { repo.setMehBand(it) }
    NahDivider()
    val home = r.areas.filter { it.home }.flatMap { it.names }.joinToString(" or ")
    NahRow("Stack ending home", "A meh. stack ending in $home is bet!") {
        NahSwitch(r.homeStackBonus, { v -> scope.launch { repo.setHomeStackBonus(v) } }, "Stack ending home")
    }
}

/**
 * The unpaid drive back from each drop-off area (the town at the end of Uber's drop-off line),
 * under the idle-aware rules, as rows to put in a [NahGroup]. Other areas keep the extra km rule.
 */
@Composable
private fun AreaSteppers(settings: AppSettings) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val t = settings.thresholds
    settings.idleRules.areas.forEachIndexed { i, a ->
        if (i > 0) NahDivider()
        val name = a.names.first()
        ThresholdField(
            a.label, if (a.minutes == 0) "None" else "${a.minutes}", if (a.minutes == 0) null else " min",
            if (a.home) "Home" else null,
            "Decrease the drive back from $name by 5 minutes", "Increase the drive back from $name by 5 minutes",
            { scope.launch { repo.setAreaMinutes(name, a.minutes - 5) } },
            { scope.launch { repo.setAreaMinutes(name, a.minutes + 5) } },
        )
    }
    NahDivider()
    NahRow(
        "Anywhere else",
        if (t.extraKmPercent > 0) "Extra km: +${t.extraKmPercent}% of the trip" else "No drive back while Extra km is off",
    )
    // The same km limit as the extra km rule, shown there when that rule is on.
    if (t.extraKmPercent == 0) {
        NahDivider()
        OverKmField(t)
    }
}

/**
 * Minimum pay by the card's orders (1, 2, 3 or more, an add-on), and the tip cushion that comes off
 * each order's pay first, as rows to put in a [NahGroup].
 */
@Composable
fun MinPaySteppers(t: Thresholds) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    MoneyStepper("1 order", "minimum pay for 1 order", t.minPay, max = "80") { repo.setMinPay(it) }
    NahDivider()
    MoneyStepper("2 orders", "minimum pay for 2 orders", t.minPay2, max = "80") { repo.setMinPay2(it) }
    NahDivider()
    MoneyStepper("3+ orders", "minimum pay for 3 or more orders", t.minPay3, max = "80") { repo.setMinPay3(it) }
    NahDivider()
    MoneyStepper(
        "Add-on", "the add-on minimum", t.minAddOn, hint = "What an \"Add a delivery\" card adds.",
        max = "50", offAtZero = true,
    ) { repo.setMinAddOn(it) }
    NahDivider()
    MoneyStepper(
        "Tip cushion", "the tip cushion", t.tipCushion, unit = "/order",
        hint = "Pay includes the tip. Taken off per order, in case one doesn't come.",
        max = "10", offAtZero = true,
    ) { repo.setTipCushion(it) }
}

/** A dollar amount in steps of [by] ($0.25 by default), from $0 to [max]; "Off" at $0 when [offAtZero]. */
@Composable
private fun MoneyStepper(
    label: String, spokenLabel: String, value: BigDecimal, unit: String? = null, hint: String? = null,
    max: String, offAtZero: Boolean = false, by: String = "0.25", set: suspend (BigDecimal) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val off = offAtZero && value.signum() == 0
    ThresholdField(
        label, if (off) "Off" else money(value), if (off) null else unit, hint,
        "Decrease $spokenLabel by $$by", "Increase $spokenLabel by $$by",
        { scope.launch { set(step(value, "-$by", "0", max)) } },
        { scope.launch { set(step(value, by, "0", max)) } },
    )
}

private fun money(v: BigDecimal) = "$" + v.setScale(2, RoundingMode.HALF_UP).toPlainString()
