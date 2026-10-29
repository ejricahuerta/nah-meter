package dev.edmel.offerchecker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.OfferDatabase
import dev.edmel.offerchecker.data.OfferEntry
import dev.edmel.offerchecker.data.SettingsRepository
import dev.edmel.offerchecker.service.ServiceState
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Home: the power button (reading on or paused), today's offers, the delivery apps to open, and
 * the rules in one line. [onEditRules] goes to the Rules tab, [onOpenPermissions] to Settings.
 */
@Composable
fun HomeScreen(
    settings: AppSettings,
    onEditRules: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    // Re-read system state whenever the user comes back from another app or a settings page.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val running by ServiceState.running.collectAsState()
    val a11y = remember(refresh, running) { SystemSetup.isAccessibilityEnabled(ctx) }
    val reading = a11y && running
    val on = reading && settings.readingEnabled

    val offers by remember { OfferDatabase.get(ctx).offers().observeAll() }.collectAsState(initial = emptyList())
    val startOfDay = remember(offers) { startOfToday() }
    val today = offers.filter { it.time >= startOfDay }

    val c = Nah.c
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Wordmark(24.sp)

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                !a11y -> StatusPill(false, "Accessibility is off · not reading")
                !running -> StatusPill(false, "Not running · turn Accessibility off and on")
                !settings.readingEnabled -> StatusPill(false, "Paused · no verdicts")
                else -> StatusPill(true, "Reading offers")
            }
            PowerButton(
                on = settings.readingEnabled,
                label = when {
                    !reading -> "Off"
                    settings.readingEnabled -> "On"
                    else -> "Paused"
                },
                active = on,
                onChange = { v -> scope.launch { repo.setReadingEnabled(v) } },
            )
            if (!a11y || !running) NahButton("Fix in Settings", onOpenPermissions, kind = NahButtonKind.Secondary, small = true)
        }

        Column {
            SectionLabel("Today")
            Overview(today, Modifier.alpha(if (settings.readingEnabled) 1f else 0.5f))
        }

        Column {
            SectionLabel("Open an app")
            AppTiles(settings, refresh, verdictsOn = on)
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val t = settings.thresholds
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Your rules", style = Nah.style(13.sp, 500, 18.sp, color = c.inkMuted))
                Text(
                    "${Scorer.money(t.minPay)} min · ${Scorer.money(t.minHourly)}/hr · ${Scorer.money(t.minPerKm)}/km" +
                        (t.maxKm?.let { " · ${Scorer.km(it)} km max" } ?: "") +
                        (if (t.extraKmPercent > 0) " · +${t.extraKmPercent}% km" else ""),
                    style = Nah.style(16.sp, 600, 22.sp, color = c.ink),
                )
            }
            NahButton("Edit", onEditRules, kind = NahButtonKind.Tertiary)
        }
    }
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** The big on/off button: black with a green ring and glow while reading, outlined when not. */
@Composable
private fun PowerButton(on: Boolean, label: String, active: Boolean, onChange: (Boolean) -> Unit) {
    val c = Nah.c
    Box(
        Modifier.size(140.dp)
            .then(if (active) Modifier.shadow(24.dp, CircleShape, ambientColor = Nah.bet, spotColor = Nah.bet) else Modifier)
            .clip(CircleShape)
            .background(if (active) Nah.card else Color.Transparent)
            .border(4.dp, if (active) Nah.bet else c.controlBorder, CircleShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = "Read offers" },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Nah.style(28.sp, 900, 30.sp, (-0.04).em, if (active) Color.White else c.ink))
    }
}

/** Today's numbers: offers read, average $/hr, the bet · eh · nah split, and the best offer. */
@Composable
private fun Overview(today: List<OfferEntry>, modifier: Modifier = Modifier) {
    val c = Nah.c
    NahGroup(modifier) {
        if (today.isEmpty()) {
            NahRow("No offers yet today")
            return@NahGroup
        }
        fun count(v: Verdict) = today.count { it.verdict == v.name }
        fun perHour(v: Double) = buildAnnotatedString {
            append("$" + "%.2f".format(Locale.US, v))
            withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, letterSpacing = 0.em, color = c.inkMuted)) { append("/hr") }
        }
        val split = buildAnnotatedString {
            withStyle(SpanStyle(color = c.betInk)) { append("${count(Verdict.GREEN)}") }
            withStyle(SpanStyle(color = c.inkMuted)) { append(" · ") }
            withStyle(SpanStyle(color = c.ehInk)) { append("${count(Verdict.YELLOW)}") }
            withStyle(SpanStyle(color = c.inkMuted)) { append(" · ") }
            withStyle(SpanStyle(color = c.nahInk)) { append("${count(Verdict.RED)}") }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Offers", AnnotatedString("${today.size}"), Modifier.weight(1f))
            Stat("Avg offer", perHour(today.map { it.hourly }.average()), Modifier.weight(1f))
        }
        NahDivider()
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("bet · eh · nah", split, Modifier.weight(1f))
            Stat("Best offer", perHour(today.maxOf { it.hourly }), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: AnnotatedString, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = Nah.style(13.sp, 500, 18.sp, color = Nah.c.inkMuted))
        Text(value, style = Nah.style(24.sp, 800, 28.sp, (-0.03).em, Nah.c.ink))
    }
}

/** The delivery apps picked in Settings, two to a row, then a "More apps" tile that isn't built yet. */
@Composable
private fun AppTiles(settings: AppSettings, refresh: Int, verdictsOn: Boolean) {
    val ctx = LocalContext.current
    val apps = DeliveryApps.ALL.filter { it.verdicts || it.id in settings.apps }
    val tiles = apps.map { app ->
        val installed = remember(refresh) { SystemSetup.installed(ctx, app.packages) }
        val subtitle = when {
            !installed -> "Not installed"
            app.verdicts -> if (verdictsOn) "Verdicts on" else "Paused"
            else -> "Verdicts soon"
        }
        Tile(app.name, app.mark, subtitle, enabled = installed, live = installed && app.verdicts && verdictsOn) {
            if (!SystemSetup.openApp(ctx, app.packages)) Toast.makeText(ctx, "Couldn't open ${app.name}", Toast.LENGTH_SHORT).show()
        }
    } + Tile("More apps", "+", "Not yet", enabled = false, live = false) {}

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { AppTile(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private class Tile(
    val name: String, val mark: String, val subtitle: String, val enabled: Boolean, val live: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun AppTile(tile: Tile, modifier: Modifier = Modifier) {
    val c = Nah.c
    Row(
        modifier.heightIn(min = 72.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
            .clickable(enabled = tile.enabled, role = Role.Button, onClick = tile.onClick)
            .alpha(if (tile.enabled) 1f else 0.5f)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
            Text(tile.mark, style = Nah.style(18.sp, 900, 20.sp, color = c.ink))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tile.name, style = Nah.style(15.sp, 700, 20.sp, color = c.ink), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tile.live) Box(Modifier.size(8.dp).clip(CircleShape).background(Nah.bet))
                Text(tile.subtitle, style = Nah.style(13.sp, 400, 18.sp, color = c.inkMuted), maxLines = 1)
            }
        }
    }
}
