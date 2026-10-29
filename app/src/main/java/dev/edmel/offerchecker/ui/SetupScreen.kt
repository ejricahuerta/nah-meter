package dev.edmel.offerchecker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.core.Offer
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.SettingsRepository
import dev.edmel.offerchecker.service.NahCopy
import dev.edmel.offerchecker.service.Voice
import kotlinx.coroutines.launch
import java.math.BigDecimal

private val STEP_TITLES = listOf("Welcome", "Your apps", "Your rules", "Permissions", "Test it")

/**
 * Setup: five steps with a progress bar: Welcome, Your apps, Your rules, Permissions, Test it.
 * Shown on first launch and again from Settings → Run setup again. [onFinish] ends it.
 */
@Composable
fun SetupScreen(settings: AppSettings, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = STEP_TITLES.size - 1
    BackHandler(enabled = step > 0) { step-- }
    val c = Nah.c

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SetupStepper(step)
            when (step) {
                0 -> WelcomeStep(settings.thresholds)
                1 -> AppsStep(settings)
                2 -> RulesStep(settings.thresholds)
                3 -> PermissionsStep()
                else -> TestStep(settings)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().background(c.ground).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step > 0) NahButton("Back", { step-- }, modifier = Modifier.weight(1f), kind = NahButtonKind.Secondary)
            NahButton(
                when (step) {
                    0 -> "Get started"
                    last -> "Finish"
                    else -> "Continue"
                },
                { if (step == last) onFinish() else step++ },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A bar of five segments (done in ink, the current one red) over "Step 2 of 5 · Your apps". */
@Composable
private fun SetupStepper(step: Int) {
    val c = Nah.c
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            STEP_TITLES.forEachIndexed { i, _ ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(CircleShape)
                        .background(
                            when {
                                i < step -> c.ink
                                i == step -> c.stop
                                else -> c.line
                            },
                        ),
                )
            }
        }
        val label = "Step ${step + 1} of ${STEP_TITLES.size} · ${STEP_TITLES[step]}"
        Text(label, style = Nah.style(14.sp, 600, 20.sp, color = c.inkMuted), modifier = Modifier.semantics { contentDescription = label })
    }
}

// ---------- 1 · Welcome ----------

@Composable
private fun WelcomeStep(thresholds: Thresholds) {
    val c = Nah.c
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Wordmark(30.sp)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                buildAnnotatedString {
                    append("Bad offer?\nJust say nah")
                    withStyle(SpanStyle(color = c.stop)) { append(".") }
                },
                style = Nah.style(50.sp, 900, 48.sp, (-0.06).em, c.ink),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "nah.meter reads each delivery offer and gives you a one-word answer before the timer runs out. Out loud too, if you want.",
                style = Nah.style(17.sp, 400, 25.sp, color = c.inkMuted),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Example(Verdict.GREEN, "pays your rate")
            Example(Verdict.YELLOW, "pays the hour, long drive")
            Example(Verdict.RED, "not worth your time")
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Point("Your rules: ${Scorer.money(thresholds.minPay)} min, ${Scorer.money(thresholds.minHourly)}/hr, ${Scorer.money(thresholds.minPerKm)}/km")
            Point("It never taps Match for you, and it has no internet access. You decide.")
            Point("No account. Nothing leaves your phone.")
        }
    }
}

/** A small black verdict card: the word and what it means. */
@Composable
private fun Example(v: Verdict, meaning: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nah.card)
            .border(1.dp, Nah.cardEdge, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(NahCopy.word(v), style = Nah.style(40.sp, 900, 40.sp, (-0.07).em, Nah.verdictColor(v)), modifier = Modifier.weight(1f))
        Text(meaning, style = Nah.style(17.sp, 600, 22.sp, color = Nah.cardSoft), modifier = Modifier.padding(bottom = 4.dp))
    }
}

/** A list point marked with the square full stop. */
@Composable
private fun Point(text: String) {
    val c = Nah.c
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 7.dp)) { Stop(8.dp) }
        Text(text, style = Nah.style(15.sp, 400, 22.sp, color = if (c.dark) Color(0xFFD4D4D4) else c.ink))
    }
}

// ---------- 2 · Your apps ----------

@Composable
private fun AppsStep(settings: AppSettings) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val c = Nah.c
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Which apps do you drive for?", style = Nah.title.copy(color = c.ink), modifier = Modifier.semantics { heading() })
        Text(
            "They show on Home so you can open them in one tap. nah.meter gives verdicts for Uber Eats today.",
            style = Nah.style(15.sp, 400, 22.sp, color = c.inkMuted),
        )
        NahGroup {
            DeliveryApps.ALL.forEachIndexed { i, app ->
                if (i > 0) NahDivider()
                val picked = app.verdicts || app.id in settings.apps
                NahRow(app.name, if (app.verdicts) "Verdicts on" else "Opens from Home · verdicts coming soon") {
                    NahCheck(picked, enabled = !app.verdicts, label = app.name) { on ->
                        scope.launch { repo.setApps(if (on) settings.apps + app.id else settings.apps - app.id) }
                    }
                }
            }
            NahDivider()
            NahRow("Instacart and more", "Not yet") {
                NahCheck(false, enabled = false, label = "Instacart and more") {}
            }
        }
    }
}

