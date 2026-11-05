package dev.edmel.offerchecker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The idle-aware rules from the nah.meter findings PRD (2026-11-05), with the courier's own bar
 * from that night's log ($17.50/hr). Drop-offs are real lines from offers in OfferParserTest.
 */
class IdleRulesTest {
    private val rules = IdleRules()
    private val t = Thresholds(minHourly = BigDecimal("17.50"), idle = rules)

    private fun offer(pay: String, min: Int, km: String, dropoff: String? = null, orders: Int = 1, addOn: Boolean = false) =
        Offer(BigDecimal(pay), min, BigDecimal(km), orders > 1, dropoff = dropoff, orders = orders, addOn = addOn)

    private fun assertMoney(want: String, got: BigDecimal?) =
        assertTrue("want $want, got $got", got != null && got.compareTo(BigDecimal(want)) == 0)

    private fun bar(idle: Int?, hour: Int? = 20, minHourly: String = "17.50") =
        Scorer.idleBar(BigDecimal(minHourly), rules, ScoreContext(idle, hour))

    /** R2's table: $17.50 until 10 min, $14 until 25 min, then the $12 floor. */
    @Test fun barStepsDownWithIdleTime() {
        assertMoney("17.50", bar(null))
        assertMoney("17.50", bar(0))
        assertMoney("17.50", bar(9))
        assertMoney("14.00", bar(10))
        assertMoney("14.00", bar(24))
        assertMoney("12.00", bar(25))
        assertMoney("12.00", bar(180))
    }

    /** After 10pm each step starts 5 minutes sooner; the night runs until 5am. */
    @Test fun lateNightStepsComeSooner() {
        assertMoney("17.50", bar(4, hour = 23))
        assertMoney("14.00", bar(5, hour = 22))
        assertMoney("12.00", bar(20, hour = 23))
        assertMoney("14.00", bar(5, hour = 2))
        assertMoney("17.50", bar(5, hour = 21))
        assertMoney("17.50", bar(5, hour = 5))
        // Unknown hour: not late.
        assertMoney("17.50", bar(5, hour = null))
    }

    /** The steps only ever lower the courier's own bar. */
    @Test fun barNeverAboveTheCouriersOwn() {
        assertMoney("12.50", bar(10, minHourly = "12.50"))
        assertMoney("12.00", bar(25, minHourly = "12.50"))
    }

    @Test fun waitingLowersTheBar() {
        // $7 for 30 min: $14.00/hr.
        val o = offer("7.00", 30, "5.0")
        val fresh = Scorer.score(o, t, ScoreContext(0, 20))
        assertEquals(Verdict.RED, fresh.verdict)
        assertEquals("Under \$17.50/hr target", fresh.failedRule)
        val waited = Scorer.score(o, t, ScoreContext(12, 20))
        assertEquals(Verdict.GREEN, waited.verdict)
        assertMoney("14.00", waited.bar)
        assertEquals(12, waited.idleMin)
        // The fixed rules know nothing of waiting.
        val fixed = Scorer.score(o, t.copy(idle = null), ScoreContext(12, 20))
        assertEquals(Verdict.RED, fixed.verdict)
        assertNull(fixed.bar)
        assertNull(fixed.idleMin)
    }

    /** Up to $2/hr under the bar is meh.; exactly $2 under still is. */
    @Test fun mehBand() {
        val ctx = ScoreContext(30, 20) // bar $12
        val edge = Scorer.score(offer("10.00", 60, "5.0"), t, ctx)
        assertEquals(Verdict.YELLOW, edge.verdict)
        assertEquals(Check.HOURLY, edge.failed)
        assertEquals("Under \$12/hr target", edge.failedRule)
        assertEquals(Verdict.RED, Scorer.score(offer("9.99", 60, "5.0"), t, ctx).verdict)
        // A $0 band is off: any miss is nah.
        val noBand = t.copy(idle = rules.copy(mehBand = BigDecimal.ZERO))
        assertEquals(Verdict.RED, Scorer.score(offer("11.99", 60, "5.0"), noBand, ctx).verdict)
        // With $/km strict, $/hr is only ever meh.
        val perKm = t.copy(priority = Priority.PER_KM)
        assertEquals(Verdict.YELLOW, Scorer.score(offer("6.00", 60, "5.0"), perKm, ctx).verdict)
    }

    @Test fun areaNames() {
        fun area(dropoff: String?) = returnAreaFor(dropoff, rules.areas)?.second
        assertEquals("Richmond Hill", area("Manorheights St & Martini Dr, Richmond Hill"))
        assertEquals("Toronto", area("King St E& Sherbourne St, Toronto"))
        assertEquals("Markham", area("Gandhi Ln, Markham"))
        assertEquals("North York", area("Fennell St & Sheppard Ave W, North York"))
        assertEquals("Stouffville", area("Main St, Whitchurch-Stouffville"))
        // One text-recognition slip in a longer name.
        assertEquals("Markham", area("Yonge St, Markhan"))
        // Not listed, no area part, or not read: no area.
        assertNull(area("Butterfly Hts & McMichael Ave, Kleinburg"))
        assertNull(area("Black Creek Blvd & Jane St, York"))
        assertNull(area("5831 THORNHILL"))
        assertNull(area(null))
        assertTrue(returnAreaFor("Esther Shiner Blvd & McMahon Dr, North York", rules.areas)!!.first.home)
    }

