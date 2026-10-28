package dev.edmel.offerchecker.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import dev.edmel.offerchecker.data.SettingsRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mono (dark) by default, Daylight when the phone is in light mode; bars follow.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.WHITE, AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.WHITE, AndroidColor.BLACK),
        )
        setContent {
            NahTheme {
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
}

private enum class Tab(val label: String, val icon: ImageVector) {
    STATUS("Status", NahIcons.status),
    THRESHOLDS("Thresholds", NahIcons.sliders),
    LOG("Log", NahIcons.list),
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = null)
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(Nah.c.ground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val s = settings ?: return@Box
        if (!s.onboarded) {
            OnboardingScreen(s.thresholds, onSetUp = { scope.launch { repo.setOnboarded() } })
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (Tab.entries[tab]) {
                    Tab.STATUS -> StatusScreen(s)
                    Tab.THRESHOLDS -> ThresholdsScreen(s)
                    Tab.LOG -> LogScreen(s.thresholds)
                }
            }
            TabBar(tab) { tab = it }
        }
    }
}

/** TabBar: three tabs on `ground` under a hairline; the current one in `ink`. */
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