/** A checkbox: an ink square with a tick when on, outlined when off. */
@Composable
private fun NahCheck(checked: Boolean, enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = Nah.c
    Box(
        Modifier.size(44.dp).alpha(if (enabled) 1f else 0.5f)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                .background(if (checked) c.ink else Color.Transparent)
                .border(2.dp, if (checked) c.ink else c.controlBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(NahIcons.check, contentDescription = null, tint = c.onInk, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------- 3 · Your rules ----------

/** The PRD's example offer, shown wherever the card needs something to be scored on. */
internal val EXAMPLE_OFFER = Offer(BigDecimal("6.07"), 23, BigDecimal("11.5"))

@Composable
private fun RulesStep(thresholds: Thresholds) {
    val c = Nah.c
    val preview = Scorer.score(EXAMPLE_OFFER, thresholds)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Set your rules", style = Nah.title.copy(color = c.ink), modifier = Modifier.semantics { heading() })
        Text(
            "Pay first, then $/hr, then $/km. You can change these any time under Rules.",
            style = Nah.style(15.sp, 400, 22.sp, color = c.inkMuted),
        )
        NahGroup { RuleSteppers(thresholds, showBuffer = false) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Your rules on a real offer")
            VerdictCard(
                preview.verdict, preview.hourly?.let { "$${it.toPlainString()}" }, NahCopy.cardReason(preview, thresholds),
                spoken = NahCopy.spoken(preview, thresholds), kmChip = NahCopy.kmChip(preview),
            )
            Text(
                "$${EXAMPLE_OFFER.pay.toPlainString()} · ${EXAMPLE_OFFER.minutes} min · ${EXAMPLE_OFFER.km.toPlainString()} km",
                style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted),
            )
        }
    }
}

// ---------- 4 · Permissions ----------

@Composable
private fun PermissionsStep() {
    val c = Nah.c
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Let it read offers", style = Nah.title.copy(color = c.ink), modifier = Modifier.semantics { heading() })
        Text(
            "Go down the list. Each one has a button that opens the right page; come back here when it's on.",
            style = Nah.style(15.sp, 400, 22.sp, color = c.inkMuted),
        )
        PermissionSteps()
        Text(
            "No internet permission. Offers stay on your phone and nothing is ever tapped for you.",
            style = Nah.style(13.sp, 400, 18.sp, color = c.inkMuted),
        )
    }
}

// ---------- 5 · Test it ----------

@Composable
private fun TestStep(settings: AppSettings) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val c = Nah.c
    val preview = Scorer.score(EXAMPLE_OFFER, settings.thresholds)
    // Works without Accessibility: the card is drawn here, and spoken through nah.meter's own voice.
    val voice = remember { Voice(ctx) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Test it", style = Nah.title.copy(color = c.ink), modifier = Modifier.semantics { heading() })
        Text(
            "This is how a verdict sits above an offer. The offer below is a stand-in, not the real app.",
            style = Nah.style(15.sp, 400, 22.sp, color = c.inkMuted),
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VerdictCard(
                preview.verdict, preview.hourly?.let { "$${it.toPlainString()}" }, NahCopy.cardReason(preview, settings.thresholds),
                spoken = NahCopy.spoken(preview, settings.thresholds), kmChip = NahCopy.kmChip(preview),
            )
            StandInOffer()
        }
        NahGroup {
            NahRow("Read verdicts out loud", "\"${NahCopy.speech(preview, settings.voiceMode)}\"") {
                NahSwitch(settings.voice, { v -> scope.launch { repo.setVoice(v) } }, "Read verdicts out loud")
            }
        }
        NahButton(
            "Play the test verdict",
            { voice.say(NahCopy.speech(preview, settings.voiceMode), settings) },
            modifier = Modifier.fillMaxWidth(), kind = NahButtonKind.Secondary,
        )
    }
}

/** A plain stand-in for Uber's offer card: it only shows where the verdict sits. */
@Composable
private fun StandInOffer() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1C1C1E)).padding(16.dp)
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Delivery", style = Nah.style(14.sp, 600, 20.sp, color = Color(0xFFA8A8A8)))
        Text("$${EXAMPLE_OFFER.pay.toPlainString()}", style = Nah.style(32.sp, 800, 36.sp, (-0.03).em, Color.White))
        Text(
            "${EXAMPLE_OFFER.minutes} min (${EXAMPLE_OFFER.km.toPlainString()} km) total",
            style = Nah.style(15.sp, 500, 22.sp, color = Color.White),
        )
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFDEDEDE)),
            contentAlignment = Alignment.Center,
        ) { Text("Match", style = Nah.style(16.sp, 700, 22.sp, color = Color.Black)) }
    }
}
