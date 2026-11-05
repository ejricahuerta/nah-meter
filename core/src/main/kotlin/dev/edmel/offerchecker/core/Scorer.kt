package dev.edmel.offerchecker.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The five decision rules, run in order; the first that fails sets the colour.
 *   1. pay ≥ min pay        else Red     (by orders: 1, 2 or 3+; add-ons have their own)
 *   2. km ≤ max km          else Red (when a max is set)
 *   3. the strict rate      else Red     ($/hr ≥ min hourly by default, or $/km ≥ min per km)
 *   4. the other rate       else Yellow  ([Thresholds.priority] picks which is strict)
 *   5. otherwise            Green
 * The tip cushion ([Thresholds.tipCushion] per order) comes off the pay before every rule.
 * The unpaid drive back ([Thresholds.extraKmPercent], on trips over [Thresholds.extraKmOverKm]) adds km
 * and minutes to the rates, not to the max.
 *
 * With the idle-aware rules ([Thresholds.idle], off by default):
 *   - the $/hr bar steps down the longer Uber hasn't paid ([ScoreContext.idleMin]), never above min hourly;
 *   - a strict $/hr up to [IdleRules.mehBand] under the bar is Yellow, not Red;
 *   - a drop-off in a listed area adds that area's drive back instead of the % rule;
 *   - a Yellow stack ending in a home area is Green when it clears the lowest step.
 * Comparisons are exact (no rounding); rates are rounded to 2 decimals for display only.
 */
object Scorer {
    private val SIXTY = BigDecimal(60)
    private val HUNDRED = BigDecimal(100)

    fun evaluate(read: ReadResult, t: Thresholds, ctx: ScoreContext = ScoreContext()): Evaluation? = when (read) {
        ReadResult.NoOffer -> null
        is ReadResult.Unreadable -> Evaluation.CANT_READ
        is ReadResult.Read -> score(read.offer, t, ctx)
    }

