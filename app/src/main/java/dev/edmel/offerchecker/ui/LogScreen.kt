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

    val c = Nah.c
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today", style = Nah.title.copy(color = c.ink), modifier = Modifier.weight(1f))
                NahButton("Export CSV", {
                    export.launch("nahmeter-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv")
                }, kind = NahButtonKind.Tertiary, enabled = offers.isNotEmpty())
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)
                    .semantics(mergeDescendants = true) { },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (today.isEmpty()) {
                    Text("No offers yet today", style = Nah.bodyStrong.copy(color = c.ink))
                    Text("Every offer nahmeter reads lands here.", style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted))
                } else {
                    val avg = "%.2f".format(Locale.US, today.map { it.hourly }.average())
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Average offer", style = Nah.style(14.sp, 500, 20.sp, color = c.inkMuted))
                            Text(
                                buildAnnotatedString {
                                    append("$$avg")
                                    withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.W600, letterSpacing = 0.em, color = c.inkMuted)) {
                                        append("/hr")
                                    }
                                },
                                style = Nah.style(40.sp, 800, 42.sp, (-0.045).em, c.ink),
                            )
                        }
                        Text(
                            "${today.size} offer${if (today.size == 1) "" else "s"}",
                            style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted),
                        )
                    }
                    // Share of bet / eh / nah today.
                    Row(
                        Modifier.fillMaxWidth().height(10.dp).clearAndSetSemantics { },
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        listOf(Verdict.GREEN, Verdict.YELLOW, Verdict.RED).forEach { v ->
                            val n = count(v)
                            if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxSize().background(Nah.verdictColor(v)))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Verdict.GREEN, Verdict.YELLOW, Verdict.RED).forEach { v ->
                            if (count(v) > 0) VerdictChip(v, "${count(v)} ${NahCopy.chipWord(v)}")
                        }
                    }
                }
            }
        }
        if (offers.isNotEmpty()) {
            item {
                NahGroup {
                    offers.forEachIndexed { i, e ->
                        if (i > 0) NahDivider()
                        OfferRow(e, thresholds, today = e.time >= startOfDay) { open = e }
                    }
                }
            }
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

/** The rates line for a logged offer: "$15.83/hr · under $20", "under $5.50 min" or "$/hr · $/km". */
private fun ratesText(e: OfferEntry, thresholds: Thresholds, verdict: Verdict): String {
    val hr = "$" + "%.2f".format(Locale.US, e.hourly) + "/hr"
    // A nah shows the floor it missed, when today's floors still give the same verdict.
    val rescored = Scorer.score(Offer(BigDecimal.valueOf(e.pay), e.minutes, BigDecimal.valueOf(e.km)), thresholds.copy(waitBufferMin = 0))
        .takeIf { it.verdict == verdict }
    return when (rescored?.failed) {
        Check.MIN_PAY -> "under ${Scorer.money(thresholds.minPay)} min"
        Check.HOURLY -> "$hr · under ${Scorer.money(thresholds.minHourly)}"
        else -> "$hr · ${perKmText(e)}"
    }
}

private fun perKmText(e: OfferEntry) = (e.perKm?.let { "$" + "%.2f".format(Locale.US, it) } ?: "—") + "/km"

private fun numsText(e: OfferEntry) =
    (if (e.addOn) "+" else "") + "$" + "%.2f".format(Locale.US, e.pay) + " · ${e.minutes} min · ${e.km} km" +
        (if (e.orders > 1) " · ×${e.orders}" else "")

/** OfferRow: time, the offer's numbers, rates and restaurant, and a verdict chip. Tap for details. */
@Composable
private fun OfferRow(e: OfferEntry, thresholds: Thresholds, today: Boolean, onOpen: () -> Unit) {
    val c = Nah.c
    val verdict = runCatching { Verdict.valueOf(e.verdict) }.getOrDefault(Verdict.GREY)
    val time = remember(e.time, today) {
        SimpleDateFormat(if (today) "h:mm a" else "MMM d", Locale.getDefault()).format(Date(e.time))
    }
    val rates = ratesText(e, thresholds, verdict)
    val nums = numsText(e)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp)
            .clickable(onClickLabel = "Show offer details", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$time, ${NahCopy.word(verdict)} $nums, $rates" + (e.pickup?.let { ", from $it" } ?: "")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(time, style = Nah.style(13.sp, 500, 18.sp, color = c.inkMuted), modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nums, style = Nah.style(16.sp, 600, 22.sp, color = c.ink))
            Text(rates, style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted))
            if (e.pickup != null) {
                Text(
                    e.pickup, style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
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
    val reason = if (rescored.verdict == verdict) NahCopy.cardReason(rescored, thresholds) else perKmText(e)
    val hourly = "$" + "%.2f".format(Locale.US, e.hourly)
    val whenText = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(e.time))
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.ground, contentColor = c.ink) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(whenText, style = Nah.style(14.sp, 600, 20.sp, color = c.inkMuted))
            VerdictCard(verdict, hourly, reason, spoken = "${NahCopy.word(verdict)} $hourly per hour. $reason")
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
            if (e.pickup == null && e.dropoff == null) {
                Text(
                    "Places are read from offers logged since this update.",
                    style = Nah.style(13.sp, 400, 18.sp, color = c.inkMuted),
                )
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
