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

    /** OCR lines from the 2026-10-28 $5.58 pop-up over another app (1000 px tall screen). */
    @Test fun ocrPopupPicksOnlyTheCard() {
        val lines = listOf(
            OcrLine("$9.99 deals near you", 100, 120), // other app's text, far above the card
            OcrLine("$3.00", 250, 270), // outside the band
            OcrLine("Delivery", 580, 600),
            OcrLine("Exclusive", 580, 600),
            OcrLine("$5.58", 630, 665),
            OcrLine("including estimated tip", 690, 705),
            OcrLine("27 min (13.7 km) total", 750, 765),
            OcrLine("Burger King (1607 Birchmount Road)", 800, 815),
            OcrLine("Accept", 900, 915),
        )
        val card = OfferParser.cardFromOcr(lines, 1000)!!
        assertEquals(listOf("Delivery", "Exclusive", "$5.58", "including estimated tip", "27 min (13.7 km) total"), card)
        val e = Scorer.evaluate(OfferParser.parse(card), Thresholds())!!
        assertEquals(Verdict.RED, e.verdict)
        assertEquals("\$12.40/hr · \$0.41/km", e.ratesText)
    }

    @Test fun ocrWithoutTripLineIsNull() {
        assertEquals(null, OfferParser.cardFromOcr(listOf(OcrLine("$5.58", 0, 10)), 1000))
    }

    /** The 2026-10-28 "Package (2)" offer that showed grey: $11.59, 43 min, 13.6 km. */
    @Test fun stackedPackageOffer() {
        val o = read("Package (2)", "$11.59", "including estimated tip", "43 min (13.6 km) total", "5831 THORNHILL", "Match")
        assertEquals(BigDecimal("11.59"), o.pay)
        assertEquals(43, o.minutes)
        assertEquals(BigDecimal("13.6"), o.km)
        assertTrue(o.stacked)
        val e = Scorer.score(o, Thresholds())
        assertEquals(Verdict.RED, e.verdict)
        assertEquals("\$16.17/hr · \$0.85/km", e.ratesText)
    }

    @Test fun textRecognitionSlips() {
        for (trip in listOf(
            "43 min C13.6 km) total", "43min (13.6km)total", "43 min [13.6 km] total",
            ") 43 min (13.6 km) total", "43 min 13.6 km total", "43 min (13,6 km) total",
        )) {
            val o = read("|$11.59", "including estimated tipl", trip)
            assertEquals(trip, BigDecimal("11.59"), o.pay)
            assertEquals(trip, 43, o.minutes)
            assertEquals(trip, BigDecimal("13.6"), o.km)
        }
    }

    @Test fun singleOrderNotStacked() {
        assertFalse(read("Delivery", "Exclusive", "$5.58", "27 min (13.7 km) total").stacked)
    }

    /** Pay as text recognition actually read it on the 2026-10-28 $11.59 offer, and similar slips. */
    @Test fun payTextRecognitionSlips() {
        for ((seen, pay) in listOf(
            "$1l.59" to "11.59", "$1159" to "11.59", "$11,59" to "11.59", "\$I1.59" to "11.59",
            "\$S.50" to "5.50", "$6.O5" to "6.05", "$550" to "5.50",
        )) {
            assertEquals(seen, BigDecimal(pay), read("O Package (2)", seen, "including estimated tip", "O 43 min (13.6 km) total").pay)
        }
        // Whole-dollar amounts without cents are kept as they are.
        assertEquals(BigDecimal("12"), read("$12", "30 min (8 km) total").pay)
    }

    /** The exact screenshot lines logged for the $11.59 offer. */
    @Test fun loggedScreenshotOf11_59Offer() {
        val lines = listOf(
            "Park", "THLD", "A", "5:28 >", "AF", "O Package (2)", "$1l.59", "ON-407 E", "including estimated tip",
            "STEELES- AVE-E", "O 43 min (13.6 km) total", "5831 THORNHILL", "BAT", "S Customer verification", "Match",
        ).mapIndexed { i, t -> OcrLine(t, 1000 + i * 40, 1030 + i * 40) }
        val card = OfferParser.cardFromOcr(lines, 2424)!!
        val e = Scorer.evaluate(OfferParser.parse(card), Thresholds())!!
        assertEquals(Verdict.RED, e.verdict)
        assertEquals("\$16.17/hr · \$0.85/km", e.ratesText)
        assertTrue(e.offer!!.stacked)
    }
}