    fun score(offer: Offer, t: Thresholds, ctx: ScoreContext = ScoreContext()): Evaluation {
        val idle = t.idle
        // Uber's time leaves out waiting for the food; with several orders that adds up.
        val pickupWait = if (offer.orders > 1) t.perOrderWaitMin * offer.orders else 0
        // The drive back isn't paid. Only on trips over the set km (shorter ones stay in your
        // area), and not for add-ons: the trip they add to already counts its own way back.
        val returns = !offer.addOn && offer.km > BigDecimal(t.extraKmOverKm.coerceAtLeast(0))
        // Idle-aware rules: the drop-off's area, for its own drive back and the stack bonus.
        val area = if (idle != null && !offer.addOn) returnAreaFor(offer.dropoff, idle.areas) else null
        val extraKm: BigDecimal
        val extraMin: Int
        if (area != null) {
            // The area's minutes back, and the km at the offer's own pace.
            extraMin = if (returns) area.first.minutes.coerceAtLeast(0) else 0
            extraKm = if (extraMin > 0 && offer.minutes > 0) {
                offer.km.multiply(BigDecimal(extraMin)).divide(BigDecimal(offer.minutes), 2, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
        } else {
            // The same share of the km and, at the offer's pace, of Uber's minutes.
            val pct = if (returns) t.extraKmPercent.coerceIn(0, 100) else 0
            extraKm = offer.km.multiply(BigDecimal(pct)).movePointLeft(2)
            extraMin = BigDecimal(offer.minutes * pct).divide(HUNDRED, 0, RoundingMode.HALF_UP).toInt()
        }
        val minutes = offer.minutes + extraMin + t.waitBufferMin + pickupWait
        if (minutes <= 0) return Evaluation.CANT_READ
        val m = BigDecimal(minutes)
        val totalKm = offer.km + extraKm
        // Uber's pay includes the estimated tip: score it as if each order's tip came up short.
        val cushion = t.tipCushion.max(BigDecimal.ZERO).multiply(BigDecimal(offer.orders.coerceAtLeast(1)))
        val pay = (offer.pay - cushion).max(BigDecimal.ZERO)
        val hourly = pay.multiply(SIXTY).divide(m, 2, RoundingMode.HALF_UP)
        val perKm = if (totalKm.signum() > 0) pay.divide(totalKm, 2, RoundingMode.HALF_UP) else null

        // The $/hr bar: the courier's own, or lower after waiting (idle-aware rules).
        val bar = if (idle == null) t.minHourly else idleBar(t.minHourly, idle, ctx, offer)
        // Exact checks: pay*60 ≥ bar*minutes  ⇔  pay/minutes*60 ≥ bar.
        val shortHourly = pay.multiply(SIXTY) < bar.multiply(m)
        val shortPerKm = totalKm.signum() > 0 && pay < t.minPerKm.multiply(totalKm)
        // Just under the bar is meh., not nah. (idle-aware rules only).
        val nearHourly = idle != null && shortHourly && idle.mehBand.signum() > 0 &&
            pay.multiply(SIXTY) >= (bar - idle.mehBand).max(BigDecimal.ZERO).multiply(m)
        val (strict, soft) = when (t.priority) {
            Priority.HOURLY -> (shortHourly to Check.HOURLY) to (shortPerKm to Check.PER_KM)
            Priority.PER_KM -> (shortPerKm to Check.PER_KM) to (shortHourly to Check.HOURLY)
        }
        val minPay = t.minPayFor(offer)
        val (ruled, check) = when {
            // By the card's orders; an add-on's pay is only what it adds to a trip already
            // accepted, so it has its own (smaller) minimum, and 0 skips it.
            pay < minPay -> Verdict.RED to Check.MIN_PAY
            // The max is about how far Uber sends you: the card's km, without the drive back.
            t.maxKm != null && offer.km > t.maxKm -> Verdict.RED to Check.MAX_KM
            strict.first && strict.second == Check.HOURLY && nearHourly -> Verdict.YELLOW to Check.HOURLY
            strict.first -> Verdict.RED to strict.second
            soft.first -> Verdict.YELLOW to soft.second
            else -> Verdict.GREEN to null
        }
        // A stack that ends back home costs no drive back: meh. becomes bet! once it clears the floor.
        val floor = idle?.steps?.minOfOrNull { it.hourly }?.min(t.minHourly) ?: t.minHourly
        val homeBonus = idle != null && idle.homeStackBonus && ruled == Verdict.YELLOW && offer.orders >= 2 &&
            area?.first?.home == true && pay.multiply(SIXTY) >= floor.multiply(m)
        val verdict = if (homeBonus) Verdict.GREEN else ruled
        val failed = if (homeBonus) null else check
        val rule = when (failed) {
            Check.MIN_PAY -> when {
                offer.addOn -> "Under ${money(minPay)} add-on minimum"
                offer.orders > 1 -> "Under ${money(minPay)} for ${offer.orders} orders"
                else -> "Under ${money(minPay)} minimum"
            }
            Check.MAX_KM -> "Over ${km(t.maxKm!!)} km max"
            Check.HOURLY -> "Under ${money(bar)}/hr target"
            Check.PER_KM -> "Under ${money(t.minPerKm)}/km target"
            null -> null
        }
        return Evaluation(
            verdict, offer, minutes, hourly, perKm, rule, failed,
            extraKm.takeIf { it.signum() > 0 }, cushion.takeIf { it.signum() > 0 },
            bar = if (idle != null) bar else null,
            idleMin = if (idle != null && !offer.addOn) ctx.idleMin else null,
            returnMin = extraMin.takeIf { it > 0 },
            area = area?.second,
            homeBonus = homeBonus,
        )
    }

    /**
     * The $/hr bar after waiting [ScoreContext.idleMin] minutes: the latest step reached (late at
     * night each comes [IdleRules.lateSoonerMin] sooner), never above [minHourly]. Unknown idle time
     * keeps [minHourly], and so does an add-on: it only comes mid-trip, when Uber hasn't paid yet
     * but the courier isn't waiting.
     */
    fun idleBar(minHourly: BigDecimal, idle: IdleRules, ctx: ScoreContext, offer: Offer? = null): BigDecimal {
        val waited = ctx.idleMin ?: return minHourly
        if (offer?.addOn == true) return minHourly
        val sooner = if (idle.isLate(ctx.hour)) idle.lateSoonerMin.coerceAtLeast(0) else 0
        val step = idle.steps.filter { waited >= it.afterMin - sooner }.maxByOrNull { it.afterMin }
        return step?.hourly?.min(minHourly) ?: minHourly
    }

    /** "15" for whole km, "12.5" otherwise. */
    fun km(v: BigDecimal): String = v.stripTrailingZeros().toPlainString()

    /** "$20" for whole dollars, "$5.50" otherwise. */
    fun money(v: BigDecimal): String {
        val s = v.stripTrailingZeros()
        return if (s.scale() <= 0) "$${s.toBigInteger()}" else "$${v.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
    }
}
