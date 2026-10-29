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
        assertEquals("Under \$20/hr target", e.failedRule)
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
        assertEquals(
            listOf(
                "Delivery", "Exclusive", "$5.58", "including estimated tip", "27 min (13.7 km) total",
                "Burger King (1607 Birchmount Road)", "Accept",
            ),
            card,
        )
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

    /**
     * 2026-10-28 5:45: the $9.07 pop-up over a web page showing sample badges ("$25.64/hr").
     * The small $25.64 behind the card was taken as the pay (green $49.63/hr). The card's own
     * pay is far larger text, so it must win. Positions are from that screenshot (2000 px tall).
     */
    @Test fun ocrIgnoresSmallerDollarAmountsBehindThePopup() {
        val lines = listOf(
            OcrLine("On a live offer: the badge over the Uber Driver offer card", 755, 778),
            OcrLine("$15.83", 1016, 1032), OcrLine("/hr", 1020, 1030),
            OcrLine("$25.64", 1016, 1032), OcrLine("/hr", 1020, 1030),
            OcrLine("$24.00", 1016, 1032), OcrLine("/hr", 1020, 1030),
            OcrLine("Delivery", 1130, 1170),
            OcrLine("$9.07", 1205, 1290),
            OcrLine("including estimated tip", 1335, 1370),
            OcrLine("31 min (19.2 km) total", 1450, 1490),
            OcrLine("Burger King (1607 Birchmount Road)", 1550, 1590),
            OcrLine("Match", 1800, 1840),
        )
        val e = Scorer.evaluate(OfferParser.parse(OfferParser.cardFromOcr(lines, 2000)!!), Thresholds())!!
        assertEquals(BigDecimal("9.07"), e.offer!!.pay)
        // The card's top edge is about one row above the 85 px pay line.
        assertEquals(1205 - 119, OfferParser.ocrCardTop(lines, 2000))
        assertEquals(Verdict.RED, e.verdict)
        assertEquals("\$17.55/hr · \$0.47/km", e.ratesText)
    }

    /** 2026-10-28 4:37: the $10.07 two-package offer; map labels "ort", "UI" sit left of the card. */
    @Test fun restaurantAndDropoffFromScreenshot() {
        val lines = listOf(
            OcrLine("$10.07", 1215, 1290, 72, 352),
            OcrLine("including estimated tip", 1335, 1370, 88, 460),
            OcrLine("ort", 1470, 1505, 0, 40),
            OcrLine("O 48 min (14.8 km) total", 1450, 1490, 72, 530),
            OcrLine("Lazeez Shawarma (2900 Steeles)", 1550, 1590, 140, 735),
            OcrLine("UI", 1590, 1620, 0, 30),
            OcrLine("Annibale Dr & Bembridge Dr,", 1615, 1655, 140, 650),
            OcrLine("Markham", 1672, 1712, 140, 312),
            OcrLine("Accept", 1800, 1840, 380, 510),
            OcrLine("Sunnybrook Park", 1905, 1930, 300, 555),
        )
        val r = OfferParser.parse(OfferParser.cardFromOcr(lines, 2000)!!) as ReadResult.Read
        assertEquals("Lazeez Shawarma (2900 Steeles)", r.offer.pickup)
        assertEquals("Annibale Dr & Bembridge Dr, Markham", r.offer.dropoff)
        assertEquals(BigDecimal("10.07"), r.offer.pay)
        assertEquals(48, r.offer.minutes)
    }

    @Test fun placesFromAccessibilityTexts() {
        val o = read(
            "Delivery", "Exclusive", "$5.50", "including estimated tip", "24 min (10.0 km) total",
            "McDonald's (Steeles & Hilda)", "Yonge St, Toronto", "Accept",
        )
        assertEquals("McDonald's (Steeles & Hilda)", o.pickup)
        assertEquals("Yonge St, Toronto", o.dropoff)
    }

    @Test fun noPlacesWhenCardEndsAtTripLine() {
        val o = read("$6.07", "23 min (11.5 km) total")
        assertEquals(null, o.pickup)
        assertEquals(null, o.dropoff)
    }

    /** 2026-10-28 6:21: an "Add a delivery" card; every number is what it adds. */
    @Test fun addOnOffer() {
        val o = read(
            "Add a delivery", "Exclusive", "+ $5.10", "including estimated tip", "+ 13 min (+ 3.5 km) total",
            "Booster Juice (5937 Leslie St)", "Empress Ave & Wilfred Ave, North", "York", "Accept",
        )
        assertEquals(BigDecimal("5.10"), o.pay)
        assertEquals(13, o.minutes)
        assertEquals(BigDecimal("3.5"), o.km)
        assertTrue(o.addOn)
        assertEquals("Booster Juice (5937 Leslie St)", o.pickup)
        assertEquals("Empress Ave & Wilfred Ave, North York", o.dropoff)
        val e = Scorer.score(o, Thresholds(minPay = BigDecimal.ZERO))
        assertEquals("\$23.54/hr · \$1.46/km", e.ratesText)
        // OCR spacing variants of the same line.
        assertEquals(13, read("+$5.10", "+13 min (+3.5 km) total").minutes)
    }

    /** 2026-10-28 6:33: a three-order trip over an hour. */
    @Test fun hoursAndThreeOrders() {
        val o = read(
            "Delivery (3)", "Exclusive", "$19.02", "including estimated tip", "1 hr 1 min (14.6 km) total",
            "My Sushi (Yonge St)", "Raglan Ave & St Clair Ave W, Toronto", "Accept",
        )
        assertEquals(61, o.minutes)
        assertEquals(3, o.orders)
        assertTrue(o.stacked)
        assertFalse(o.addOn)
        val e = Scorer.score(o, Thresholds())
        assertEquals(Verdict.RED, e.verdict)
        assertEquals("\$18.71/hr · \$1.30/km", e.ratesText)
        assertEquals(120, read("$30.00", "2 hr (40 km) total").minutes)
        assertEquals(75, read("$30.00", "1 hour 15 min (40 km) total").minutes)
    }

    /**
     * 2026-10-28 9:38: "$13.54 · Delivery (2) · 1 hr 3 min (38.3 km) total" got eh. at $270.80/hr:
     * the hour was dropped and the trip scored as 3 minutes. Uber's "1" reads as l or I.
     */
    @Test fun hourWithALetterForTheOne() {
        val card = arrayOf("Delivery (2)", "$13.54", "including estimated tip")
        for (trip in listOf("1 hr 3 min (38.3 km) total", "l hr 3 min (38.3 km) total", "I hr 3 min (38.3 km) total", "|hr 3min (38.3km) total")) {
            val o = read(*card, trip, "Ziyafet Kebab Modern Turkish", "Cuisine", "Church St & Dundas St E, Toronto", "Match")
            assertEquals(trip, 63, o.minutes)
            assertEquals(2, o.orders)
            val e = Scorer.score(o, Thresholds())
            assertEquals(Verdict.RED, e.verdict)
            assertEquals("\$12.90/hr · \$0.35/km", e.ratesText)
        }
        // An hour that can't be read at all is can't read., never "3 min".
        for (trip in listOf("? hr 3 min (38.3 km) total", "hr 3 min (38.3 km) total", "Q hr 3 min (38.3 km) total")) {
            assertTrue(trip, OfferParser.parse(listOf(*card, trip)) is ReadResult.Unreadable)
        }
        assertEquals(13, read("$5.10", "13 min (3.5 km) total", "Kebab House (Church St)", "Accept").minutes)
    }

    @Test fun tripWithoutATimeIsUnreadable() {
        assertTrue(OfferParser.parse(listOf("$6.07", "hr (11.5 km) total")) is ReadResult.Unreadable)
    }

    /** 2026-10-28 6:35: one offer logged twice because its restaurant read two ways. */
    @Test fun placeNamesMatchDespiteOcrSlips() {
        assertTrue(samePlace("ARAİTaiker Taiwanese Fried", "AITaiker Taiwanese Fried"))
        assertTrue(samePlace("Kakuya (Yonge St)", "Kakuya (Yonge St.)"))
        assertTrue(samePlace(null, "Kakuya (Yonge St)"))
        assertFalse(samePlace("Kakuya (Yonge St)", "Ono Poke Bar (Yonge & Finch)"))
        assertFalse(samePlace("Burger King (1607 Birchmount Road)", "Booster Juice (5937 Leslie St)"))
    }

    /** 2026-10-28 6:21: "can't read." (nahmeter's own card) was logged as the restaurant. */
    @Test fun ownVerdictCardIsNotAPlace() {
        val o = read("+ $5.10", "+ 13 min (+ 3.5 km) total", "can't read.", "Check this offer yourself", "Booster Juice (5937 Leslie St)", "Accept")
        assertEquals("Booster Juice (5937 Leslie St)", o.pickup)
        assertEquals(null, o.dropoff)
    }
}
