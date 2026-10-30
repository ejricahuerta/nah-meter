package dev.edmel.offerchecker.service

import dev.edmel.offerchecker.core.Check
import dev.edmel.offerchecker.core.Evaluation
import dev.edmel.offerchecker.core.Priority
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.VoiceMode
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * nah.meter's words for a verdict: lowercase verdicts (nah., meh. and bet!), then the number, then
 * the reason ("nah. $15.83/hr · Under your $20").
 */
object NahCopy {
    /** On cards and pills: always with its full stop, or bet!'s exclamation mark. */
    fun word(v: Verdict) = when (v) {
        Verdict.GREEN -> "bet!"
        Verdict.YELLOW -> "meh."
        Verdict.RED -> "nah."
        Verdict.GREY -> "can't read."
    }

    /** In lists and chips the full stop (or bet!'s mark) becomes the coloured dot before the word. */
    fun chipWord(v: Verdict) = word(v).trimEnd('.', '!')

    /** A rate split for the card, so the unit can be drawn smaller: "$15.83" and "/hr". */
    data class Rate(val amount: String, val unit: String) {
        override fun toString() = amount + unit
    }

    /**
     * The verdict card's two rates: the big one beside the word, and the chip beside the reason.
     * The strict rate ([Priority]) is big, so the number on top is the one that decided the
     * verdict. With no distance there's no $/km: the $/hr is big and there's no chip.
     * Null without a $/hr.
     */
    fun rates(hourly: BigDecimal?, perKm: BigDecimal?, priority: Priority): Pair<Rate, Rate?>? {
        val hr = hourly?.let { Rate("$${it.toPlainString()}", "/hr") } ?: return null
        val km = perKm?.let { Rate("$${it.toPlainString()}", "/km") }
        return if (priority == Priority.PER_KM && km != null) km to hr else hr to km
    }

    /** [rates] for an evaluation; null for can't read. */
    fun rates(e: Evaluation, t: Thresholds): Pair<Rate, Rate?>? =
        if (e.verdict == Verdict.GREY) null else rates(e.hourly, e.perKm, t.priority)

    /** "$15.83 per hour", "$0.53 per km": a rate as TalkBack should say it. */
    fun spokenRate(r: Rate) = r.amount + if (r.unit == "/km") " per km" else " per hour"

    /**
     * Under the word on the verdict card: "Under your $20". The other rate is its own chip ([rates]).
     * An "Add a delivery" card starts with "Add-on ·": its rates are for the extra work only.
     */
    fun cardReason(e: Evaluation, t: Thresholds): String =
        (if (e.offer?.addOn == true) "Add-on · " else "") + baseReason(e, t)

    // A rate missed in red is the strict one ([Thresholds.priority]): "Under your $20" or "Under
    // your $1/km". Missed in yellow it's the other one: "Short on $/hr" or "Short on $/km".
    private fun baseReason(e: Evaluation, t: Thresholds): String = when {
        e.verdict == Verdict.GREY -> "Check this offer yourself"
        e.failed == Check.MIN_PAY -> "Under ${Scorer.money(t.minPay)} minimum"
        e.failed == Check.MAX_KM -> "Over your ${t.maxKm?.let { Scorer.km(it) } ?: "max"} km"
        e.failed == Check.HOURLY && e.verdict == Verdict.RED -> "Under your ${hourlyTarget(t)}"
        e.failed == Check.HOURLY -> "Short on $/hr"
        e.failed == Check.PER_KM && e.verdict == Verdict.RED -> "Under your ${perKmTarget(t)}"
        e.failed == Check.PER_KM -> "Short on $/km"
        else -> "Clears your ${strictTarget(t)}"
    }

    /** The short reason after the offer's numbers: "$6.07 · 23 min · 11.5 km · under your $20". */
    fun shortReason(e: Evaluation, t: Thresholds): String = when {
        e.verdict == Verdict.GREY -> "check it yourself"
        e.failed == Check.MIN_PAY -> "under your ${Scorer.money(t.minPay)} min"
        e.failed == Check.MAX_KM -> "over your ${t.maxKm?.let { Scorer.km(it) } ?: "max"} km"
        e.failed == Check.HOURLY && e.verdict == Verdict.RED -> "under your ${hourlyTarget(t)}"
        e.failed == Check.HOURLY -> "short on $/hr"
        e.failed == Check.PER_KM && e.verdict == Verdict.RED -> "under your ${perKmTarget(t)}"
        e.failed == Check.PER_KM -> "short on $/km"
        else -> "clears your ${strictTarget(t)}"
    }

    /** "$20": the hourly target reads as plain dollars, as it always has. */
    private fun hourlyTarget(t: Thresholds) = Scorer.money(t.minHourly)

    private fun perKmTarget(t: Thresholds) = "${Scorer.money(t.minPerKm)}/km"

