package dev.edmel.offerchecker.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.edmel.offerchecker.data.AccountRepository
import dev.edmel.offerchecker.data.AccountState
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.Appearance
import dev.edmel.offerchecker.data.SettingsRepository
import dev.edmel.offerchecker.service.ServiceState
import kotlinx.coroutines.launch

/** The pages Settings can show; Home's "Fix in Settings" opens [PERMISSIONS] directly. */
enum class SettingsPage { MAIN, PERMISSIONS, APPS, VOICE, TERMS, PRIVACY, LICENCES, ACCOUNT }

@Composable
fun SettingsScreen(
    settings: AppSettings, page: SettingsPage, onPage: (SettingsPage) -> Unit, onRunSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = page != SettingsPage.MAIN) { onPage(SettingsPage.MAIN) }
    val ctx = LocalContext.current
    val account by remember { AccountRepository(ctx) }.state.collectAsState(initial = AccountState())
    when (page) {
        SettingsPage.ACCOUNT -> AccountPage(account, onBack = { onPage(SettingsPage.MAIN) }, modifier = modifier)
        SettingsPage.PERMISSIONS -> PermissionsScreen(onBack = { onPage(SettingsPage.MAIN) }, modifier = modifier)
        SettingsPage.APPS -> DeliveryAppsScreen(settings, onBack = { onPage(SettingsPage.MAIN) }, modifier = modifier)
        SettingsPage.VOICE -> VoiceScreen(settings, onBack = { onPage(SettingsPage.MAIN) }, modifier = modifier)
        SettingsPage.TERMS -> LegalScreen(LegalDoc.TERMS, onBack = { onPage(SettingsPage.MAIN) }, modifier = modifier)
        SettingsPage.PRIVACY -> LegalScreen(LegalDoc.PRIVACY, onBack = { onPage(SettingsPage.MAIN) }, modifier = modifier)
        SettingsPage.LICENCES -> LegalScreen(LegalDoc.LICENCES, onBack = { onPage(SettingsPage.MAIN) }, modifier = modifier)
        SettingsPage.MAIN -> SettingsMain(settings, account, onPage, onRunSetup, modifier)
    }
}

/** A page's header: a Back link, then the title. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NahButton("Back", onBack, kind = NahButtonKind.Tertiary)
        Text(title, style = Nah.title.copy(color = Nah.c.ink))
    }
}

@Composable
private fun SettingsMain(settings: AppSettings, account: AccountState, onPage: (SettingsPage) -> Unit, onRunSetup: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val running by ServiceState.running.collectAsState()
    val c = Nah.c

    // The same four checks the Permissions page lists.
    val todo = remember(refresh, running) {
        listOf(
            SystemSetup.isAccessibilityEnabled(ctx) && running,
            SystemSetup.batteryUnrestricted(ctx),
            SystemSetup.notificationsAllowed(ctx),
            SystemSetup.uberInstalled(ctx).isNotEmpty(),
        ).count { !it }
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = Nah.title.copy(color = c.ink))

        Column {
            SectionLabel("Account")
            NahGroup {
                LinkRow(account.email ?: "Account", licenceLine(account.licence), licenceAlert(account.licence)) { onPage(SettingsPage.ACCOUNT) }
            }
        }

        Column {
            SectionLabel("Health")
            NahGroup {
                LinkRow("Permissions", if (todo == 0) "All on" else "$todo to fix", todo > 0) { onPage(SettingsPage.PERMISSIONS) }
                NahDivider()
                LinkRow("Run setup again", null, false) { onRunSetup() }
            }
        }

        Column {
            SectionLabel("Verdicts")
            NahGroup {
                SegmentedRow(
                    "Verdict card", null,
                    listOf("Full", "Compact"), if (settings.compactBadge) 1 else 0,
                ) { i -> scope.launch { repo.setCompactBadge(i == 1) } }
                NahDivider()
                LinkRow("Voice", if (settings.voice) "On · ${settings.voiceVolume}%" else "Off", false) {
                    onPage(SettingsPage.VOICE)
                }
                NahDivider()
                NahRow("Haptics", "bet short · eh double · nah long") {
                    NahSwitch(settings.vibration, { v -> scope.launch { repo.setVibration(v) } }, "Haptics")
                }
                NahDivider()
                NahRow("Show a test verdict") {
                    NahButton(
                        "Show",
                        {
                            if (!ServiceState.showTestBadge()) Toast.makeText(ctx, "Turn on Accessibility first", Toast.LENGTH_SHORT).show()
                        },
                        kind = NahButtonKind.Secondary, small = true,
                    )
                }
            }
        }

        Column {
            SectionLabel("Look")
            NahGroup {
                SegmentedRow(
                    "Appearance", "Mono is dark, Daylight is light",
                    listOf("Mono", "Daylight", "Match phone"), settings.appearance.ordinal,
                ) { i -> scope.launch { repo.setAppearance(Appearance.entries[i]) } }
            }
        }

        Column {
            SectionLabel("Apps")
            NahGroup {
                val count = (settings.apps + DeliveryApps.UBER.id).size
                LinkRow("Delivery apps", "$count app${if (count == 1) "" else "s"}", false) { onPage(SettingsPage.APPS) }
            }
        }

        Column {
            SectionLabel("About")
            NahGroup {
                NahRow("nah.meter ${appVersion(ctx)}", "Offers never leave your phone · not affiliated with Uber")
                NahDivider()
                LegalLink(LegalDoc.TERMS) { onPage(SettingsPage.TERMS) }
                NahDivider()
                LegalLink(LegalDoc.PRIVACY) { onPage(SettingsPage.PRIVACY) }
                NahDivider()
                LegalLink(LegalDoc.LICENCES) { onPage(SettingsPage.LICENCES) }
            }
        }
    }
}

/** A row that opens another page: title, a line under it (red when something needs doing), a chevron. */
@Composable
private fun LinkRow(title: String, desc: String?, alert: Boolean, onClick: () -> Unit) {
    val c = Nah.c
    Box(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        NahRow(title, desc, descColor = if (alert) c.nahInk else c.inkMuted) {
            Icon(NahIcons.chevron, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** A row with a title and line, and a segmented control under them. */
@Composable
private fun SegmentedRow(title: String, desc: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Nah.style(16.sp, 600, 22.sp, color = Nah.c.ink))
            if (desc != null) Text(desc, style = Nah.style(14.sp, 400, 20.sp, color = Nah.c.inkMuted))
        }
        NahSegmented(options, selected, onSelect)
    }
}

/** Which delivery apps show on Home. Uber Eats is always there: it's the one nah.meter reads. */
@Composable
private fun DeliveryAppsScreen(settings: AppSettings, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val c = Nah.c
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BackHeader("Delivery apps", onBack)
        Text(
            "nah.meter reads offers from Uber Eats only, for now.",
            style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted),
        )
        NahGroup {
            DeliveryApps.ALL.forEachIndexed { i, app ->
                if (i > 0) NahDivider()
                if (app.verdicts) {
                    NahRow(app.name) {
                        Text("Always on", style = Nah.style(14.sp, 600, 20.sp, color = c.inkMuted))
                    }
                } else {
                    NahRow(app.name, "Verdicts soon") {
                        NahSwitch(
                            app.id in settings.apps,
                            { on -> scope.launch { repo.setApps(if (on) settings.apps + app.id else settings.apps - app.id) } },
                            app.name,
                        )
                    }
                }
            }
        }
    }
}
