package dev.edmel.offerchecker.core

import java.math.BigDecimal

enum class Verdict { GREEN, YELLOW, RED, GREY }

/** The decision rules, in the order they're checked. */
enum class Check { MIN_PAY, MAX_KM, HOURLY, PER_KM }

/**
 * Which rate is strict: missing it is nah., missing the other is only meh. $/km suits busy times,
 * when traffic stretches the minutes but not the distance.
 */
enum class Priority { HOURLY, PER_KM }

/** One delivery offer as read from the card. Pay is in CAD; minutes are Uber's total. */
data class Offer(
    val pay: BigDecimal,
    val minutes: Int,
    val km: BigDecimal,
    val stacked: Boolean = false,
    /** The restaurant line, e.g. "Lazeez Shawarma (2900 Steeles)", when it could be read. */
    val pickup: String? = null,
    /** Where it goes, e.g. "Annibale Dr & Bembridge Dr, Markham", when it could be read. */
    val dropoff: String? = null,
    /** How many orders the card bundles ("Delivery (3)" = 3). */
    val orders: Int = if (stacked) 2 else 1,
    /**
     * An "Add a delivery" card: pay, minutes and km are what this order adds to the trip already
     * accepted, so the rates are for the extra work.
     */
    val addOn: Boolean = false,
)

/** What the parser made of the text inside an offer card. */
sealed interface ReadResult {
    /** No offer card on screen (no "min (… km) total" line). */
    data object NoOffer : ReadResult

    /** Looks like an offer card, but a number could not be read. Never guessed. */
    data class Unreadable(val raw: List<String>) : ReadResult

    data class Read(val offer: Offer, val raw: List<String>) : ReadResult
}

/** Thresholds from Settings. All are inclusive: meeting the threshold exactly passes. */
data class Thresholds(
    /** Minimum pay for a single order; stacks and add-ons have their own ([minPayFor]). */
    val minPay: BigDecimal = BigDecimal("5.50"),
    val minHourly: BigDecimal = BigDecimal("20.00"),
    val minPerKm: BigDecimal = BigDecimal("1.00"),
    val waitBufferMin: Int = 0,
    /** Waiting for food at each pickup on a multi-order offer: 2 orders add 4 min. */
    val perOrderWaitMin: Int = 2,
    /** The longest trip worth taking, in km (Uber's total on the card); null is no limit. */
    val maxKm: BigDecimal? = null,
    /**
     * The unpaid drive back to the courier's area, as a % of the trip: 50 adds half the km, and half
     * Uber's minutes (the same pace). Lowers $/hr and $/km; 0 is off.
     */
    val extraKmPercent: Int = 0,
    /** The drive back only counts on trips over this many km; shorter ones stay in your area. 0 is every trip. */
    val extraKmOverKm: Int = 10,
    /** The strict rate (red when missed); the other one only gives yellow. */
    val priority: Priority = Priority.HOURLY,
    /** Minimum pay for a card with 2 orders ("Delivery (2)"). */
    val minPay2: BigDecimal = BigDecimal("9.00"),
    /** Minimum pay for a card with 3 or more orders. */
    val minPay3: BigDecimal = BigDecimal("12.00"),
    /** Minimum for an "Add a delivery" add-on (what it adds to the trip); 0 skips the check. */
    val minAddOn: BigDecimal = BigDecimal("4.00"),
    /**
     * Uber's pay includes the estimated tip, and a tip can fail to come or be taken back: this much
     * per order is taken off the pay before every check and rate. 0 is off.
     */
    val tipCushion: BigDecimal = BigDecimal.ZERO,
    /**
     * The idle-aware rules: a $/hr bar that drops the longer Uber hasn't paid, the drive back by
     * drop-off area, the meh. band and the stack bonus. Null is the fixed rules above.
     */
    val idle: IdleRules? = null,
) {
    /** The minimum pay for this card: by its number of orders, or the add-on minimum. */
    fun minPayFor(offer: Offer?): BigDecimal = when {
        offer == null -> minPay
        offer.addOn -> minAddOn
        offer.orders >= 3 -> minPay3
        offer.orders == 2 -> minPay2
        else -> minPay
    }
}

