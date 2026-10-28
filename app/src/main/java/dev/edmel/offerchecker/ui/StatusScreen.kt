package dev.edmel.offerchecker.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.service.ServiceState
import java.text.DateFormat
import java.util.Date

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    // Re-read system state whenever the user comes back from a settings page.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val running by ServiceState.running.collectAsState()
    val last by ServiceState.lastRead.collectAsState()
    val uberEvents by ServiceState.uberEvents.collectAsState()
    val lastScreen by ServiceState.lastScreen.collectAsState()
    val windows by ServiceState.windows.collectAsState()
    val log by ServiceState.log.collectAsState()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val a11y = remember(refresh, running) { SystemSetup.isAccessibilityEnabled(ctx) }
    val notifs = remember(refresh) { SystemSetup.notificationsAllowed(ctx) }
    val battery = remember(refresh) { SystemSetup.batteryUnrestricted(ctx) }
    val uber = remember(refresh) { SystemSetup.uberInstalled(ctx) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Offer Checker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (running) "Reader is on. Go online in Uber and wait for an offer."
            else "Finish the steps below to turn the reader on.",
            style = MaterialTheme.typography.bodyLarge,
        )

        if (Build.VERSION.SDK_INT >= 33 && !a11y) {
            StepCard(
                ok = false,
                title = "Allow restricted settings (Android 13+)",
                body = "Needed once for sideloaded apps before Accessibility can be turned on. " +
                    "On the App info page tap ⋮ (top right) → Allow restricted settings. " +
                    "If you don't see ⋮, try step 2 first, then come back here.",
                action = "Open App info",
            ) { SystemSetup.openAppInfo(ctx) }
        }
        StepCard(
            ok = a11y && running,
            title = "Accessibility: Offer Checker",
            body = when {
                a11y && running -> "On. Reading Uber offers."
                a11y -> "Enabled but not running yet. Toggle it off and on again."
                else -> "Settings → Accessibility → Installed apps → Offer Checker → On."
            },
            action = "Open Accessibility",
        ) { SystemSetup.openAccessibility(ctx) }
        StepCard(
            ok = running,
            title = "Badge overlay",
            body = if (running) "Ready. Drawn by the accessibility service, no extra permission needed."
            else "Available once Accessibility is on.",
        )
        StepCard(
            ok = battery,
            title = "Battery: Unrestricted",
            body = if (battery) "Android won't stop the reader mid-shift." else "So Android doesn't stop the reader mid-shift.",
            action = "Set Unrestricted",
        ) { SystemSetup.requestUnrestrictedBattery(ctx) }
        if (SystemSetup.isSamsung || SystemSetup.isXiaomi) {
            StepCard(
                ok = null,
                title = if (SystemSetup.isSamsung) "Samsung: Never sleeping apps" else "Xiaomi: Autostart + No restrictions",
                body = if (SystemSetup.isSamsung) "Battery → Background usage limits → Never sleeping apps → add Offer Checker."
                else "Turn on Autostart, then set battery saver to \"No restrictions\" for Offer Checker.",
                action = "Open",
            ) { SystemSetup.openBrandBattery(ctx) }
        }
        StepCard(
            ok = notifs,
            title = "Notifications",
            body = "For the \"Offer checker on\" notice.",
            action = if (notifs) null else "Allow",
        ) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else SystemSetup.openNotificationSettings(ctx)
        }
        StepCard(
            ok = uber.isNotEmpty(),
            title = "Uber app",
            body = if (uber.isNotEmpty()) "Found: ${uber.joinToString()}." else "No Uber app (Uber Eats or Uber Driver) found on this phone.",
            action = if (uber.isNotEmpty()) "Open Uber" else null,
        ) { SystemSetup.openUber(ctx) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (!ServiceState.showTestBadge()) {
                    Toast.makeText(ctx, "Turn on Accessibility first", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Test badge") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Last offer read", style = MaterialTheme.typography.titleMedium)
                val l = last
                if (l == null) {
                    Text("None yet.")
                } else {
                    val e = l.evaluation
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(verdictColor(e.verdict), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${e.ratesText}${e.failedRule?.let { " · $it" } ?: ""}",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    e.offer?.let { o ->
                        Text("$${o.pay.toPlainString()} · ${e.effectiveMinutes} min · ${o.km.toPlainString()} km${if (o.stacked) " · ×2" else ""}")
                    }
                    Text(DateFormat.getTimeInstance().format(Date(l.time)), style = MaterialTheme.typography.bodySmall)
                    Text("Raw text read:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        l.raw.joinToString("\n").ifEmpty { "(none)" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                val diag = buildString {
                    appendLine("Offer Checker ${appVersion(ctx)}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
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
                Text(
                    if (uberEvents == 0) "No events from Uber yet. With the reader on, open the Uber app: this number should go up."
                    else "The log keeps what the reader saw around recent offers. If an offer showed no badge, tap Copy and send this to the developer.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(diag.trim(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Offer Checker diagnostics", diag))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy diagnostics") }
            }
        }
        Text(
            "Read-only: never taps in Uber, no internet permission, nothing leaves your phone.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun appVersion(ctx: android.content.Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.GREEN -> Color(0xFF1E8E3E)
    Verdict.YELLOW -> Color(0xFFF9AB00)
    Verdict.RED -> Color(0xFFD93025)
    Verdict.GREY -> Color(0xFF5F6368)
}

/** One setup step. [ok] null means "can't check, do it by hand". */
@Composable
private fun StepCard(ok: Boolean?, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (ok) { true -> "✓"; false -> "✕"; null -> "•" },
                color = when (ok) { true -> Color(0xFF1E8E3E); false -> Color(0xFFD93025); null -> Color.Gray },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onAction) { Text(action) }
            }
        }
    }
}