    private fun strictTarget(t: Thresholds) = if (t.priority == Priority.PER_KM) perKmTarget(t) else hourlyTarget(t)

    /**
     * Said out loud while driving. By default the word and whole dollars an hour, "nah. 14 an hour"
     * ("$14.19/hr" reads badly aloud); [VoiceMode.WORD] is just "nah." and [VoiceMode.WORD_HOURLY_KM]
     * adds the $/km as "nah. 14 an hour, 53 cents a k." Add-ons start with "add-on.".
     * With [Priority.PER_KM] the $/km leads instead: "nah. 53 cents a k." and
     * "nah. 53 cents a k, 14 an hour." With the Toronto voice the word is its recorded line
     * ([clipLine]): "nah fam. 14 an hour."
     */
    fun speech(
        e: Evaluation,
        mode: VoiceMode = VoiceMode.WORD_HOURLY,
        priority: Priority = Priority.HOURLY,
        toronto: Boolean = false,
    ): String = speechParts(e, mode, priority, toronto).joinToString(" ") { it.text }

    /** [speech] in the courier's voice mode and voice, with their strict rate leading. */
    fun speech(e: Evaluation, s: AppSettings): String = speech(e, s.voiceMode, s.thresholds.priority, s.torontoVoice)

    /**
     * The Toronto voice's recorded lines (res/raw, made in ElevenLabs), word for word. Only the voice
     * says these; the card keeps [word].
     */
    fun clipLine(v: Verdict) = when (v) {
        Verdict.GREEN -> "bet, say less!"
        Verdict.YELLOW -> "meh, your call fam."
        Verdict.RED -> "nah fam."
        Verdict.GREY -> "mans can't read that. check it yourself."
    }

    /** One piece of the spoken verdict: its [text], and the verdict whose recorded line says it instead. */
    data class SpeechPart(val text: String, val clip: Verdict? = null)

    /**
     * [speech] in the order it's said: "add-on." for an add-on, the word, then the rates. With
     * [toronto] the word is a recorded line ([SpeechPart.clip]); the rest is always the phone's voice.
     */
    fun speechParts(e: Evaluation, mode: VoiceMode, priority: Priority, toronto: Boolean): List<SpeechPart> {
        if (e.verdict == Verdict.GREY) {
            return listOf(
                if (toronto) SpeechPart(clipLine(Verdict.GREY), Verdict.GREY)
                else SpeechPart(if (mode == VoiceMode.WORD) "can't read." else "can't read. check it yourself."),
            )
        }
        val parts = mutableListOf<SpeechPart>()
        if (e.offer?.addOn == true) parts += SpeechPart("add-on.")
        parts += if (toronto) SpeechPart(clipLine(e.verdict), e.verdict) else SpeechPart(word(e.verdict))
        if (mode == VoiceMode.WORD) return parts
        val hourly = "${e.hourly?.toBigInteger() ?: 0} an hour"
        val perKm = e.perKm?.let { spokenPerKm(it) }
        // With no distance there's no $/km to lead with, so the hour does.
        val rates = if (priority == Priority.PER_KM && perKm != null) listOf(perKm, hourly) else listOfNotNull(hourly, perKm)
        parts += SpeechPart((if (mode == VoiceMode.WORD_HOURLY_KM) rates.joinToString(", ") else rates.first()) + ".")
        return parts
    }

    /** [speechParts] in the courier's voice mode and voice. */
    fun speechParts(e: Evaluation, s: AppSettings): List<SpeechPart> =
        speechParts(e, s.voiceMode, s.thresholds.priority, s.torontoVoice)

    /** "53 cents a k", "1 dollar 5 cents a k". */
    private fun spokenPerKm(v: BigDecimal): String {
        val cents = v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt().coerceAtLeast(0)
        val dollars = cents / 100
        val rest = cents % 100
        val parts = buildList {
            if (dollars > 0) add(if (dollars == 1) "1 dollar" else "$dollars dollars")
            if (rest > 0 || dollars == 0) add(if (rest == 1) "1 cent" else "$rest cents")
        }
        return parts.joinToString(" ") + " a k"
    }

    /**
     * What TalkBack announces, the card in reading order: "nah. $15.83 per hour. Under your $20,
     * $0.53 per km." With $/km strict the $/km comes first: "nah. $0.53 per km. Under your $1 per
     * km, $15.83 per hour."
     */
    fun spoken(e: Evaluation, t: Thresholds): String {
        val (big, chip) = rates(e, t) ?: return "can't read. Check this offer yourself."
        return "${word(e.verdict)} ${spokenRate(big)}. " +
            (cardReason(e, t) + (chip?.let { " · $it" } ?: ""))
                .replace(" · ", ", ").replace("/km", " per km").replace("/hr", " per hour") + "."
    }
}
