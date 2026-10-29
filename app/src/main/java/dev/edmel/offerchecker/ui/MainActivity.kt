package dev.edmel.offerchecker.ui

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.data.AccountRepository
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.Appearance
import dev.edmel.offerchecker.data.LEGAL_VERSION
import dev.edmel.offerchecker.data.SettingsRepository
import dev.edmel.offerchecker.service.CrashLog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        // Opened by the sign-in email's link.
        if (savedInstanceState == null) SignInLink.tokenFrom(intent)?.let { SignInLink.pending.value = it }
        // Mono (dark) until the Appearance setting is read; the bars follow the theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
        )
        setContent {
            val repo = remember { SettingsRepository(applicationContext) }
            val s by repo.settings.collectAsState(initial = AppSettings())
            val dark = when (s.appearance) {
                Appearance.MONO -> true
                Appearance.DAYLIGHT -> false
                Appearance.PHONE -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(AndroidColor.BLACK) else SystemBarStyle.light(AndroidColor.WHITE, AndroidColor.WHITE)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            NahTheme(dark) {
                val c = Nah.c
                val scheme = if (c.dark) darkColorScheme() else lightColorScheme()
                MaterialTheme(
                    colorScheme = scheme.copy(
                        background = c.ground, surface = c.surface, onSurface = c.ink,
                        primary = c.ink, onPrimary = c.onInk, surfaceContainerHigh = c.surfaceRaised,
                    ),
                ) {
                    CompositionLocalProvider(LocalContentColor provides c.ink) { App() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SignInLink.tokenFrom(intent)?.let { SignInLink.pending.value = it }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", NahIcons.status),
    HISTORY("History", NahIcons.list),
    RULES("Rules", NahIcons.sliders),
    SETTINGS("Settings", NahIcons.cog),
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = null)
    val accountRepo = remember { AccountRepository(ctx) }
    val account by accountRepo.state.collectAsState(initial = null)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsPage by rememberSaveable { mutableIntStateOf(0) }
    // Settings → Run setup again.
    var rerunSetup by rememberSaveable { mutableStateOf(false) }
    // Back on Setup's first page leaves a re-run (its later pages go back a page themselves).
    BackHandler(enabled = rerunSetup) { rerunSetup = false }

    FinishSignIn()
    // The licence dates change on edmel.dev (a payment, free months): re-check when the app opens.
    LaunchedEffect(account?.signedIn) {
        if (account?.signedIn == true) runCatching { accountRepo.refreshLicence() }
    }

    Box(Modifier.fillMaxSize().background(Nah.c.ground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val s = settings ?: return@Box
        val a = account ?: return@Box
        if (!s.onboarded || rerunSetup) {
            SetupScreen(s, onFinish = {
                rerunSetup = false
                if (!s.onboarded) scope.launch { repo.setOnboarded() }
            })
            return@Box
        }
        // Anyone who hasn't agreed to the current terms is asked on every launch until they do.
        if (s.termsAccepted < LEGAL_VERSION) {
            AgreeScreen(onAgree = { scope.launch { repo.setTermsAccepted(LEGAL_VERSION) } })
            return@Box
        }
        // The trial and the licence need an account; offers still never leave the phone.
        if (!a.signedIn) {
            SignInScreen(a)
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (Tab.entries[tab]) {
                    Tab.HOME -> HomeScreen(
                        s,
                        onEditRules = { tab = Tab.RULES.ordinal },
                        onOpenPermissions = { settingsPage = SettingsPage.PERMISSIONS.ordinal; tab = Tab.SETTINGS.ordinal },
                        account = a,
                        onOpenAccount = { settingsPage = SettingsPage.ACCOUNT.ordinal; tab = Tab.SETTINGS.ordinal },
                    )
                    Tab.HISTORY -> LogScreen(s.thresholds)
                    Tab.RULES -> ThresholdsScreen(s)
                    Tab.SETTINGS -> SettingsScreen(s, SettingsPage.entries[settingsPage], { settingsPage = it.ordinal }, { rerunSetup = true })
                }
            }
            TabBar(tab) { tab = it }
        }
    }
}

/** TabBar: four tabs on `ground` under a hairline; the current one in `ink`. */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        val c = Nah.c
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().background(c.ground).padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Tab.entries.forEachIndexed { i, t ->
                val color = if (i == selected) c.ink else c.inkMuted
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .semantics { this.selected = i == selected }
                        .clickable(role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(t.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Text(t.label, style = Nah.style(12.sp, 600, 16.sp, color = color))
                }
            }
        }
    }
}
