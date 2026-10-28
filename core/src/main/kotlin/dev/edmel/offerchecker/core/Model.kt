package dev.edmel.offerchecker.core

import java.math.BigDecimal

enum class Verdict { GREEN, YELLOW, RED, GREY }

/** The decision rules, in the order they're checked. */
enum class Check { MIN_PAY, HOURLY, PER_KM }

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
)

data class Evaluation(
    val verdict: Verdict,
    val offer: Offer?,
    /** Uber's minutes plus the wait buffer. */
    val effectiveMinutes: Int?,
    /** Rounded to 2 decimals, for display only. */
    val hourly: BigDecimal?,
    /** Rounded to 2 decimals, for display only; null when km is 0. */
    val perKm: BigDecimal?,
    /** The first rule that failed, e.g. "Under $20/hr target". */
    val failedRule: String?,
    /** Which check [failedRule] is, for wording it elsewhere. */
    val failed: Check? = null,
) {
    /** "$15.83/hr · $0.53/km", or "Can't read" for grey. */
    val ratesText: String
        get() = if (verdict == Verdict.GREY) "Can't read"
        else "$${hourly!!.toPlainString()}/hr · ${perKm?.let { "$${it.toPlainString()}" } ?: "—"}/km"

    companion object {
        val CANT_READ = Evaluation(Verdict.GREY, null, null, null, null, null)
    }
}

/** One line of text recognised (OCR) in a screenshot, with its vertical position in pixels. */
data class OcrLine(val text: String, val top: Int, val bottom: Int, val left: Int = 0, val right: Int = 0)
