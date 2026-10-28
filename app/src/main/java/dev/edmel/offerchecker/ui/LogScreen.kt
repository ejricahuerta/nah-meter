package dev.edmel.offerchecker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.core.Offer
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.OfferCsv
import dev.edmel.offerchecker.data.OfferDao
import dev.edmel.offerchecker.data.OfferDatabase
import dev.edmel.offerchecker.data.OfferEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(thresholds: Thresholds, modifier: Modifier = Modifier) {
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
    fun count(v: Verdict) = today.count { it.verdict == v.name }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today", style = Wi.title, modifier = Modifier.weight(1f))
                WiButton("Export CSV", {
                    export.launch("offers-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv")
                }, kind = WiButtonKind.Tertiary, enabled = offers.isNotEmpty())
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Wi.surface)
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (today.isEmpty()) {
                    Text("No offers yet today", style = Wi.bodyStrong)
                    Text("Each offer Worth It reads is listed here.", style = Wi.label)
                } else {
                    val avg = "%.2f".format(Locale.US, today.map { it.hourly }.average())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Average offer · ${today.size} offer${if (today.size == 1) "" else "s"}",
                            style = Wi.style(14.sp, 500, 20.sp, color = Wi.inkMuted),
                        )
                        Text(
                            buildAnnotatedString {
                                append("$$avg")
                                withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.W600, letterSpacing = 0.em, color = Wi.inkMuted)) {
                                    append("/hr")
                                }
                            },
                            style = Wi.style(44.sp, 700, 48.sp, (-0.03).em),
                        )
                    }
                    // Share of GO / MAYBE / SKIP today.
                    Row(
                        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp))
                            .clearAndSetSemantics { },
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        listOf(Verdict.GREEN, Verdict.YELLOW, Verdict.RED).forEach { v ->
                            val n = count(v)
                            if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxSize().background(verdictColor(v)))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Verdict.GREEN, Verdict.YELLOW, Verdict.RED).forEach { v ->
                            if (count(v) > 0) VerdictChip(v, "${count(v)} ${Wi.word(v)}")
                        }
                    }
                }
            }
        }
        if (offers.isNotEmpty()) {
            item {
                WiGroup {
                    offers.forEachIndexed { i, e ->
                        if (i > 0) WiDivider()
                        OfferRow(e, thresholds, today = e.time >= startOfDay)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("The last ${OfferDao.MAX_OFFERS} offers are kept.", style = Wi.label)
                    WiButton("Clear log", { confirmClear = true }, kind = WiButtonKind.Tertiary)
                }
            }
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

/** OfferRow: time, the offer's numbers, its rates (or the target it missed) and a verdict chip. */
@Composable
private fun OfferRow(e: OfferEntry, thresholds: Thresholds, today: Boolean) {
    val verdict = runCatching { Verdict.valueOf(e.verdict) }.getOrDefault(Verdict.GREY)
    val time = remember(e.time, today) {
        SimpleDateFormat(if (today) "h:mm a" else "MMM d", Locale.getDefault()).format(Date(e.time))
    }
    val hr = "$" + "%.2f".format(Locale.US, e.hourly) + "/hr"
    // A SKIP shows the target it missed, when today's targets still give the same verdict.
    val missed = if (verdict == Verdict.RED) {
        Scorer.score(
            Offer(BigDecimal.valueOf(e.pay), e.minutes, BigDecimal.valueOf(e.km)),
            thresholds.copy(waitBufferMin = 0),
        ).takeIf { it.verdict == verdict }?.failedRule?.removeSuffix(" target")?.replaceFirstChar { it.lowercase() }
    } else null
    val rates = when {
        missed != null && missed.endsWith("minimum") -> missed
        missed != null -> "$hr · $missed"
        else -> "$hr · ${e.perKm?.let { "$" + "%.2f".format(Locale.US, it) } ?: "—"}/km"
    }
    val nums = "$" + "%.2f".format(Locale.US, e.pay) + " · ${e.minutes} min · ${e.km} km" + if (e.stacked) " · ×2" else ""
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$time, ${Wi.word(verdict)}, $nums, $rates" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(time, style = Wi.style(13.sp, 500, 18.sp, color = Wi.inkMuted), modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nums, style = Wi.style(16.sp, 600, 22.sp))
            Text(rates, style = Wi.style(14.sp, 400, 20.sp, color = Wi.inkMuted))
        }
        VerdictChip(verdict)
    }
}
