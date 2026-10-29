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
     * "(" read as "C", "[" or "{", missing brackets or spaces ("43min (13.6km)total"), and
     * letters for digits in the time ("l hr 3 min": Uber's "1" has no foot and reads as l or I).
     */
    private val TRIP = Regex(
        """(?:(?<![a-z])([0-9lIi|O]{1,2})\s*h(?:r|rs|our|ours)?\.?\s*)?(?:(?<![a-z])([0-9lIi|O]{1,3})\s*min\.?)?\s*[(\[{C]?\s*\+?\s*(\d+(?:[.,]\d+)?)\s*km\s*[)\]}]?\s*total""",
        RegexOption.IGNORE_CASE,
    )

    /** An hours word in the trip line: if its number wasn't read, the minutes alone are wrong. */
    private val HOURS_WORD = Regex("""(?<![a-z])h(?:r|rs|our|ours)\b""", RegexOption.IGNORE_CASE)

    /** "l" → 1, "O" → 0: a time number as text recognition may have read it. */
    private fun timeNumber(s: String): Int? =
        s.map { LOOKALIKES[it] ?: it }.joinToString("").takeIf { it.isNotEmpty() }?.toIntOrNull()

    /** A line that is shaped like the trip line even if its numbers can't be read. */
    private val TRIP_LIKE = Regex("""(min|hr|hour).*km.*total""", RegexOption.IGNORE_CASE)

    /** How many orders: "Delivery (3)", "Package (2)", "×2", "2 orders". */
    private val ORDER_COUNT = Regex(
        """(?:[×x]\s?([2-9])\b)|(?:\b([2-9]|two|three)\s+(?:orders|deliveries|pickups|packages)\b)|(?:\b(?:package|packages|delivery|deliveries|orders?)\s*\(\s*([2-9])\s*\))""",
        RegexOption.IGNORE_CASE,
    )

    /** An add-on to a trip already accepted: "Add a delivery", with "+ $5.10" and "+ 13 min (+ 3.5 km)". */
    private val ADD_ON = Regex("""add a delivery|add-on|^\W*\+\s*\$""", RegexOption.IGNORE_CASE)

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

        val (tripLine, trip) = lines.firstNotNullOfOrNull { l -> TRIP.find(l)?.let { l to it } }
            ?: return ReadResult.Unreadable(lines)
        // The largest $ value inside the card is the pay.
        val pay = payValues(lines).maxOrNull()
            ?: return ReadResult.Unreadable(lines)
        // "1 hr 1 min" = 61; "1 hr" = 60; "23 min" = 23.
        val hours = timeNumber(trip.groupValues[1])
        val mins = timeNumber(trip.groupValues[2])
        // "? hr 3 min" with the hours unreadable: 3 minutes would be a guess, and a wildly good one.
        if (hours == null && HOURS_WORD.containsMatchIn(tripLine.substringBefore("km"))) {
            return ReadResult.Unreadable(lines)
        }
        val minutes = if (hours == null && mins == null) null else (hours ?: 0) * 60 + (mins ?: 0)
        val km = trip.groupValues[3].replace(',', '.').toBigDecimalOrNull()
        if (minutes == null || minutes <= 0 || km == null || km < BigDecimal.ZERO) {
            return ReadResult.Unreadable(lines)
        }
        val orders = lines.firstNotNullOfOrNull { ORDER_COUNT.find(it) }?.let { m ->
            when (val n = m.groupValues.drop(1).first { it.isNotEmpty() }.lowercase()) {
                "two" -> 2
                "three" -> 3
                else -> n.toInt()
            }
        } ?: if (lines.any { STACKED.containsMatchIn(it) }) 2 else 1
        val addOn = lines.any { ADD_ON.containsMatchIn(it) }
        val (pickup, dropoff) = places(lines)
        return ReadResult.Read(
            Offer(pay, minutes, km, orders > 1, pickup, dropoff, orders = orders, addOn = addOn),
            lines,
        )
    }

    /** The card's buttons: the place lines end here. */
    private val STOP = Regex("""^\W*(accept|match|decline|reject)\b""", RegexOption.IGNORE_CASE)

    /**
     * Card furniture that isn't a place, and nahmeter's own verdict card if a screenshot caught it
     * ("can't read.", "Check this offer yourself", "Under your $20 ·").
     */
    private val NOT_A_PLACE = Regex(
        """customer verification|including estimated tip|exclusive|add a delivery|^\W*(delivery|package)\b|\$\s?\d|""" +
            """^\W*(bet|eh|nah|can'?t read)\W*$|check this offer|under your|clears your|pays the hour|short on \$/km|add-on ·""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The restaurant and the drop-off area: the lines between the trip line and the card's button.
     * The first is the restaurant; the rest (Uber wraps the area over two lines) the drop-off.
     */
    private fun places(lines: List<String>): Pair<String?, String?> {
        val start = lines.indexOfFirst { TRIP_LIKE.containsMatchIn(it) }
        if (start < 0) return null to null
        val places = lines.drop(start + 1)
            .takeWhile { !STOP.containsMatchIn(it) }
            .filter { it.count(Char::isLetter) >= 3 && !NOT_A_PLACE.containsMatchIn(it) && !TRIP_LIKE.containsMatchIn(it) }
            .distinct()
        if (places.isEmpty()) return null to null
        // Uber wraps one address over lines ("…, North" / "York"), so the lines join with a space.
        val dropoff = places.drop(1).joinToString(" ").trim().trimEnd(',').ifEmpty { null }
        return places.first().trimEnd(',') to dropoff
    }

    /**
     * Picks the offer card's lines out of a whole-screen OCR result: the trip line plus the
     * lines just above it (pay, "including estimated tip"), so map labels elsewhere are ignored.
     * Of the dollar amounts there, only the one in the largest text is kept as the pay.
     * Returns null when no trip line was recognised.
     */
    fun cardFromOcr(lines: List<OcrLine>, screenHeight: Int): List<String>? {
        val (band, pay) = ocrBand(lines, screenHeight) ?: return null
        val pays = band.filter { containsPay(listOf(it.text)) }
        // Uber's pay is the biggest text on the card. Other dollar amounts in the band come from
        // whatever is behind the pop-up (a web page, another app), so drop them.
        val above = band.filter { it == pay || it !in pays }.sortedBy { it.top }
        return (above + placeLines(lines, screenHeight)).map { it.text }
    }

    /**
     * The lines under the trip line, down to the card's button: the restaurant and drop-off area.
     * Only lines starting near the trip line's left edge, so map labels beside the card stay out.
     */
    private fun placeLines(lines: List<OcrLine>, screenHeight: Int): List<OcrLine> {
        val trip = lines.firstOrNull { isTripLine(it.text) } ?: return emptyList()
        val bottom = trip.bottom + (screenHeight * OCR_BELOW).toInt()
        val slack = trip.bottom - trip.top
        val below = lines.filter { it.top >= trip.bottom - 4 && it.top <= bottom }
            .filter { trip.right == 0 || it.left >= trip.left - slack }
            .sortedBy { it.top }
        val stop = below.indexOfFirst { STOP.containsMatchIn(it.text) }
        return if (stop >= 0) below.take(stop + 1) else below
    }

    /**
     * Where the offer card's top edge is, estimated from the pay line (the card shows one row,
     * the "Delivery" chip, above the pay): used to dock the verdict card just above the offer.
     */
    fun ocrCardTop(lines: List<OcrLine>, screenHeight: Int): Int? {
        val pay = ocrBand(lines, screenHeight)?.second ?: return null
        return pay.top - (pay.bottom - pay.top) * 14 / 10
    }

    /** The lines from 30% of the screen above the trip line down to it, and the pay line among them. */
    private fun ocrBand(lines: List<OcrLine>, screenHeight: Int): Pair<List<OcrLine>, OcrLine?>? {
        val trip = lines.firstOrNull { isTripLine(it.text) } ?: return null
        val bandTop = trip.top - (screenHeight * OCR_BAND).toInt()
        val band = lines.filter { it.top >= bandTop && it.bottom <= trip.bottom + 4 }
        val pay = band.filter { containsPay(listOf(it.text)) }
            .maxWithOrNull(compareBy<OcrLine>({ it.bottom - it.top }, { it.bottom }))
        return band to pay
    }

    /** How far above the trip line (as a share of screen height) the pay can be. */
    private const val OCR_BAND = 0.3

    /** How far below the trip line the restaurant and drop-off lines can be. */
    private const val OCR_BELOW = 0.25
}
