package dev.edmel.offerchecker.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The four decision rules, run in order; the first that fails sets the colour.
 *   1. pay ≥ min pay        else Red
 *   2. $/hr ≥ min hourly    else Red
 *   3. $/km ≥ min per km    else Yellow
 *   4. otherwise            Green
 * Comparisons are exact (no rounding); rates are rounded to 2 decimals for display only.
 */
object Scorer {
    private val SIXTY = BigDecimal(60)

    fun evaluate(read: ReadResult, t: Thresholds): Evaluation? = when (read) {
        ReadResult.NoOffer -> null
        is ReadResult.Unreadable -> Evaluation.CANT_READ
        is ReadResult.Read -> score(read.offer, t)
    }

    fun score(offer: Offer, t: Thresholds): Evaluation {
        val minutes = offer.minutes + t.waitBufferMin
        if (minutes <= 0) return Evaluation.CANT_READ
        val m = BigDecimal(minutes)
        val hourly = offer.pay.multiply(SIXTY).divide(m, 2, RoundingMode.HALF_UP)
        val perKm = if (offer.km.signum() > 0) offer.pay.divide(offer.km, 2, RoundingMode.HALF_UP) else null

        // Exact checks: pay*60 ≥ minHourly*minutes  ⇔  pay/minutes*60 ≥ minHourly.
        val (verdict, failed) = when {
            offer.pay < t.minPay -> Verdict.RED to "Under ${money(t.minPay)} minimum"
            offer.pay.multiply(SIXTY) < t.minHourly.multiply(m) -> Verdict.RED to "Under ${money(t.minHourly)}/hr target"
            offer.km.signum() > 0 && offer.pay < t.minPerKm.multiply(offer.km) ->
                Verdict.YELLOW to "Under ${money(t.minPerKm)}/km target"
            else -> Verdict.GREEN to null
        }
        return Evaluation(verdict, offer, minutes, hourly, perKm, failed)
    }

    /** "$20" for whole dollars, "$5.50" otherwise. */
    fun money(v: BigDecimal): String {
        val s = v.stripTrailingZeros()
        return if (s.scale() <= 0) "$${s.toBigInteger()}" else "$${v.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
    }
}