    /** R3: a drop-off in a listed area adds its drive back, km at the offer's own pace. */
    @Test fun driveBackByArea() {
        val rh = Scorer.score(offer("15.00", 30, "12.0", "Manorheights St & Martini Dr, Richmond Hill"), t)
        assertEquals(15, rh.returnMin)
        assertEquals(45, rh.effectiveMinutes)
        assertMoney("6.00", rh.extraKm)
        assertMoney("20.00", rh.hourly)
        assertMoney("0.83", rh.perKm)
        assertEquals("Richmond Hill", rh.area)
        assertEquals(Verdict.YELLOW, rh.verdict)

        val downtown = Scorer.score(offer("20.00", 35, "20.0", "King St E& Sherbourne St, Toronto"), t)
        assertEquals(25, downtown.returnMin)
        assertEquals(60, downtown.effectiveMinutes)
        assertMoney("14.29", downtown.extraKm)
        assertMoney("0.58", downtown.perKm)
    }

    /** A "…, Toronto" drop-off on a short trip is near home, not downtown: the km gate still applies. */
    @Test fun noDriveBackOnShortTrips() {
        val e = Scorer.score(offer("8.00", 15, "6.0", "Yonge St, Toronto"), t)
        assertNull(e.returnMin)
        assertNull(e.extraKm)
        assertEquals(15, e.effectiveMinutes)
        assertEquals("Toronto", e.area)
    }

    /** An area that isn't listed, or no drop-off read, keeps the % rule. */
    @Test fun unknownAreaKeepsThePercentRule() {
        val pct = t.copy(extraKmPercent = 50)
        for (dropoff in listOf("Butterfly Hts & McMichael Ave, Kleinburg", null)) {
            val e = Scorer.score(offer("20.00", 40, "20.0", dropoff), pct)
            assertEquals(dropoff, 20, e.returnMin)
            assertMoney("10.0", e.extraKm)
            assertNull(e.area)
        }
    }

    @Test fun fixedRulesIgnoreTheArea() {
        val e = Scorer.score(offer("20.00", 40, "20.0", "Main St, Whitchurch-Stouffville"), Thresholds())
        assertNull(e.area)
        assertNull(e.returnMin)
        assertEquals(40, e.effectiveMinutes)
    }

    /** R4: a stack ending in North York goes from meh. to bet!. */
    @Test fun stackEndingHome() {
        // 26 min + 4 min pickup waits = 30: $30/hr, but $0.94/km.
        val home = Scorer.score(offer("15.00", 26, "16.0", "Esther Shiner Blvd & McMahon Dr, North York", orders = 2), t)
        assertEquals(Verdict.GREEN, home.verdict)
        assertTrue(home.homeBonus)
        assertNull(home.failed)
        assertNull(home.returnMin)
        assertEquals("North York", home.area)

        // The same stack ending in Markham drives back 15 min: meh., no bonus.
        val markham = Scorer.score(offer("15.00", 26, "16.0", "Gandhi Ln, Markham", orders = 2), t)
        assertEquals(Verdict.YELLOW, markham.verdict)
        assertFalse(markham.homeBonus)
        assertEquals(15, markham.returnMin)

        // One order: no bonus.
        val single = Scorer.score(offer("15.00", 30, "16.0", "Esther Shiner Blvd & McMahon Dr, North York"), t)
        assertEquals(Verdict.YELLOW, single.verdict)
        assertFalse(single.homeBonus)

        // nah. stays nah.
        val red = Scorer.score(offer("9.50", 40, "16.0", "Esther Shiner Blvd & McMahon Dr, North York", orders = 2), t)
        assertEquals(Verdict.RED, red.verdict)

        // Under the floor ($12), meh. from the band stays meh.
        val under = Scorer.score(offer("11.00", 56, "8.0", "Olive Ave & Wilfred Ave, North York", orders = 2), t, ScoreContext(30, 20))
        assertEquals(Verdict.YELLOW, under.verdict)
        assertFalse(under.homeBonus)

        // Turned off.
        val off = t.copy(idle = rules.copy(homeStackBonus = false))
        assertEquals(Verdict.YELLOW, Scorer.score(offer("15.00", 26, "16.0", "Esther Shiner Blvd & McMahon Dr, North York", orders = 2), off).verdict)
    }

    /** Add-ons come mid-trip, while Uber hasn't paid: waiting doesn't lower their bar. */
    @Test fun addOnsIgnoreIdleTime() {
        // $4 for 15 min: $16/hr; $15.50 is in the band under $17.50.
        val e = Scorer.score(offer("4.00", 15, "3.0", "Yonge St, Toronto", addOn = true), t, ScoreContext(30, 20))
        assertEquals(Verdict.YELLOW, e.verdict)
        assertMoney("17.50", e.bar)
        assertNull(e.idleMin)
        assertNull(e.area)
    }
}
