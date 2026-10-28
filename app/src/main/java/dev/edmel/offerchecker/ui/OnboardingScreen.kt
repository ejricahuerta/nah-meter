package dev.edmel.offerchecker.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.core.Verdict

/** Welcome: what Worth It does, shown once before setup. */
@Composable
fun OnboardingScreen(thresholds: Thresholds, onSetUp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Wordmark(26.sp)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Know if an offer pays before you tap.", style = Wi.style(36.sp, 700, 40.sp, (-0.025).em))
            Text("A verdict appears above every Uber Eats offer in about a second.", style = Wi.style(16.sp, 400, 24.sp, color = Wi.inkMuted))
        }

        // Illustration: the verdict card docked above a sketched offer card.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color1B).padding(12.dp)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VerdictCard(Verdict.GREEN, "$25.64", "$1.38/km", "Meets all targets")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Wi.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(72.dp, 22.dp).clip(RoundedCornerShape(6.dp)).background(Wi.surfaceRaised))
                Text("$9.40", style = Wi.style(28.sp, 700, 30.sp, (-0.03).em))
                Box(Modifier.size(180.dp, 12.dp).clip(RoundedCornerShape(6.dp)).background(Wi.surfaceRaised))
                Box(Modifier.size(220.dp, 12.dp).clip(RoundedCornerShape(6.dp)).background(Wi.surfaceRaised))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Point(
                1, "Your rules.",
                "${Scorer.money(thresholds.minPay)} minimum, ${Scorer.money(thresholds.minHourly)}/hr, then ${Scorer.money(thresholds.minPerKm)}/km.",
            )
            Point(2, "You decide.", "It never taps Accept for you.")
            Point(3, "Private.", "No account. Nothing leaves your phone.")
        }

        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WiButton("Set up", onSetUp, modifier = Modifier.fillMaxWidth())
            Text(
                "Takes about 5 minutes · Android 8 or newer",
                style = Wi.style(13.sp, 400, 18.sp, color = Wi.inkMuted).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val Color1B = androidx.compose.ui.graphics.Color(0xFF1B1B1B)

@Composable
private fun Point(n: Int, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Wi.ink), contentAlignment = Alignment.Center) {
            Text("$n", style = Wi.style(14.sp, 700, color = Wi.onInk))
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.W600)) { append(title) }
                append(" ")
                withStyle(SpanStyle(color = Wi.inkMuted)) { append(text) }
            },
            style = Wi.body,
            modifier = Modifier.weight(1f),
        )
    }
}
