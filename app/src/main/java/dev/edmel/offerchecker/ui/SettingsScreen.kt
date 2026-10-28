package dev.edmel.offerchecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.BadgeSize
import dev.edmel.offerchecker.data.SettingsRepository
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }
    val scope = rememberCoroutineScope()
    val s by repo.settings.collectAsState(initial = null)
    val settings = s ?: return
    val t = settings.thresholds

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Checks run in order; the first that fails sets the colour. Exactly meeting a target counts as a pass.",
            style = MaterialTheme.typography.bodyMedium,
        )
        DecimalField("1 · Minimum pay ($) — else red", t.minPay) { scope.launch { repo.setMinPay(it) } }
        DecimalField("2 · Minimum $/hr — else red", t.minHourly) { scope.launch { repo.setMinHourly(it) } }
        DecimalField("3 · Minimum $/km — else yellow", t.minPerKm) { scope.launch { repo.setMinPerKm(it) } }
        DecimalField(
            "Restaurant wait buffer (min, added to Uber's time)",
            BigDecimal(t.waitBufferMin),
            integer = true,
        ) { scope.launch { repo.setWaitBuffer(it.toInt()) } }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vibrate on each offer", style = MaterialTheme.typography.titleMedium)
                Text("Green 1 short · yellow 2 short · red 1 long", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.vibration, onCheckedChange = { v -> scope.launch { repo.setVibration(v) } })
        }

        Text("Badge size", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeSize.entries.forEach { size ->
                FilterChip(
                    selected = settings.badgeSize == size,
                    onClick = { scope.launch { repo.setBadgeSize(size) } },
                    label = { Text(size.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        Text("Drag the badge to move it; tap it to dismiss.", style = MaterialTheme.typography.bodySmall)

        TextButton(onClick = { scope.launch { repo.resetThresholds() } }) {
            Text("Reset targets to \$5.50 · \$20/hr · \$1/km · 0 min")
        }
        Text("Defaults: ${defaultsLabel(AppSettings())}", style = MaterialTheme.typography.bodySmall)
    }
}

private fun defaultsLabel(d: AppSettings) =
    "min \$${d.thresholds.minPay}, \$${d.thresholds.minHourly}/hr, \$${d.thresholds.minPerKm}/km, buffer ${d.thresholds.waitBufferMin} min"

/** Text field that saves when the input is a valid non-negative number. */
@Composable
private fun DecimalField(label: String, value: BigDecimal, integer: Boolean = false, onValid: (BigDecimal) -> Unit) {
    var text by remember { mutableStateOf(value.toPlainString()) }
    // Follow outside changes (e.g. reset) unless the field already means the same number.
    LaunchedEffect(value) {
        if (text.toBigDecimalOrNull()?.compareTo(value) != 0) text = value.toPlainString()
    }
    val parsed = text.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && (!integer || it.stripTrailingZeros().scale() <= 0) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toBigDecimalOrNull()
                ?.takeIf { v -> v.signum() >= 0 && (!integer || v.stripTrailingZeros().scale() <= 0) && v < BigDecimal(1000) }
                ?.let(onValid)
        },
        label = { Text(label) },
        isError = parsed == null,
        supportingText = { if (parsed == null) Text(if (integer) "Whole minutes, 0 or more" else "A number, 0 or more") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
