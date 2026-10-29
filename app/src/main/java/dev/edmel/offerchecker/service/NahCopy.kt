package dev.edmel.offerchecker.service

import dev.edmel.offerchecker.core.Check
import dev.edmel.offerchecker.core.Evaluation
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.core.Verdict

/**
 * nah.meter's words for a verdict, from the design system's voice: lowercase verdicts that end in a
 * full stop, then the number, then the reason ("nah. $15.83/hr · Under your $20").
 */
object NahCopy {
    /** On cards and pills: always with the full stop. */
    fun word(v: Verdict) = when (v) {
        Verdict.GREEN -> "bet."
        Verdict.YELLOW -> "eh."
        Verdict.RED -> "nah."
        Verdict.GREY -> "can't read."
    }

    /** In lists and chips the full stop becomes the coloured dot before the word. */
    fun chipWord(v: Verdict) = word(v).removeSuffix(".")

    /** The $/km in its own chip on the verdict card ("$0.53/km"); null for can't read. or no distance. */
    fun kmChip(e: Evaluation): String? =
        if (e.verdict == Verdict.GREY) null else e.perKm?.let { "$${it.toPlainString()}/km" }

    /**
     * Under the word on the verdict card: "Under your $20". The $/km is its own chip ([kmChip]).
     * An "Add a delivery" card starts with "Add-on ·": its rates are for the extra work only.
     */
    fun cardReason(e: Evaluation, t: Thresholds): String =
        (if (e.offer?.addOn == true) "Add-on · " else "") + baseReason(e, t)

    private fun baseReason(e: Evaluation, t: Thresholds): String = when {
        e.verdict == Verdict.GREY -> "Check this offer yourself"
        e.failed == Check.MIN_PAY -> "Under ${Scorer.money(t.minPay)} minimum"
        e.failed == Check.HOURLY -> "Under your ${Scorer.money(t.minHourly)}"
        e.failed == Check.PER_KM -> "Short on $/km"
        else -> "Clears your ${Scorer.money(t.minHourly)}"
    }

    /** The short reason after the offer's numbers: "$6.07 · 23 min · 11.5 km · under your $20". */
    fun shortReason(e: Evaluation, t: Thresholds): String = when {
        e.verdict == Verdict.GREY -> "check it yourself"
        e.failed == Check.MIN_PAY -> "under your ${Scorer.money(t.minPay)} min"
        e.failed == Check.HOURLY -> "under your ${Scorer.money(t.minHourly)}"
        e.failed == Check.PER_KM -> "short on $/km"
        else -> "clears your ${Scorer.money(t.minHourly)}"
    }

    /**
     * Said out loud while driving: the word and whole dollars an hour, "nah. 14 an hour"
     * ("$14.19/hr" reads badly aloud). Add-ons start with "add-on.".
     */
    fun speech(e: Evaluation): String =
        if (e.verdict == Verdict.GREY) "can't read. check it yourself."
        else (if (e.offer?.addOn == true) "add-on. " else "") +
            "${word(e.verdict)} ${e.hourly?.toBigInteger() ?: 0} an hour."

    /** What TalkBack announces: "nah. $15.83 per hour. Under your $20, $0.53 per km." */
    fun spoken(e: Evaluation, t: Thresholds): String =
        if (e.verdict == Verdict.GREY) "can't read. Check this offer yourself."
        else "${word(e.verdict)} $${e.hourly} per hour. " +
            (cardReason(e, t) + (kmChip(e)?.let { " · $it" } ?: "")).replace(" · ", ", ").replace("/km", " per km") + "."
}