/** Once Uber hasn't paid for [afterMin] minutes, the $/hr bar is [hourly] (never above the courier's own). */
data class IdleStep(val afterMin: Int, val hourly: BigDecimal)

/**
 * A drop-off area, as the town at the end of Uber's drop-off line names it ("…, Markham"), and the
 * unpaid minutes driving back from it. A stack ending in a [home] area gets the stack bonus.
 */
data class ReturnArea(val names: List<String>, val minutes: Int, val home: Boolean = false) {
    /** How Rules lists it: "Markham, Richmond Hill, Vaughan, Concord". */
    val label: String get() = names.joinToString(", ")
}

/**
 * The idle-aware rules (the nah.meter findings PRD, decided 2026-11-05). Declining an offer means
 * waiting, and late in a slow night waiting earns far less than the fixed bar, so:
 *  - the $/hr bar steps down the longer Uber hasn't paid ([steps]; from [lateFromHour] until 5am each
 *    step comes [lateSoonerMin] sooner, since good offers are rarer then);
 *  - a $/hr up to [mehBand] under the bar is meh., not nah. (0 turns it off);
 *  - a drop-off in a listed area adds its drive back ([areas]) instead of the % rule;
 *  - a stack ending in a home area moves from meh. to bet! when it clears the lowest step
 *    ([homeStackBonus]).
 */
data class IdleRules(
    val steps: List<IdleStep> = DEFAULT_STEPS,
    val lateFromHour: Int = 22,
    val lateSoonerMin: Int = 5,
    val mehBand: BigDecimal = BigDecimal("2.00"),
    val homeStackBonus: Boolean = true,
    val areas: List<ReturnArea> = DEFAULT_AREAS,
) {
    /** From [lateFromHour] until [LATE_UNTIL_HOUR]; unknown hour is not late. */
    fun isLate(hour: Int?): Boolean = hour != null && (hour >= lateFromHour || hour < LATE_UNTIL_HOUR)

    companion object {
        const val LATE_UNTIL_HOUR = 5
        val DEFAULT_STEPS = listOf(IdleStep(10, BigDecimal("14.00")), IdleStep(25, BigDecimal("12.00")))

        /** The PRD's starting estimates from the Yonge corridor (Sheppard to Steeles). */
        val DEFAULT_AREAS = listOf(
            ReturnArea(listOf("North York", "Thornhill"), 0, home = true),
            ReturnArea(listOf("Markham", "Richmond Hill", "Vaughan", "Concord"), 15),
            ReturnArea(listOf("Scarborough", "Woodbridge", "Maple"), 20),
            ReturnArea(listOf("Toronto"), 25),
            ReturnArea(listOf("Stouffville", "Mississauga"), 30),
        )
    }
}

/**
 * What an offer is scored against besides its card, for the idle-aware rules: minutes since Uber
 * last paid ([idleMin], null when unknown) and the hour of day (0–23).
 */
data class ScoreContext(val idleMin: Int? = null, val hour: Int? = null)

/**
 * The drop-off's area, and the name it matched ("Stouffville"), from the part after the drop-off's
 * last comma ("Main St, Whitchurch-Stouffville"). Letters only, so spacing and dashes don't matter;
 * the town may end the part ("Whitchurch-Stouffville") and one slip is allowed in longer names.
 * Null when the drop-off wasn't read, has no area part, or the area isn't listed.
 */
fun returnAreaFor(dropoff: String?, areas: List<ReturnArea>): Pair<ReturnArea, String>? {
    if (dropoff == null || ',' !in dropoff) return null
    val part = placeKey(dropoff.substringAfterLast(','))
    if (part.isEmpty()) return null
    for (area in areas) for (name in area.names) {
        val key = placeKey(name)
        if (key.isEmpty()) continue
        if (part == key || part.endsWith(key) || (key.length >= 6 && editDistance(part, key) <= 1)) return area to name
    }
    return null
}

