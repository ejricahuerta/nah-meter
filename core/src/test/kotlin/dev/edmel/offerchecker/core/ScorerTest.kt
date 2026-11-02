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

    /**
     * 2026-10-28 6:21: a $5.10 add-on is under the $5.50 minimum for a whole trip, but add-ons have
     * their own minimum ($4 by default, decided 2026-11-02); 0 skips it.
     */
    @Test fun addOnHasItsOwnMinimum() {
        val addOn = Offer(BigDecimal("5.10"), 13, BigDecimal("3.5"), addOn = true)
        val e = Scorer.score(addOn, defaults)
        assertEquals(Verdict.GREEN, e.verdict)
        assertEquals(null, e.failed)
        // Still judged on the hour and km.
        assertEquals(Check.HOURLY, Scorer.score(addOn.copy(minutes = 20), defaults).failed)
        // The same numbers as a whole trip are under the minimum.
        assertEquals(Check.MIN_PAY, Scorer.score(addOn.copy(addOn = false), defaults).failed)

        // Under the add-on minimum.
        val small = Offer(BigDecimal("3.50"), 8, BigDecimal("2.0"), addOn = true)
        check(small, Verdict.RED, "26.25", "1.75", "Under \$4 add-on minimum")
        // 0 skips it, as add-ons did before.
        check(small, Verdict.GREEN, "26.25", "1.75", null, defaults.copy(minAddOn = BigDecimal.ZERO))
        // 2026-10-31 01:56 add-on, $5.47 for 15 min and 5.5 km: clears $4, short of a $6 add-on minimum.
        val real = Offer(BigDecimal("5.47"), 15, BigDecimal("5.5"), addOn = true)
        assertEquals(Check.PER_KM, Scorer.score(real, defaults).failed)
        assertEquals("Under \$6 add-on minimum", Scorer.score(real, defaults.copy(minAddOn = BigDecimal("6"))).failedRule)
    }

    /**
     * Each stack has its own minimum: 1 order $5.50, 2 orders $9, 3 or more $12 (decided 2026-11-02).
     * Offers from the 2026-10-30/31 log; their logged minutes include the 2 min per order of
     * waiting for food, so Uber's own minutes are 4 (2 orders) or 6 (3 orders) fewer.
     */
    @Test fun minimumPerStack() {
        // 2026-10-30 13:09: $6.09, 2 orders, 35 min logged, 7.0 km.
        check(
            Offer(BigDecimal("6.09"), 31, BigDecimal("7.0"), orders = 2),
            Verdict.RED, "10.44", "0.87", "Under \$9 for 2 orders",
        )
        // Exactly the minimum passes.
        assertEquals(Verdict.GREEN, Scorer.score(Offer(BigDecimal("9.00"), 20, BigDecimal("5"), orders = 2), defaults).verdict)
        assertEquals(Check.MIN_PAY, Scorer.score(Offer(BigDecimal("8.99"), 20, BigDecimal("5"), orders = 2), defaults).failed)

        // 2026-10-31 02:57: $14.02, 3 orders, 56 min logged, 9.9 km: clears $12, short on $/hr.
        val three = Offer(BigDecimal("14.02"), 50, BigDecimal("9.9"), orders = 3)
        check(three, Verdict.RED, "15.02", "1.42", "Under \$20/hr target")
        check(three, Verdict.RED, "15.02", "1.42", "Under \$15 for 3 orders", defaults.copy(minPay3 = BigDecimal("15")))
        // 4 orders use the 3+ minimum.
        val four = three.copy(orders = 4)
        assertEquals("Under \$15 for 4 orders", Scorer.score(four, defaults.copy(minPay3 = BigDecimal("15"))).failedRule)

        // A single order still uses $5.50: $8, 24 min, 5 km is under the 2-order $9 but passes.
        assertEquals(Verdict.GREEN, Scorer.score(offer("8.00", 24, "5.0"), defaults).verdict)

        assertEquals(BigDecimal("5.50"), defaults.minPayFor(offer("1", 1, "1")))
        assertEquals(BigDecimal("9.00"), defaults.minPayFor(three.copy(orders = 2)))
        assertEquals(BigDecimal("12.00"), defaults.minPayFor(three))
        assertEquals(BigDecimal("4.00"), defaults.minPayFor(three.copy(orders = 1, addOn = true)))
    }

    /** The tip cushion comes off the pay for each order before every rule (decided 2026-11-02, off by default). */
    @Test fun tipCushion() {
        // PRD case 3, $9.40 for 22 min and 6.8 km: scored on $7.40 it still makes $20/hr.
        val case3 = offer("9.40", 22, "6.8")
        check(case3, Verdict.GREEN, "20.18", "1.09", null, defaults.copy(tipCushion = BigDecimal("2")))
        assertEquals(0, BigDecimal("2").compareTo(Scorer.score(case3, defaults.copy(tipCushion = BigDecimal("2"))).tipCushion))
        // On $6.90 it doesn't.
        check(case3, Verdict.RED, "18.82", "1.01", "Under \$20/hr target", defaults.copy(tipCushion = BigDecimal("2.50")))

        // 2026-10-30 11:50: $11.05, 2 orders, 37 min logged, 7.7 km. Two tips at $2 → scored on $7.05.
        val two = Scorer.score(Offer(BigDecimal("11.05"), 33, BigDecimal("7.7"), orders = 2), defaults.copy(tipCushion = BigDecimal("2")))
        assertEquals(Verdict.RED to "Under \$9 for 2 orders", two.verdict to two.failedRule)
        assertEquals(BigDecimal("11.43"), two.hourly)
        assertEquals(0, BigDecimal("4").compareTo(two.tipCushion))

        // An add-on is one order.
        val addOn = Scorer.score(Offer(BigDecimal("5.10"), 13, BigDecimal("3.5"), addOn = true), defaults.copy(tipCushion = BigDecimal("1")))
        assertEquals(0, BigDecimal("1").compareTo(addOn.tipCushion))
        // $4.10 clears the $4 add-on minimum, but at $18.92/hr it's under $20.
        assertEquals(Check.HOURLY, addOn.failed)
        assertEquals(BigDecimal("18.92"), addOn.hourly)

        // More cushion than pay scores $0, never less.
        val none = Scorer.score(offer("5.00", 8, "2.0"), defaults.copy(tipCushion = BigDecimal("10")))
        assertEquals(BigDecimal("0.00"), none.hourly)
        assertEquals(Check.MIN_PAY, none.failed)

        // Off by default.
        assertEquals(null, Scorer.score(case3, defaults).tipCushion)
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

    /** Strict on $/km (busy times): missing $/km is red and missing $/hr only yellow; the default is the reverse. */
    @Test fun priorityPicksTheStrictRate() {
        val perKm = defaults.copy(priority = Priority.PER_KM)

        // Short on $/hr, plenty per km.
        val slow = offer("6.00", 30, "1.0")
        check(slow, Verdict.RED, "12.00", "6.00", "Under \$20/hr target")
        check(slow, Verdict.YELLOW, "12.00", "6.00", "Under \$20/hr target", perKm)
        assertEquals(Check.HOURLY, Scorer.score(slow, perKm).failed)

        // Short on $/km, fine per hour.
        val far = offer("10.00", 25, "12.0")
        check(far, Verdict.YELLOW, "24.00", "0.83", "Under \$1/km target")
        check(far, Verdict.RED, "24.00", "0.83", "Under \$1/km target", perKm)
        assertEquals(Check.PER_KM, Scorer.score(far, perKm).failed)

        // Short on both: red on either priority, named after the strict rate.
        val both = offer("6.00", 30, "12.0")
        assertEquals(Check.HOURLY, Scorer.score(both, defaults).failed)
        assertEquals(Check.PER_KM, Scorer.score(both, perKm).failed)
        assertEquals(Verdict.RED, Scorer.score(both, perKm).verdict)

        // Exactly $20/hr and $1/km passes on both.
        check(offer("8.00", 24, "8.0"), Verdict.GREEN, "20.00", "1.00", null)
        check(offer("8.00", 24, "8.0"), Verdict.GREEN, "20.00", "1.00", null, perKm)

        // The minimum and the max km still come first.
        assertEquals(Check.MIN_PAY, Scorer.score(offer("5.00", 8, "20.0"), perKm).failed)
        assertEquals(Check.MAX_KM, Scorer.score(offer("10.00", 25, "16"), perKm.copy(maxKm = BigDecimal("15"))).failed)
    }

    /** Strict on $/km with the other rules: add-ons still have their own minimum, and the drive back counts in the $/km. */
    @Test fun priorityWithAddOnsAndDriveBack() {
        val perKm = defaults.copy(priority = Priority.PER_KM)

        // 2026-10-28 add-on, $5.10 for 13 min and 3.5 km: under the $5.50 minimum, but clears the $4 add-on minimum.
        val addOn = Offer(BigDecimal("5.10"), 13, BigDecimal("3.5"), addOn = true)
        assertEquals(Verdict.GREEN, Scorer.score(addOn, perKm).verdict)
        // The same add-on stretched to 6 km is short per km: red when $/km is strict.
        val longer = Scorer.score(addOn.copy(km = BigDecimal("6")), perKm)
        assertEquals(Verdict.RED to Check.PER_KM, longer.verdict to longer.failed)

        // $30, 30 min, 25 km clears $1/km alone, but with half the km again for the drive back
        // (37.5 km, $0.80/km) it's short: eh. by default, nah. with $/km strict.
        val far = offer("30.00", 30, "25")
        val back = Thresholds(extraKmPercent = 50)
        assertEquals(Verdict.GREEN, Scorer.score(far, perKm).verdict)
        assertEquals(Verdict.YELLOW, Scorer.score(far, back).verdict)
        val strict = Scorer.score(far, back.copy(priority = Priority.PER_KM))
        assertEquals(Verdict.RED to Check.PER_KM, strict.verdict to strict.failed)
        assertEquals(BigDecimal("0.80"), strict.perKm)
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
