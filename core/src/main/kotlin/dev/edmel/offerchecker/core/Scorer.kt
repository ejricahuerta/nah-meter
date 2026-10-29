package dev.edmel.offerchecker.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The five decision rules, run in order; the first that fails sets the colour.
 *   1. pay ≥ min pay        else Red
 *   2. km ≤ max km          else Red (when a max is set)
 *   3. $/hr ≥ min hourly    else Red
 *   4. $/km ≥ min per km    else Yellow
 *   5. otherwise            Green
 * The unpaid drive back ([Thresholds.extraKmPercent], on trips over [Thresholds.extraKmOverKm]) adds km
 * and minutes to the rates, not to the max.
 * Comparisons are exact (no rounding); rates are rounded to 2 decimals for display only.
 */
object Scorer {
    private val SIXTY = BigDecimal(60)
    private val HUNDRED = BigDecimal(100)

    fun evaluate(read: ReadResult, t: Thresholds): Evaluation? = when (read) {
        ReadResult.NoOffer -> null
        is ReadResult.Unreadable -> Evaluation.CANT_READ
        is ReadResult.Read -> score(read.offer, t)
    }

    fun score(offer: Offer, t: Thresholds): Evaluation {
        // Uber's time leaves out waiting for the food; with several orders that adds up.
        val pickupWait = if (offer.orders > 1) t.perOrderWaitMin * offer.orders else 0
        // The drive back isn't paid: the same share of the km and, at the offer's pace, of Uber's
        // minutes. Only on trips over the set km (shorter ones stay in your area), and not for
        // add-ons: the trip they add to already counts its own way back.
        val returns = !offer.addOn && offer.km > BigDecimal(t.extraKmOverKm.coerceAtLeast(0))
        val pct = if (returns) t.extraKmPercent.coerceIn(0, 100) else 0
        val extraKm = offer.km.multiply(BigDecimal(pct)).movePointLeft(2)
        val extraMin = BigDecimal(offer.minutes * pct).divide(HUNDRED, 0, RoundingMode.HALF_UP).toInt()
        val minutes = offer.minutes + extraMin + t.waitBufferMin + pickupWait
        if (minutes <= 0) return Evaluation.CANT_READ
        val m = BigDecimal(minutes)
        val totalKm = offer.km + extraKm
        val hourly = offer.pay.multiply(SIXTY).divide(m, 2, RoundingMode.HALF_UP)
        val perKm = if (totalKm.signum() > 0) offer.pay.divide(totalKm, 2, RoundingMode.HALF_UP) else null

        // Exact checks: pay*60 ≥ minHourly*minutes  ⇔  pay/minutes*60 ≥ minHourly.
        val (verdict, check) = when {
            // An add-on's pay is only what it adds to a trip already accepted, so the minimum
            // (meant for a whole trip) doesn't apply; it's judged on $/hr and $/km alone.
            !offer.addOn && offer.pay < t.minPay -> Verdict.RED to Check.MIN_PAY
            // The max is about how far Uber sends you: the card's km, without the drive back.
            t.maxKm != null && offer.km > t.maxKm -> Verdict.RED to Check.MAX_KM
            offer.pay.multiply(SIXTY) < t.minHourly.multiply(m) -> Verdict.RED to Check.HOURLY
            totalKm.signum() > 0 && offer.pay < t.minPerKm.multiply(totalKm) -> Verdict.YELLOW to Check.PER_KM
            else -> Verdict.GREEN to null
        }
        val rule = when (check) {
            Check.MIN_PAY -> "Under ${money(t.minPay)} minimum"
            Check.MAX_KM -> "Over ${km(t.maxKm!!)} km max"
            Check.HOURLY -> "Under ${money(t.minHourly)}/hr target"
            Check.PER_KM -> "Under ${money(t.minPerKm)}/km target"
            null -> null
        }
        return Evaluation(verdict, offer, minutes, hourly, perKm, rule, check, extraKm.takeIf { it.signum() > 0 })
    }

    /** "15" for whole km, "12.5" otherwise. */
    fun km(v: BigDecimal): String = v.stripTrailingZeros().toPlainString()

    /** "$20" for whole dollars, "$5.50" otherwise. */
    fun money(v: BigDecimal): String {
        val s = v.stripTrailingZeros()
        return if (s.scale() <= 0) "$${s.toBigInteger()}" else "$${v.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
    }
}
