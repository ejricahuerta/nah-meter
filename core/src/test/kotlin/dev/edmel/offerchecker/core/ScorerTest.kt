package dev.edmel.offerchecker.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** The PRD's acceptance test cases, default thresholds, buffer 0 unless noted. */
class ScorerTest {
    private val defaults = Thresholds()

    private fun offer(pay: String, min: Int, km: String) = Offer(BigDecimal(pay), min, BigDecimal(km))

    private fun check(
        o: Offer, verdict: Verdict, hourly: String, perKm: String,
        failed: String?, t: Thresholds = defaults,
    ) {
        val e = Scorer.score(o, t)
        assertEquals(verdict, e.verdict)
        assertEquals(BigDecimal(hourly), e.hourly)
        assertEquals(BigDecimal(perKm), e.perKm)
        assertEquals(failed, e.failedRule)
    }

    @Test fun case1_realOffer_underHourly_red() {
        check(offer("6.07", 23, "11.5"), Verdict.RED, "15.83", "0.53", "Under \$20/hr target")
        assertEquals("\$15.83/hr · \$0.53/km", Scorer.score(offer("6.07", 23, "11.5"), defaults).ratesText)
    }

    @Test fun case2_underMinimumPay_red() =
        check(offer("5.00", 8, "2.0"), Verdict.RED, "37.50", "2.50", "Under \$5.50 minimum")

    @Test fun case3_passesAll_green() =
        check(offer("9.40", 22, "6.8"), Verdict.GREEN, "25.64", "1.38", null)

    @Test fun case4_underPerKmOnly_yellow() =
        check(offer("10.00", 25, "12.0"), Verdict.YELLOW, "24.00", "0.83", "Under \$1/km target")

    @Test fun case5_exactly20PerHour_passes() =
        check(offer("8.00", 24, "5.0"), Verdict.GREEN, "20.00", "1.60", null)

    @Test fun case6_exactlyMinimumPay_passes() =
        check(offer("5.50", 15, "4.0"), Verdict.GREEN, "22.00", "1.38", null)

    @Test fun case7_waitBufferApplied() {
        val t = defaults.copy(waitBufferMin = 5)
        check(offer("12.00", 30, "8.0"), Verdict.GREEN, "20.57", "1.50", null, t)
        assertEquals(35, Scorer.score(offer("12.00", 30, "8.0"), t).effectiveMinutes)
    }

    @Test fun case8_textMissing_grey() {
        val e = Scorer.evaluate(OfferParser.parse(listOf("23 min (11.5 km) total")), defaults)
        assertEquals(Verdict.GREY, e!!.verdict)
        assertEquals("Can't read", e.ratesText)
    }

    @Test fun hourlyFailIsRedEvenWithHighPerKm() =
        check(offer("6.00", 30, "1.0"), Verdict.RED, "12.00", "6.00", "Under \$20/hr target")

    @Test fun failedCheckIsNamed() {
        assertEquals(Check.MIN_PAY, Scorer.score(offer("5.00", 8, "2.0"), defaults).failed)
        assertEquals(Check.HOURLY, Scorer.score(offer("6.07", 23, "11.5"), defaults).failed)
        assertEquals(Check.PER_KM, Scorer.score(offer("10.00", 25, "12.0"), defaults).failed)
        assertEquals(null, Scorer.score(offer("9.40", 22, "6.8"), defaults).failed)
    }

    @Test fun customThresholdsLabelled() {
        val t = Thresholds(BigDecimal("6"), BigDecimal("22.50"), BigDecimal("1.25"), 0)
        assertEquals("Under \$22.50/hr target", Scorer.score(offer("8.00", 24, "5.0"), t).failedRule)
    }

    /** 2026-10-28 6:21: a $5.10 add-on is under the $5.50 minimum, but the minimum skips add-ons. */
    @Test fun addOnSkipsMinimumPay() {
        val addOn = Offer(BigDecimal("5.10"), 13, BigDecimal("3.5"), addOn = true)
        val e = Scorer.score(addOn, defaults)
        assertEquals(Verdict.GREEN, e.verdict)
        assertEquals(null, e.failed)
        // Still judged on the hour and km.
        assertEquals(Check.HOURLY, Scorer.score(addOn.copy(minutes = 20), defaults).failed)
        // The same numbers as a whole trip are under the minimum.
        assertEquals(Check.MIN_PAY, Scorer.score(addOn.copy(addOn = false), defaults).failed)
    }