data class Evaluation(
    val verdict: Verdict,
    val offer: Offer?,
    /** Uber's minutes plus the wait buffer, the pickup waits for several orders, and the drive back. */
    val effectiveMinutes: Int?,
    /** Rounded to 2 decimals, for display only. */
    val hourly: BigDecimal?,
    /** Rounded to 2 decimals, for display only; null when km is 0. */
    val perKm: BigDecimal?,
    /** The first rule that failed, e.g. "Under $20/hr target". */
    val failedRule: String?,
    /** Which check [failedRule] is, for wording it elsewhere. */
    val failed: Check? = null,
    /** The unpaid km added for the drive back ([Thresholds.extraKmPercent]); null when none. */
    val extraKm: BigDecimal? = null,
    /** The dollars taken off the pay for tips that may not come ([Thresholds.tipCushion]); null when none. */
    val tipCushion: BigDecimal? = null,
    /** The $/hr bar under the idle-aware rules ([Thresholds.idle]); null under the fixed rules. */
    val bar: BigDecimal? = null,
    /** Minutes since Uber last paid, under the idle-aware rules, when known. */
    val idleMin: Int? = null,
    /** The minutes added for the drive back (either rule); null when none. */
    val returnMin: Int? = null,
    /** The drop-off area the idle-aware rules matched ("Stouffville"); null when none. */
    val area: String? = null,
    /** A stack ending in a home area, moved up from meh. to bet! ([IdleRules.homeStackBonus]). */
    val homeBonus: Boolean = false,
) {
    /** "$15.83/hr · $0.53/km", or "Can't read" for grey. */
    val ratesText: String
        get() = if (verdict == Verdict.GREY) "Can't read"
        else "$${hourly!!.toPlainString()}/hr · ${perKm?.let { "$${it.toPlainString()}" } ?: "—"}/km"

    companion object {
        val CANT_READ = Evaluation(Verdict.GREY, null, null, null, null, null)
    }
}

/** Whether the courier is online in Uber, as Uber's own screen says it. */
enum class OnlineState { ONLINE, OFFLINE }

/** Uber's online or offline state, and the line on its screen that showed it ("Finding trips"). */
data class StatusRead(val state: OnlineState, val line: String)

/** The courier's note on a restaurant, set from History and shown on later offers from it. */
enum class PlaceFlag { SLOW, CLOSED }

/** A flagged restaurant: its name as an offer showed it ("Kakuya (Yonge St)"), and the flag. */
data class FlaggedPlace(val name: String, val flag: PlaceFlag)

/**
 * The courier's flag for the restaurant [pickup], if any. Names match like [samePlace] (text
 * recognition varies between reads), but an unknown name, or one with no Latin letters or digits
 * to compare, never matches. Each location is its own restaurant.
 */
fun flagFor(pickup: String?, flags: List<FlaggedPlace>): FlaggedPlace? {
    if (!canFlag(pickup)) return null
    return flags.firstOrNull { canFlag(it.name) && samePlace(pickup, it.name) }
}

/** Whether a flag on this restaurant name could ever match a later offer ([flagFor]). */
fun canFlag(pickup: String?): Boolean = pickup != null && placeKey(pickup).isNotEmpty()

/** A place name's letters and digits, the part [samePlace] compares. */
private fun placeKey(s: String) = s.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

/**
 * Whether two reads of a place name are the same place. Text recognition varies between reads of
 * one card ("ARAİTaiker Taiwanese Fried" / "AITaiker Taiwanese Fried"), so compare letters and
 * digits only and allow a few differences. Unknown (null) on either side matches.
 */
fun samePlace(a: String?, b: String?): Boolean {
    if (a == null || b == null) return true
    val x = placeKey(a)
    val y = placeKey(b)
    if (x.isEmpty() || y.isEmpty() || x == y) return true
    // Levenshtein distance, allowed up to a quarter of the longer name.
    return editDistance(x, y) <= maxOf(x.length, y.length) / 4
}

/** Levenshtein distance: the fewest single-letter changes that turn [x] into [y]. */
private fun editDistance(x: String, y: String): Int {
    var prev = IntArray(y.length + 1) { it }
    for (i in 1..x.length) {
        val cur = IntArray(y.length + 1)
        cur[0] = i
        for (j in 1..y.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
        }
        prev = cur
    }
    return prev[y.length]
}

/** One line of text recognised (OCR) in a screenshot, with its vertical position in pixels. */
data class OcrLine(val text: String, val top: Int, val bottom: Int, val left: Int = 0, val right: Int = 0)
