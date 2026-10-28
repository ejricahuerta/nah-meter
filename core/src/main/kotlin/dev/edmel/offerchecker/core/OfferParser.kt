package dev.edmel.offerchecker.core

import java.math.BigDecimal

/**
 * Turns the text inside an Uber Eats offer card into an [Offer].
 *
 * All patterns live here so a change in Uber's wording is a one-file fix.
 * Built from the 2026-10-28 offer screenshot.
 */
object OfferParser {
    /** "$6.07", "$ 6.07", "CA$6.07", optionally followed by "including estimated tip". */
    private val PAY = Regex(
        """^(?:CA)?\$\s?(\d{1,3}(?:\.\d{2})?)(?:\s*\(?\s*including estimated tip\s*\)?)?$""",
        RegexOption.IGNORE_CASE,
    )

    /** "23 min (11.5 km) total". */
    private val TRIP = Regex(
        """(\d+)\s*min\s*\((\d+(?:[.,]\d+)?)\s*km\)\s*total""",
        RegexOption.IGNORE_CASE,
    )

    /** A line that is shaped like the trip line even if its numbers can't be read. */
    private val TRIP_LIKE = Regex("""\bmin\b.*\bkm\b.*\btotal\b""", RegexOption.IGNORE_CASE)

    /** Markers of a stacked (multi-order) offer. Unconfirmed: needs a real stacked screenshot. */
    private val STACKED = Regex(
        """(?:[×x]\s?2\b)|(?:\b(?:2|two)\s+(?:orders|deliveries|pickups)\b)""",
        RegexOption.IGNORE_CASE,
    )

    private fun lines(texts: List<String>): List<String> =
        texts.flatMap { it.split('\n') }
            .map { it.replace(' ', ' ').trim() }
            .filter { it.isNotEmpty() }

    /** True if this text contains an offer's "min (… km) total" line, readable or not. */
    fun isTripLine(text: String): Boolean = lines(listOf(text)).any { TRIP_LIKE.containsMatchIn(it) }

    /** True if any text is a standalone pay amount. */
    fun containsPay(texts: List<String>): Boolean = payValues(lines(texts)).isNotEmpty()

    /**
     * Standalone pay amounts. Also tries each line joined with the next, for apps that
     * draw "$" and "5.50" as separate text pieces.
     */
    private fun payValues(lines: List<String>): List<BigDecimal> {
        val candidates = lines + lines.zipWithNext { a, b -> a + b } + lines.zipWithNext { a, b -> "$a $b" }
        return candidates.mapNotNull { PAY.matchEntire(it)?.groupValues?.get(1)?.toBigDecimalOrNull() }
    }

    /**
     * Parses the texts of one card. Text outside the card (map labels, street names)
     * must not be passed in; the caller limits [cardTexts] to the card.
     */
    fun parse(cardTexts: List<String>): ReadResult {
        val lines = lines(cardTexts)
        if (lines.none { TRIP_LIKE.containsMatchIn(it) }) return ReadResult.NoOffer

        val trip = lines.firstNotNullOfOrNull { TRIP.find(it) }
            ?: return ReadResult.Unreadable(lines)
        // The largest $ value inside the card is the pay.
        val pay = payValues(lines).maxOrNull()
            ?: return ReadResult.Unreadable(lines)
        val minutes = trip.groupValues[1].toIntOrNull()
        val km = trip.groupValues[2].replace(',', '.').toBigDecimalOrNull()
        if (minutes == null || minutes <= 0 || km == null || km < BigDecimal.ZERO) {
            return ReadResult.Unreadable(lines)
        }
        val stacked = lines.any { STACKED.containsMatchIn(it) }
        return ReadResult.Read(Offer(pay, minutes, km, stacked), lines)
    }

    /**
     * Picks the offer card's lines out of a whole-screen OCR result: the trip line plus the
     * lines just above it (pay, "including estimated tip"), so map labels elsewhere are ignored.
     * Returns null when no trip line was recognised.
     */
    fun cardFromOcr(lines: List<OcrLine>, screenHeight: Int): List<String>? {
        val trip = lines.firstOrNull { isTripLine(it.text) } ?: return null
        val bandTop = trip.top - (screenHeight * OCR_BAND).toInt()
        return lines.filter { it.top >= bandTop && it.bottom <= trip.bottom + 4 }
            .sortedBy { it.top }
            .map { it.text }
    }

    /** How far above the trip line (as a share of screen height) the pay can be. */
    private const val OCR_BAND = 0.3
}
