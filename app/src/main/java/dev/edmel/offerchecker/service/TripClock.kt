package dev.edmel.offerchecker.service

import android.content.Context
import dev.edmel.offerchecker.core.OnlineState
import dev.edmel.offerchecker.core.ScoreContext
import dev.edmel.offerchecker.core.Scorer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal
import java.util.Calendar

/** One rise of Uber's earnings figure: a delivery (or a tip) paid [amount] at [at]. */
data class PayRise(val at: Long, val amount: BigDecimal)

/**
 * What Uber's earnings figure ("$124.40" above its map) has done today. It goes up when a delivery
 * pays, so its last rise is when the last trip ended. Days start at 4am ([shiftDayStart]).
 */
data class UberPay(
    /** Uber's figure as last read; null until seen. */
    val amount: BigDecimal? = null,
    /** The day [rises] count for. */
    val day: Long = 0L,
    /** Today's rises, oldest first. */
    val rises: List<PayRise> = emptyList(),
) {
    private fun today(now: Long) = if (day == shiftDayStart(now)) rises else emptyList()

    /** When Uber last paid today; null before the first pay. */
    fun paidAt(now: Long): Long? = today(now).lastOrNull()?.at

    /** What Uber paid today while nah.meter was watching, from [from] on. */
    fun earned(now: Long, from: Long = 0L): BigDecimal =
        today(now).filter { it.at >= from }.fold(BigDecimal.ZERO) { sum, r -> sum + r.amount }

    /** Whether Uber's figure has been read today. */
    fun seenToday(now: Long) = amount != null && day == shiftDayStart(now)
}

/**
 * Keeps [UberPay] from Uber's earnings figure ([dev.edmel.offerchecker.core.OfferParser.uberEarnings],
 * once two reads agree), on the phone (SharedPreferences), and gives the idle time the
 * idle-aware rules score with: minutes since Uber last paid, or since going online if it hasn't.
 *
 * nah.meter never sees an offer accepted or a trip start, only the figure going up when it pays.
 * A tip added later also raises it, which restarts the idle time (a stricter bar for a while).
 */
object TripClock {
    private const val PREFS = "trip_clock"

    /** A rise bigger than this in one step is a misread (or many trips unseen): a new baseline, not pay. */
    private val MAX_RISE = BigDecimal(100)

    private val flow = MutableStateFlow(UberPay())
    @Volatile private var loaded = false

    fun pay(context: Context): StateFlow<UberPay> {
        load(context)
        return flow
    }

    /**
     * Uber's figure read as [amount] (two reads agreeing). Returns what to log, or null when
     * nothing changed:
     *  - first read: a baseline, nothing paid;
     *  - higher: paid the difference, the idle time restarts;
     *  - lower, but no lower than before the last rise: that rise was a misread, so it's taken back;
     *  - lower than that: Uber started its figure again (a new day), a new baseline.
     */
    @Synchronized
    fun seen(context: Context, amount: BigDecimal, now: Long = System.currentTimeMillis()): Pair<String, String>? {
        load(context)
        val cur = flow.value
        val today = shiftDayStart(now)
        val rises = if (cur.day == today) cur.rises else emptyList()
        val prev = cur.amount
        val money = Scorer::money
        return when {
            prev == null -> {
                save(context, UberPay(amount, today, rises))
                "Uber earnings seen" to "${money(amount)} (from now on, a rise is a delivery paid)"
            }
            amount.compareTo(prev) == 0 -> {
                if (cur.day != today) save(context, cur.copy(day = today, rises = rises))
                null
            }
            amount > prev -> {
                val rise = amount - prev
                if (rise > MAX_RISE) {
                    save(context, UberPay(amount, today, rises))
                    "Uber earnings jumped" to "${money(prev)} → ${money(amount)}: not counted as pay"
                } else {
                    save(context, UberPay(amount, today, (rises + PayRise(now, rise)).takeLast(MAX_RISES)))
                    "Uber paid" to "${money(prev)} → ${money(amount)} (+${money(rise)}); waiting starts now"
                }
            }
            else -> {
                val last = rises.lastOrNull()
                val before = last?.let { prev - it.amount }
                if (last != null && before != null && amount >= before) {
                    // Fell back no further than before the last rise: that rise was a misread.
                    val kept = rises.dropLast(1)
                    val again = amount - before
                    val fixed = if (again.signum() > 0) kept + PayRise(last.at, again) else kept
                    save(context, UberPay(amount, today, fixed))
                    "Uber earnings corrected" to "${money(prev)} → ${money(amount)}: the last rise was a misread"
                } else {
                    save(context, UberPay(amount, today, rises))
                    "Uber earnings reset" to "${money(prev)} → ${money(amount)} (Uber started its figure again)"
                }
            }
        }
    }

    /**
     * Minutes since Uber last paid, or since going online if it hasn't since. Null when Uber isn't
     * known to be online (just started, paused, or offline): the idle-aware bar then stays the
     * courier's own.
     */
    fun idleMin(context: Context, now: Long = System.currentTimeMillis()): Int? {
        val online = OnlineClock.status(context).value
        if (online.state != OnlineState.ONLINE) return null
        val start = maxOf(online.since, pay(context).value.paidAt(now) ?: 0L)
        return ((now - start) / 60_000L).toInt().coerceAtLeast(0)
    }

    /** The idle time and hour of day, for scoring with the idle-aware rules. */
    fun scoreContext(context: Context, now: Long = System.currentTimeMillis()): ScoreContext =
        ScoreContext(idleMin(context, now), Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY))

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        val p = prefs(context)
        flow.value = UberPay(
            amount = p.getString("amount", null)?.toBigDecimalOrNull(),
            day = p.getLong("day", 0L),
            rises = p.getString("rises", null).orEmpty().split(';').mapNotNull { s ->
                val at = s.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                val amount = s.substringAfter(':', "").toBigDecimalOrNull() ?: return@mapNotNull null
                PayRise(at, amount)
            },
        )
        loaded = true
    }

    private fun save(context: Context, s: UberPay) {
        flow.value = s
        prefs(context).edit()
            .putString("amount", s.amount?.toPlainString())
            .putLong("day", s.day)
            .putString("rises", s.rises.joinToString(";") { "${it.at}:${it.amount.toPlainString()}" })
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val MAX_RISES = 100
}
