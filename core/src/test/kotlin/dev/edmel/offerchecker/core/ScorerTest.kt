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
        check(offer("6.07", 23, "11.5"), Verdict.RED, "15.83", "0.53", "under \$20/hr")
        assertEquals("\$15.83/hr · \$0.53/km", Scorer.score(offer("6.07", 23, "11.5"), defaults).ratesText)
    }

    @Test fun case2_underMinimumPay_red() =
        check(offer("5.00", 8, "2.0"), Verdict.RED, "37.50", "2.50", "under \$5.50 min")

    @Test fun case3_passesAll_green() =
        check(offer("9.40", 22, "6.8"), Verdict.GREEN, "25.64", "1.38", null)

    @Test fun case4_underPerKmOnly_yellow() =
        check(offer("10.00", 25, "12.0"), Verdict.YELLOW, "24.00", "0.83", "under \$1/km")

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
        check(offer("6.00", 30, "1.0"), Verdict.RED, "12.00", "6.00", "under \$20/hr")

    @Test fun customThresholdsLabelled() {
        val t = Thresholds(BigDecimal("6"), BigDecimal("22.50"), BigDecimal("1.25"), 0)
        assertEquals("under \$22.50/hr", Scorer.score(offer("8.00", 24, "5.0"), t).failedRule)
    }
}
