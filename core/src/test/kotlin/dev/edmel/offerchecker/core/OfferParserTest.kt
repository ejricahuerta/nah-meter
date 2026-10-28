package dev.edmel.offerchecker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class OfferParserTest {
    private fun read(vararg texts: String): Offer {
        val r = OfferParser.parse(texts.toList())
        assertTrue("expected Read, got $r", r is ReadResult.Read)
        return (r as ReadResult.Read).offer
    }

    @Test fun realOfferCard() {
        val o = read("Delivery", "$6.07", "including estimated tip", "23 min (11.5 km) total", "Accept")
        assertEquals(BigDecimal("6.07"), o.pay)
        assertEquals(23, o.minutes)
        assertEquals(BigDecimal("11.5"), o.km)
        assertFalse(o.stacked)
    }

    @Test fun payWithTipOnSameLineAndMultilineNode() {
        val o = read("$9.40 including estimated tip\n22 min (6.8 km) total")
        assertEquals(BigDecimal("9.40"), o.pay)
        assertEquals(22, o.minutes)
    }

    @Test fun spaceAfterDollarAndWholeDollars() {
        assertEquals(BigDecimal("12"), read("$ 12", "30 min (8 km) total").pay)
    }

    @Test fun largestDollarValueIsPay() {
        assertEquals(BigDecimal("8.25"), read("$1.50", "$8.25", "18 min (4.2 km) total").pay)
    }

    @Test fun ignoresDollarsInsideOtherText() {
        // "$2.00 extra" and street names are not standalone pay amounts.
        assertEquals(BigDecimal("7.10"), read("Includes $2.00 extra", "123 King St W", "$7.10", "20 min (5 km) total").pay)
    }

    @Test fun noTripLineIsNoOffer() {
        assertEquals(ReadResult.NoOffer, OfferParser.parse(listOf("$45.20", "Today's earnings")))
        assertEquals(ReadResult.NoOffer, OfferParser.parse(emptyList()))
    }

    @Test fun missingPayIsUnreadable() {
        assertTrue(OfferParser.parse(listOf("23 min (11.5 km) total")) is ReadResult.Unreadable)
    }

    @Test fun garbledTripLineIsUnreadable() {
        assertTrue(OfferParser.parse(listOf("$6.07", "— min (— km) total")) is ReadResult.Unreadable)
    }

    @Test fun stackedOffer() {
        val o = read("2 orders", "$14.80", "41 min (13.0 km) total")
        assertTrue(o.stacked)
        assertTrue(read("×2", "$14.80", "41 min (13.0 km) total").stacked)
    }

    @Test fun nonBreakingSpacesAndCommaDecimal() {
        val o = read("$ 6.07", "23 min (11,5 km) total")
        assertEquals(BigDecimal("6.07"), o.pay)
        assertEquals(BigDecimal("11.5"), o.km)
    }

    @Test fun tripLineDetection() {
        assertTrue(OfferParser.isTripLine("23 min (11.5 km) total"))
        assertFalse(OfferParser.isTripLine("King St W"))
        assertTrue(OfferParser.containsPay(listOf("x", "$6.07")))
    }

    /** Text from the 2026-10-28 $5.50 offer screenshot. */
    @Test fun exclusiveOfferScreenshot() {
        val r = OfferParser.parse(
            listOf(
                "Delivery", "Exclusive", "$5.50", "including estimated tip", "24 min (10.0 km) total",
                "McDonald's (Steeles & Hilda)", "Yonge St, Toronto", "Accept",
            ),
        )
        val e = Scorer.evaluate(r, Thresholds())!!
        assertEquals(Verdict.RED, e.verdict)
        assertEquals("\$13.75/hr · \$0.55/km", e.ratesText)
        assertEquals("under \$20/hr", e.failedRule)
    }

    @Test fun paySplitAcrossTextPieces() {
        assertEquals(BigDecimal("5.50"), read("$", "5.50", "24 min (10.0 km) total").pay)
    }
}
