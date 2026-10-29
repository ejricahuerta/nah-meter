package dev.edmel.offerchecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.service.NahCopy

/** Welcome: what nah.meter does, shown once before setup. */
@Composable
fun OnboardingScreen(thresholds: Thresholds, onSetUp: () -> Unit, modifier: Modifier = Modifier) {
    val c = Nah.c
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Wordmark(30.sp)

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                buildAnnotatedString {
                    append("Bad offer?\nJust say nah")
                    withStyle(SpanStyle(color = c.stop)) { append(".") }
                },
                style = Nah.style(52.sp, 900, 50.sp, (-0.06).em, c.ink),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "nah.meter reads every Uber Eats offer and gives you a one-word answer before the timer runs out.",
                style = Nah.style(17.sp, 400, 25.sp, color = c.inkMuted),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Example(Verdict.GREEN, "pays your rate, take it")
            Example(Verdict.YELLOW, "pays the hour, long drive")
            Example(Verdict.RED, "not worth your time")
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Point("Your rules: ${Scorer.money(thresholds.minPay)} min, ${Scorer.money(thresholds.minHourly)}/hr, ${Scorer.money(thresholds.minPerKm)}/km")
            Point("Never taps Match for you. You decide.")
            Point("No account. Nothing leaves your phone.")
        }

        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NahButton("Set up", onSetUp, modifier = Modifier.fillMaxWidth())
            Text(
                "About 5 minutes · Android 8 or newer",
                style = Nah.style(13.sp, 400, 18.sp, color = c.inkMuted).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A small black verdict card: the word and what it means. */
@Composable
private fun Example(v: Verdict, meaning: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nah.card)
            .border(1.dp, Nah.cardEdge, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(NahCopy.word(v), style = Nah.style(40.sp, 900, 40.sp, (-0.07).em, Nah.verdictColor(v)), modifier = Modifier.weight(1f))
        Text(meaning, style = Nah.style(15.sp, 400, 20.sp, color = Nah.cardMuted), modifier = Modifier.padding(bottom = 4.dp))
    }
}

/** A list point marked with the square full stop. */
@Composable
private fun Point(text: String) {
    val c = Nah.c
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 7.dp)) { Stop(8.dp) }
        Text(text, style = Nah.style(15.sp, 400, 22.sp, color = if (c.dark) androidx.compose.ui.graphics.Color(0xFFD4D4D4) else c.ink))
    }
}
