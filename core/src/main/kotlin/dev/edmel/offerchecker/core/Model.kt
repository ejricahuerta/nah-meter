package dev.edmel.offerchecker.core

import java.math.BigDecimal

enum class Verdict { GREEN, YELLOW, RED, GREY }

/** The decision rules, in the order they're checked. */
enum class Check { MIN_PAY, MAX_KM, HOURLY, PER_KM }

/**
 * Which rate is strict: missing it is nah., missing the other is only eh. $/km suits busy times,
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
)

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
) {
    /** "$15.83/hr · $0.53/km", or "Can't read" for grey. */
    val ratesText: String
        get() = if (verdict == Verdict.GREY) "Can't read"
        else "$${hourly!!.toPlainString()}/hr · ${perKm?.let { "$${it.toPlainString()}" } ?: "—"}/km"

    companion object {
        val CANT_READ = Evaluation(Verdict.GREY, null, null, null, null, null)
    }
}

/**
 * Whether two reads of a place name are the same place. Text recognition varies between reads of
 * one card ("ARAİTaiker Taiwanese Fried" / "AITaiker Taiwanese Fried"), so compare letters and
 * digits only and allow a few differences. Unknown (null) on either side matches.
 */
fun samePlace(a: String?, b: String?): Boolean {
    if (a == null || b == null) return true
    fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
    val x = norm(a)
    val y = norm(b)
    if (x.isEmpty() || y.isEmpty() || x == y) return true
    // Levenshtein distance, allowed up to a quarter of the longer name.
    var prev = IntArray(y.length + 1) { it }
    for (i in 1..x.length) {
        val cur = IntArray(y.length + 1)
        cur[0] = i
        for (j in 1..y.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
        }
        prev = cur
    }
    return prev[y.length] <= maxOf(x.length, y.length) / 4
}

/** One line of text recognised (OCR) in a screenshot, with its vertical position in pixels. */
data class OcrLine(val text: String, val top: Int, val bottom: Int, val left: Int = 0, val right: Int = 0)
