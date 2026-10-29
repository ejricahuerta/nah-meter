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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.core.Check
import dev.edmel.offerchecker.core.Offer
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.OfferCsv
import dev.edmel.offerchecker.data.OfferDao
import dev.edmel.offerchecker.data.OfferDatabase
import dev.edmel.offerchecker.data.OfferEntry
import dev.edmel.offerchecker.service.NahCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
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
    var open by remember { mutableStateOf<OfferEntry?>(null) }
    // 0 = all, then bet, eh, nah.
    var filter by rememberSaveable { mutableIntStateOf(0) }

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

    val verdicts = listOf<Verdict?>(null, Verdict.GREEN, Verdict.YELLOW, Verdict.RED)
    val shown = offers.filter { e -> verdicts[filter]?.let { e.verdict == it.name } ?: true }
    val todayStart = remember(offers) { dayStart(System.currentTimeMillis()) }
    val days = shown.groupBy { dayStart(it.time) }

    val c = Nah.c
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = Nah.title.copy(color = c.ink), modifier = Modifier.weight(1f))
                NahButton("Export", {
                    export.launch("nahmeter-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv")
                }, kind = NahButtonKind.Secondary, small = true, enabled = offers.isNotEmpty())
            }
        }
        item { NahSegmented(listOf("All", "bet", "eh", "nah"), filter, { filter = it }) }
        if (shown.isEmpty()) {
            item {
                NahGroup {
                    if (offers.isEmpty()) NahRow("No offers yet")
                    else NahRow("No offers match these filters.")
                }
            }
        }
        days.forEach { (day, list) ->
            item(key = day) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dayLabel(day, todayStart), style = Nah.style(14.sp, 600, 20.sp, color = c.ink),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${list.size} offer${if (list.size == 1) "" else "s"} · avg $${"%.2f".format(Locale.US, list.map { it.hourly }.average())}/hr",
                            style = Nah.style(13.sp, 400, 18.sp, color = c.inkMuted),
                        )
                    }
                    NahGroup {
                        list.forEachIndexed { i, e ->
                            if (i > 0) NahDivider()
                            OfferRow(e) { open = e }
                        }
                    }
                }
            }
        }
        if (offers.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("The last ${OfferDao.MAX_OFFERS} offers are kept.", style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted))
                    NahButton("Clear log", { confirmClear = true }, kind = NahButtonKind.Tertiary)
                }
            }
        }
    }

    open?.let { OfferDetails(it, thresholds) { open = null } }

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

private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** "Today · Wed, Oct 28", "Yesterday · Tue, Oct 27", or just the date. */
private fun dayLabel(day: Long, today: Long): String {
    val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(day))
    return when (day) {
        today -> "Today · $date"
        dayStart(today - 1) -> "Yesterday · $date"
        else -> date
    }
}

private fun perKmText(e: OfferEntry) = (e.perKm?.let { "$" + "%.2f".format(Locale.US, it) } ?: "—") + "/km"

private fun numsText(e: OfferEntry) =
    (if (e.addOn) "+" else "") + "$" + "%.2f".format(Locale.US, e.pay) + " · ${e.minutes} min · ${e.km} km" +
        (if (e.orders > 1) " · ×${e.orders}" else "")

/** OfferRow: the offer's numbers over its time and rates, and a verdict chip. Tap for details. */
@Composable
private fun OfferRow(e: OfferEntry, onOpen: () -> Unit) {
    val c = Nah.c
    val verdict = runCatching { Verdict.valueOf(e.verdict) }.getOrDefault(Verdict.GREY)
    val time = remember(e.time) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(e.time)) }
    val nums = numsText(e)
    val rates = "$time · $${"%.2f".format(Locale.US, e.hourly)}/hr · ${perKmText(e)}"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(onClickLabel = "Show offer details", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${NahCopy.word(verdict)} $nums, $rates"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nums, style = Nah.style(16.sp, 600, 22.sp, color = c.ink))
            Text(rates, style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted))
        }
        VerdictChip(verdict)
    }
}

/** Everything logged about one offer, opened from its row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfferDetails(e: OfferEntry, thresholds: Thresholds, onDismiss: () -> Unit) {
    val c = Nah.c
    val verdict = runCatching { Verdict.valueOf(e.verdict) }.getOrDefault(Verdict.GREY)
    val rescored = Scorer.score(
        Offer(BigDecimal.valueOf(e.pay), e.minutes, BigDecimal.valueOf(e.km), e.stacked, orders = e.orders, addOn = e.addOn),
        thresholds.copy(waitBufferMin = 0),
    )
    val sameVerdict = rescored.verdict == verdict
    // The logged rates, the strict one big as on the card. The reason comes from today's rules, so
    // it only shows while they still give the logged verdict; otherwise the other rate stands in.
    val (big, other) = NahCopy.rates(
        BigDecimal.valueOf(e.hourly).setScale(2, RoundingMode.HALF_UP),
        e.perKm?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP) },
        thresholds.priority,
    )!!
    val reason = if (sameVerdict) NahCopy.cardReason(rescored, thresholds) else other?.toString() ?: perKmText(e)
    val chip = if (sameVerdict) other?.toString() else null
    val hourly = "$" + "%.2f".format(Locale.US, e.hourly)
    val whenText = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(e.time))
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.ground, contentColor = c.ink) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(whenText, style = Nah.style(14.sp, 600, 20.sp, color = c.inkMuted))
            VerdictCard(verdict, big, reason, spoken = "${NahCopy.word(verdict)} ${NahCopy.spokenRate(big)}. $reason", chip = chip)
            NahGroup {
                Detail("Pay", "$" + "%.2f".format(Locale.US, e.pay))
                NahDivider()
                Detail("Time", "${e.minutes} min")
                NahDivider()
                Detail("Distance", "${e.km} km")
                NahDivider()
                Detail("Per hour", "$hourly/hr")
                NahDivider()
                Detail("Per km", perKmText(e))
                if (e.orders > 1) {
                    NahDivider()
                    Detail("Orders", "${e.orders}")
                }
                if (e.addOn) {
                    NahDivider()
                    Detail("Type", "Add-on to the current trip")
                }
            }
            NahGroup {
                Detail("Restaurant", e.pickup ?: "Not read")
                NahDivider()
                Detail("Drop-off", e.dropoff ?: "Not read")
            }
        }
    }
}

/** A label on the left, its value on the right; long values wrap. */
@Composable
private fun Detail(label: String, value: String) {
    val c = Nah.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = Nah.style(15.sp, 500, 22.sp, color = c.inkMuted), modifier = Modifier.width(96.dp))
        Text(value, style = Nah.style(16.sp, 600, 22.sp, color = c.ink).copy(textAlign = TextAlign.End), modifier = Modifier.weight(1f))
    }
}
