package dev.edmel.offerchecker.core

import java.math.BigDecimal

/**
 * Turns the text inside an Uber Eats offer card into an [Offer].
 *
 * All patterns live here so a change in Uber's wording is a one-file fix.
 * Built from the 2026-10-28 offer screenshot.
 */
object OfferParser {
    /**
     * "$6.07", "$ 6.07", "CA$6.07", optionally followed by "including estimated tip".
     * Tolerates stray symbols before the "$" that screen text recognition adds (e.g. "|$6.05").
     */
    private val PAY = Regex(
        """^[^\w$]*(?:CA)?\$\s?(\d{1,3}(?:\.\d{2})?)(?:\s*\(?\s*including estimated tip\w?\s*\)?)?$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "23 min (11.5 km) total". Also accepts what text recognition makes of it:
     * "(" read as "C", "[" or "{", missing brackets or spaces ("43min (13.6km)total").
     */
    private val TRIP = Regex(
        """(\d+)\s*min\.?\s*[(\[{C]?\s*(\d+(?:[.,]\d+)?)\s*km\s*[)\]}]?\s*total""",
        RegexOption.IGNORE_CASE,
    )

    /** A line that is shaped like the trip line even if its numbers can't be read. */
    private val TRIP_LIKE = Regex("""min.*km.*total""", RegexOption.IGNORE_CASE)

    /** Markers of a stacked (multi-order) offer, e.g. "Package (2)", "×2", "2 orders". */
    private val STACKED = Regex(
        """(?:[×x]\s?[2-9]\b)|(?:\b(?:[2-9]|two|three)\s+(?:orders|deliveries|pickups|packages)\b)|(?:\b(?:package|packages|delivery|deliveries|orders?)\s*\(\s*[2-9]\s*\))""",
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
        return candidates.mapNotNull { PAY.matchEntire(fixPayDigits(it))?.groupValues?.get(1)?.toBigDecimalOrNull() }
    }

    /** "$" then what should be the amount, possibly with letters text recognition mistook for digits. */
    private val PAY_AMOUNT = Regex("""^([^\w$]*(?:CA)?\$\s?)([0-9lIi|OoSsBZ]{1,5}(?:[.,][0-9lIi|OoSsBZ]{2})?)([^0-9.,].*)?$""")
    private val LOOKALIKES = mapOf(
        'l' to '1', 'I' to '1', 'i' to '1', '|' to '1', 'O' to '0', 'o' to '0',
        'S' to '5', 's' to '5', 'B' to '8', 'Z' to '2', ',' to '.',
    )

    /**
     * Undoes text-recognition slips in a pay amount: "$1l.59" → "$11.59", "$11,59" → "$11.59",
     * and a dropped decimal point "$1159" → "$11.59" (Uber always shows cents).
     */
    private fun fixPayDigits(line: String): String {
        val m = PAY_AMOUNT.matchEntire(line) ?: return line
        var amount = m.groupValues[2].map { LOOKALIKES[it] ?: it }.joinToString("")
        if ('.' !in amount && amount.length >= 3) amount = amount.dropLast(2) + "." + amount.takeLast(2)
        return m.groupValues[1] + amount + m.groupValues[3]
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
