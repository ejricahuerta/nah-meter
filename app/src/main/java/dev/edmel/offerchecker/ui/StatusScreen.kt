package dev.edmel.offerchecker.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.SettingsRepository
import dev.edmel.offerchecker.service.BadgeOverlay
import dev.edmel.offerchecker.service.ServiceState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(settings: AppSettings, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    // Re-read system state whenever the user comes back from a settings page.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val running by ServiceState.running.collectAsState()
    val last by ServiceState.lastRead.collectAsState()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val a11y = remember(refresh, running) { SystemSetup.isAccessibilityEnabled(ctx) }
    val notifs = remember(refresh) { SystemSetup.notificationsAllowed(ctx) }
    val battery = remember(refresh) { SystemSetup.batteryUnrestricted(ctx) }
    val uber = remember(refresh) { SystemSetup.uberInstalled(ctx) }
    val reading = a11y && running

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Wordmark() }
            WiSwitch(settings.readingEnabled, { v -> scope.launch { repo.setReadingEnabled(v) } }, "Read offers")
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                !reading -> StatusPill(false, "Accessibility is off · not reading")
                !settings.readingEnabled -> StatusPill(false, "Paused · no badges")
                else -> StatusPill(true, "Reading offers")
            }
        }

        Column {
            val l = last
            if (l == null) {
                Text("Last offer", style = Wi.sectionLabel, modifier = Modifier.padding(bottom = 8.dp))
                WiGroup {
                    WiRow(
                        "None read yet",
                        "Go online in Uber and the next offer shows here. Or tap Test badge.",
                    )
                }
            } else {
                val e = l.evaluation
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(l.time))
                Text("Last offer · $time", style = Wi.sectionLabel, modifier = Modifier.padding(bottom = 8.dp))
                VerdictCard(
                    verdict = e.verdict,
                    hourly = e.hourly?.let { "$${it.toPlainString()}" },
                    footLeft = e.offer?.let { o ->
                        "$${o.pay.toPlainString()} · ${e.effectiveMinutes} min · ${o.km.toPlainString()} km" +
                            if (o.stacked) " · ×2" else ""
                    },
                    reason = BadgeOverlay.reason(e).removeSuffix(" target"),
                )
            }
        }

        // Setup steps, in the order the README's install guide gives them.
        val restricted = Build.VERSION.SDK_INT >= 33 && !a11y
        val checkable = listOf(a11y, battery, notifs, uber.isNotEmpty())
        Column {
            Text(
                "Setup · ${checkable.count { it }} of ${checkable.size} done",
                style = Wi.sectionLabel, modifier = Modifier.padding(bottom = 8.dp),
            )
            WiGroup {
                if (restricted) {
                    SetupStep(
                        StepState.Todo, "Allow restricted settings",
                        "Android 13+: App info → ⋮ → Allow restricted settings, then turn on Accessibility",
                        "Open",
                    ) { SystemSetup.openAppInfo(ctx) }
                    WiDivider()
                }
                SetupStep(
                    if (a11y) StepState.Done else StepState.Todo, "Accessibility",
                    when {
                        reading -> "On · offers are read as they appear"
                        a11y -> "On but not running · turn it off and on again"
                        else -> "Off · offers aren't being read"
                    },
                    if (reading) null else "Open",
                ) { SystemSetup.openAccessibility(ctx) }
                WiDivider()
                SetupStep(
                    if (battery) StepState.Done else StepState.Todo, "Battery unrestricted",
                    if (battery) "Android won't pause the reader" else "Restricted · Android may stop the reader",
                    if (battery) null else "Allow",
                ) { SystemSetup.requestUnrestrictedBattery(ctx) }
                if (SystemSetup.isSamsung || SystemSetup.isXiaomi) {
                    WiDivider()
                    SetupStep(
                        StepState.Manual,
                        if (SystemSetup.isSamsung) "Never sleeping (Samsung)" else "Autostart (Xiaomi)",
                        if (SystemSetup.isSamsung) "Add Worth It to Never sleeping apps" else "Turn on Autostart, battery No restrictions",
                        "Open",
                    ) { SystemSetup.openBrandBattery(ctx) }
                }
                WiDivider()
                SetupStep(
                    if (notifs) StepState.Done else StepState.Todo, "Notifications",
                    if (notifs) "On · shows that Worth It is running" else "Off · needed to stay running",
                    if (notifs) null else "Allow",
                ) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else SystemSetup.openNotificationSettings(ctx)
                }
                WiDivider()
                SetupStep(
                    if (uber.isNotEmpty()) StepState.Done else StepState.Todo, "Uber app",
                    if (uber.isNotEmpty()) "Found: ${uber.joinToString()}" else "Uber Driver isn't installed",
                    if (uber.isNotEmpty()) "Open" else null,
                ) { SystemSetup.openUber(ctx) }
            }
        }

        WiButton(
            "Test badge",
            onClick = {
                if (!ServiceState.showTestBadge()) Toast.makeText(ctx, "Turn on Accessibility first", Toast.LENGTH_SHORT).show()
            },
            kind = WiButtonKind.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        Diagnostics(a11y, running)

        Text(
            "Read-only: never taps in Uber. No internet permission; nothing leaves your phone.",
            style = Wi.style(13.sp, 400, 18.sp, color = Wi.inkMuted),
        )
    }
}

/** Collapsed by default: what the reader saw, to copy and send when an offer reads wrong. */
@Composable
private fun Diagnostics(a11y: Boolean, running: Boolean) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val uberEvents by ServiceState.uberEvents.collectAsState()
    val lastScreen by ServiceState.lastScreen.collectAsState()
    val windows by ServiceState.windows.collectAsState()
    val log by ServiceState.log.collectAsState()
    val diag = buildString {
        appendLine("Worth It ${appVersion(ctx)}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Accessibility enabled: $a11y, reader running: $running")
        appendLine("Uber screen events received: $uberEvents")
        appendLine("Windows at last scan: ${windows.joinToString().ifEmpty { "none" }}")
        val ls = lastScreen
        if (ls == null) appendLine("Uber screen text: none read yet")
        else {
            appendLine("Uber screen text at ${DateFormat.getTimeInstance().format(Date(ls.first))}:")
            ls.second.forEach { appendLine("  $it") }
        }
        appendLine("Log (newest first):")
        if (log.isEmpty()) appendLine("  (empty)")
        log.forEach { appendLine("  $it") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Diagnostics", style = Wi.heading, modifier = Modifier.weight(1f))
            WiButton(if (open) "Hide" else "Show", { open = !open }, kind = WiButtonKind.Tertiary)
        }
        if (open) {
            Text(
                "What the reader saw around recent offers. If a badge was wrong, copy this and send it.",
                style = Wi.label,
            )
            WiButton(
                "Copy diagnostics",
                onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Worth It diagnostics", diag))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                },
                kind = WiButtonKind.Secondary, small = true,
            )
            WiGroup {
                Text(
                    diag.trim(), color = Wi.inkMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    lineHeight = 16.sp, modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

private fun appVersion(ctx: android.content.Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

/** Verdict colour as a Compose colour, for small dots and bars. */
fun verdictColor(v: Verdict) = Wi.fill(v).first
