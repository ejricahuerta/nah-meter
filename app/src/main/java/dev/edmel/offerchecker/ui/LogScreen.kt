package dev.edmel.offerchecker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.OfferCsv
import dev.edmel.offerchecker.data.OfferDao
import dev.edmel.offerchecker.data.OfferDatabase
import dev.edmel.offerchecker.data.OfferEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dao = remember { OfferDatabase.get(ctx).offers() }
    val scope = rememberCoroutineScope()
    val offers by dao.observeAll().collectAsState(initial = emptyList())
    var confirmClear by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(OfferCsv.write(dao.all()).toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(ctx, if (ok) "Exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    val startOfDay = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val today = offers.filter { it.time >= startOfDay }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Offer log", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (today.isEmpty()) "No offers today."
            else "Today: ${today.size} offers · average \$${"%.2f".format(Locale.US, today.map { it.hourly }.average())}/hr",
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                export.launch("offers-$stamp.csv")
            }, enabled = offers.isNotEmpty()) { Text("Export CSV") }
            OutlinedButton(onClick = { confirmClear = true }, enabled = offers.isNotEmpty()) { Text("Clear") }
        }
        Text("Last ${OfferDao.MAX_OFFERS} offers are kept.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(offers, key = { it.id }) { OfferRow(it) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the offer log?") },
            text = { Text("This deletes all ${offers.size} logged offers.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch(Dispatchers.IO) { dao.clear() }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OfferRow(e: OfferEntry) {
    val time = remember(e.time) { SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date(e.time)) }
    val verdict = runCatching { Verdict.valueOf(e.verdict) }.getOrDefault(Verdict.GREY)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(verdictColor(verdict), CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "\$${"%.2f".format(Locale.US, e.hourly)}/hr · ${e.perKm?.let { "\$" + "%.2f".format(Locale.US, it) } ?: "—"}/km",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "\$${"%.2f".format(Locale.US, e.pay)} · ${e.minutes} min · ${e.km} km${if (e.stacked) " · ×2" else ""}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(time, style = MaterialTheme.typography.bodySmall)
    }
}