    /** Multi-order offers count 2 min of waiting for food per order; single orders and add-ons don't. */
    @Test fun pickupWaitPerOrder() {
        val t = Thresholds()
        val two = Scorer.score(Offer(BigDecimal("20.00"), 56, BigDecimal("10"), orders = 2), t)
        assertEquals(60, two.effectiveMinutes)
        assertEquals(Verdict.GREEN, two.verdict) // exactly $20.00/hr passes
        assertEquals(56, Scorer.score(Offer(BigDecimal("20.00"), 56, BigDecimal("10")), t).effectiveMinutes)
        assertEquals(13, Scorer.score(Offer(BigDecimal("5.10"), 13, BigDecimal("3.5"), addOn = true), t).effectiveMinutes)
        assertEquals(56, Scorer.score(Offer(BigDecimal("20.00"), 56, BigDecimal("10"), orders = 2), t.copy(perOrderWaitMin = 0)).effectiveMinutes)
    }

    /** Max km: over it is red, exactly it passes, off by default; checked right after the minimum. */
    @Test fun maxKm() {
        val t = Thresholds(maxKm = BigDecimal("15"))
        val far = Scorer.score(offer("40.00", 40, "16"), t)
        assertEquals(Verdict.RED, far.verdict)
        assertEquals(Check.MAX_KM, far.failed)
        assertEquals("Over 15 km max", far.failedRule)
        assertEquals(Verdict.GREEN, Scorer.score(offer("40.00", 40, "15"), t).verdict)
        assertEquals(Verdict.GREEN, Scorer.score(offer("40.00", 40, "16"), defaults).verdict)
        // The minimum comes first; a good $/hr doesn't save a trip that's too far.
        assertEquals(Check.MIN_PAY, Scorer.score(offer("5.00", 10, "16"), t).failed)
        assertEquals(Check.MAX_KM, Scorer.score(offer("60.00", 30, "16"), t).failed)
        // Add-ons are checked on the km they add; several orders on the card's total.
        assertEquals(Check.MAX_KM, Scorer.score(Offer(BigDecimal("20.00"), 20, BigDecimal("16"), addOn = true), t).failed)
        assertEquals(Check.MAX_KM, Scorer.score(Offer(BigDecimal("40.00"), 40, BigDecimal("16"), orders = 2), t).failed)
        assertEquals("Over 12.5 km max", Scorer.score(offer("40.00", 40, "16"), Thresholds(maxKm = BigDecimal("12.5"))).failedRule)
    }

    /** The unpaid drive back adds the same share of km and of Uber's minutes to the rates. */
    @Test fun extraKmLowersRates() {
        val e = Scorer.score(offer("6.07", 23, "11.5"), Thresholds(extraKmPercent = 50))
        assertEquals(35, e.effectiveMinutes) // 23 + 11.5 rounded half-up to 12
        assertEquals(BigDecimal("10.41"), e.hourly)
        assertEquals(BigDecimal("0.35"), e.perKm) // 6.07 / 17.25
        assertEquals(0, BigDecimal("5.75").compareTo(e.extraKm))
        assertEquals(Verdict.RED, e.verdict)

        val off = Scorer.score(offer("6.07", 23, "11.5"), defaults)
        assertEquals(23, off.effectiveMinutes)
        assertEquals(null, off.extraKm)

        // Add-ons don't get it: the trip they add to already counts its way back.
        val addOn = Scorer.score(Offer(BigDecimal("5.10"), 13, BigDecimal("3.5"), addOn = true), Thresholds(extraKmPercent = 50))
        assertEquals(13, addOn.effectiveMinutes)
        assertEquals(BigDecimal("1.46"), addOn.perKm)

        // It can turn a green into a yellow on $/km alone.
        val green = offer("30.00", 30, "25")
        assertEquals(Verdict.GREEN, Scorer.score(green, defaults).verdict)
        assertEquals(Check.PER_KM, Scorer.score(green, Thresholds(extraKmPercent = 50)).failed)

        // The max checks the trip alone, not the drive back.
        assertEquals(null, Scorer.score(offer("40.00", 40, "11.5"), Thresholds(maxKm = BigDecimal("12"), extraKmPercent = 50)).failed)
    }

    /** The drive back only counts on trips over 10 km by default; exactly 10 km doesn't. */
    @Test fun extraKmOnlyOverSetKm() {
        val t = Thresholds(extraKmPercent = 50)
        assertEquals(null, Scorer.score(offer("10.00", 20, "9"), t).extraKm)
        assertEquals(20, Scorer.score(offer("10.00", 20, "10"), t).effectiveMinutes)
        val over = Scorer.score(offer("10.00", 20, "10.5"), t)
        assertEquals(0, BigDecimal("5.25").compareTo(over.extraKm))
        assertEquals(30, over.effectiveMinutes)
        // 0 counts it on every trip.
        assertEquals(0, BigDecimal("4.5").compareTo(Scorer.score(offer("10.00", 20, "9"), t.copy(extraKmOverKm = 0)).extraKm))
    }
}
