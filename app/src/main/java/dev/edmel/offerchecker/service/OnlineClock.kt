package dev.edmel.offerchecker.service

import android.content.Context
import dev.edmel.offerchecker.core.OnlineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

/**
 * Whether the courier is online in Uber, as nah.meter last saw it, and their online time today.
 * "Today" starts at 4am ([shiftDayStart]), so a 5pm–3am shift is one day.
 */
data class UberStatus(
    /** What Uber's screen last said; null until the reader sees it (after starting, or a pause). */
    val state: OnlineState? = null,
    /** When [state] was first seen. */
    val since: Long = 0L,
    /** Online time on [day] before the current session, in ms. */
    val doneMs: Long = 0L,
    /** The day [doneMs] counts for (local midnight). */
    val day: Long = 0L,
    /** The last time Uber confirmed [state]; ends a session a crash left open. */
    val seenAt: Long = 0L,
    /** Today's ended online sessions (start to end, ms), for online time in a window ([onlineMs]). */
    val sessions: List<Pair<Long, Long>> = emptyList(),
) {
    /** Online time today, counting the session still open from 4am at the earliest. */
    fun todayMs(now: Long): Long {
        val today = shiftDayStart(now)
        val done = if (day == today) doneMs else 0L
        val open = if (state == OnlineState.ONLINE) (now - maxOf(since, today)).coerceAtLeast(0L) else 0L
        return done + open
    }

    /** Online time between [from] and [now] (today's sessions only), e.g. the last 2 hours. */
    fun onlineMs(from: Long, now: Long): Long {
        val today = shiftDayStart(now)
        val closed = if (day == today) sessions else emptyList()
        val open = if (state == OnlineState.ONLINE) listOf(maxOf(since, today) to now) else emptyList()
        return (closed + open).sumOf { (start, end) -> (minOf(end, now) - maxOf(start, from)).coerceAtLeast(0L) }
    }

    /** The current session ended at [at]: its time today is added to [doneMs] and [sessions]. */
    internal fun closedAt(at: Long): UberStatus {
        val today = shiftDayStart(at)
        val kept = if (day == today) sessions else emptyList()
        val start = if (state == OnlineState.ONLINE) maxOf(since, today) else null
        val all = if (start != null && at > start) kept + (start to at) else kept
        return copy(doneMs = todayMs(at), day = today, sessions = all.takeLast(MAX_SESSIONS))
    }

    private companion object {
        const val MAX_SESSIONS = 50
    }
}

/**
 * Keeps [UberStatus] from what Uber's own screen says ([dev.edmel.offerchecker.core.OfferParser.onlineStatus])
 * and from offers arriving, on the phone (SharedPreferences), so the day's online time survives
 * the app restarting.
 *
 * nah.meter only knows what Uber last showed: if Uber goes offline by itself while it isn't on
 * screen, the time keeps counting until Uber is next seen offline, reading is paused, or the
 * reader stops.
 */
object OnlineClock {
    private const val PREFS = "online_clock"
    private const val SAVE_SEEN_EVERY_MS = 30 * 1000L

    private val flow = MutableStateFlow(UberStatus())
    @Volatile private var loaded = false

    /** The status for the screens; loads the saved one first. */
    fun status(context: Context): StateFlow<UberStatus> {
        load(context)
        return flow
    }

    /** Uber showed [state] (confirmed by two reads, or an offer card). Returns whether it changed. */
    @Synchronized
    fun seen(context: Context, state: OnlineState, now: Long = System.currentTimeMillis()): Boolean {
        load(context)
        val cur = flow.value
        if (cur.state == state) {
            if (now - cur.seenAt >= SAVE_SEEN_EVERY_MS) save(context, cur.copy(seenAt = now))
            return false
        }
        save(context, cur.closedAt(now).copy(state = state, since = now, seenAt = now))
        return true
    }

    /** nah.meter can't see Uber any more (reading paused, or the reader stopping): the session ends now. */
    @Synchronized
    fun stopped(context: Context, now: Long = System.currentTimeMillis()) {
        load(context)
        val cur = flow.value
        if (cur.state == null) return
        save(context, cur.closedAt(now).copy(state = null, since = now))
    }

    /** The reader is starting: a session a crash left open ends when Uber last confirmed it. */
    @Synchronized
    fun started(context: Context) {
        load(context)
        val cur = flow.value
        if (cur.state == null) return
        val end = maxOf(cur.since, cur.seenAt)
        save(context, cur.closedAt(end).copy(state = null, since = end))
    }

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        val p = prefs(context)
        flow.value = UberStatus(
            state = p.getString("state", null)?.let { s -> OnlineState.entries.firstOrNull { it.name == s } },
            since = p.getLong("since", 0L),
            doneMs = p.getLong("done_ms", 0L),
            day = p.getLong("day", 0L),
            seenAt = p.getLong("seen_at", 0L),
            sessions = p.getString("sessions", null).orEmpty().split(';').mapNotNull { s ->
                val (a, b) = s.split('-').takeIf { it.size == 2 } ?: return@mapNotNull null
                (a.toLongOrNull() ?: return@mapNotNull null) to (b.toLongOrNull() ?: return@mapNotNull null)
            },
        )
        loaded = true
    }

    private fun save(context: Context, s: UberStatus) {
        flow.value = s
        prefs(context).edit()
            .putString("state", s.state?.name)
            .putLong("since", s.since)
            .putLong("done_ms", s.doneMs)
            .putLong("day", s.day)
            .putLong("seen_at", s.seenAt)
            .putString("sessions", s.sessions.joinToString(";") { "${it.first}-${it.second}" })
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** When a day starts for a courier: 4am, like Uber's earnings day, so a 5pm–3am shift is one day. */
const val SHIFT_DAY_START_HOUR = 4

/** 4am at the start of [t]'s day (the day before's 4am between midnight and 4am). */
fun shiftDayStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    if (get(Calendar.HOUR_OF_DAY) < SHIFT_DAY_START_HOUR) add(Calendar.DAY_OF_MONTH, -1)
    set(Calendar.HOUR_OF_DAY, SHIFT_DAY_START_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
