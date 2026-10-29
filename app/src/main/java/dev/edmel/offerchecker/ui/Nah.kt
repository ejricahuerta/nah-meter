package dev.edmel.offerchecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.R
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.service.NahCopy

/** nah.meter colour tokens for one theme. The verdict card is black in both. */
data class NahPalette(
    val ground: Color, val surface: Color, val surfaceRaised: Color, val line: Color, val controlBorder: Color,
    val ink: Color, val inkMuted: Color, val onInk: Color, val stop: Color,
    val betInk: Color, val ehInk: Color, val nahInk: Color, val focusRing: Color, val dark: Boolean,
)

val Mono = NahPalette(
    ground = Color(0xFF000000), surface = Color(0xFF111111), surfaceRaised = Color(0xFF1F1F1F), line = Color(0xFF262626),
    controlBorder = Color(0xFF6B6B6B), ink = Color(0xFFFFFFFF), inkMuted = Color(0xFFA8A8A8), onInk = Color(0xFF000000),
    stop = Color(0xFFFF3B30), betInk = Color(0xFF30D158), ehInk = Color(0xFFFFD60A), nahInk = Color(0xFFFF453A), focusRing = Color(0xFFFFFFFF), dark = true,
)

val Daylight = NahPalette(
    ground = Color(0xFFFFFFFF), surface = Color(0xFFF3F3F3), surfaceRaised = Color(0xFFE8E8E8), line = Color(0xFFE2E2E2),
    controlBorder = Color(0xFF767676), ink = Color(0xFF000000), inkMuted = Color(0xFF5E5E5E), onInk = Color(0xFFFFFFFF),
    stop = Color(0xFFE5251B), betInk = Color(0xFF076B31), ehInk = Color(0xFF8A5A00), nahInk = Color(0xFFC4241B), focusRing = Color(0xFF000000), dark = false,
)

private val LocalNah = staticCompositionLocalOf { Mono }

/** Mono (dark, the default) or Daylight (light), following the phone's dark-theme setting. */
@Composable
fun NahTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNah provides if (isSystemInDarkTheme()) Mono else Daylight, content = content)
}

/** nah.meter tokens, from the nah.meter design system. */
object Nah {
    val c: NahPalette @Composable get() = LocalNah.current

    // The verdict card and pill are always black.
    val card = Color(0xFF000000)
    val cardEdge = Color(0xFF2A2A2A)
    val cardMuted = Color(0xFFA8A8A8)
    val cardSoft = Color(0xFFDEDEDE)
    val cardChip = Color(0xFF262626)
    val bet = Color(0xFF30D158)
    val eh = Color(0xFFFFD60A)
    val nah = Color(0xFFFF3B30)
    val unknown = Color(0xFF8E8E93)

    val geist = FontFamily(
        Font(R.font.geist_400, FontWeight.W400),
        Font(R.font.geist_500, FontWeight.W500),
        Font(R.font.geist_600, FontWeight.W600),
        Font(R.font.geist_700, FontWeight.W700),
        Font(R.font.geist_800, FontWeight.W800),
        Font(R.font.geist_900, FontWeight.W900),
    )

    fun style(size: TextUnit, weight: Int, line: TextUnit = TextUnit.Unspecified, tracking: TextUnit = 0.em, color: Color = Color.Unspecified) =
        TextStyle(
            fontFamily = geist, fontSize = size, fontWeight = FontWeight(weight), lineHeight = line,
            letterSpacing = tracking, color = color, fontFeatureSettings = "tnum",
        )

    val title = style(28.sp, 800, 32.sp, (-0.035).em)
    val heading = style(20.sp, 700, 26.sp, (-0.02).em)
    val body = style(16.sp, 400, 24.sp)
    val bodyStrong = style(16.sp, 600, 22.sp)
    val label = style(14.sp, 500, 20.sp)
    val caption = style(12.sp, 500, 16.sp)

    /** The verdict word's colour on the black card, and its dot in chips. */
    fun verdictColor(v: Verdict) = when (v) {
        Verdict.GREEN -> bet
        Verdict.YELLOW -> eh
        Verdict.RED -> nah
        Verdict.GREY -> unknown
    }
}

/** Interface icons: 2px outline on a 24px grid, round ends. Verdicts need no icon: the word is the icon. */
object NahIcons {
    private fun icon(name: String, stroke: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val check = icon("check", 3f, "M5 12.5l4.5 4.5L19 7.5")
    val alert = icon("alert", 3f, "M12 7v6", "M12 17h.01")
    val status = icon("status", 2f, "M4 17a8 8 0 0 1 16 0", "M12 17l4-5")
    val sliders = icon(
        "sliders", 2f, "M4 7h10M18 7h2M4 17h4M12 17h8",
        "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )
    val list = icon("list", 2f, "M8 6h12M8 12h12M8 18h12", "M4 6h.01M4 12h.01M4 18h.01")
    val minus = icon("minus", 2.5f, "M6 12h12")
    val plus = icon("plus", 2.5f, "M6 12h12M12 6v12")
}

/** The square full stop: the brand. Sized to the text it ends. */
@Composable
fun Stop(size: Dp, color: Color = Nah.c.stop) {
    Box(Modifier.size(size).background(color))
}

/** The `nah.meter` wordmark: Geist Black, tracked −5.5%, the square red full stop between the two words. */
@Composable
fun Wordmark(size: TextUnit = 26.sp) {
    val style = Nah.style(size, 900, size, (-0.055).em, Nah.c.ink)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("nah", style = style)
        Box(Modifier.padding(start = (size.value * 0.05f).dp, end = (size.value * 0.05f).dp, bottom = (size.value * 0.12f).dp)) {
            Stop((size.value * 0.2f).dp)
        }
        Text("meter", style = style)
    }
}

/** "$15.83" with a smaller, muted "/hr". */
private fun rate(amount: String, unit: String, unitSize: TextUnit, unitColor: Color) = buildAnnotatedString {
    append(amount)
    withStyle(SpanStyle(fontSize = unitSize, fontWeight = FontWeight.W600, letterSpacing = 0.em, color = unitColor)) {
        append(unit)
    }
}

/** VerdictCard: black, the word left in its colour, $/hr right, the reason underneath. */
@Composable
fun VerdictCard(
    verdict: Verdict, hourly: String?, reason: String, modifier: Modifier = Modifier, spoken: String? = null,
    kmChip: String? = null,
) {
    val unknown = verdict == Verdict.GREY
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nah.card)
            .border(1.dp, Nah.cardEdge, RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken ?: "${NahCopy.word(verdict)} ${hourly ?: ""} $reason"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                NahCopy.word(verdict),
                style = if (unknown) Nah.style(44.sp, 900, 48.sp, (-0.05).em, Nah.unknown)
                else Nah.style(64.sp, 900, 56.sp, (-0.07).em, Nah.verdictColor(verdict)),
                modifier = Modifier.weight(1f),
            )
            if (hourly != null) {
                Text(
                    rate(hourly, "/hr", 15.sp, Nah.cardMuted),
                    style = Nah.style(36.sp, 800, 38.sp, (-0.04).em, Color.White), maxLines = 1,
                )
            }
        }
        // Foot: the reason (17 semibold, card-soft) left, the $/km in its own chip right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(reason, style = Nah.style(17.sp, 600, 22.sp, color = Nah.cardSoft), modifier = Modifier.weight(1f))
            if (kmChip != null) {
                Box(
                    Modifier.height(30.dp).clip(CircleShape).background(Nah.cardChip).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(kmChip, style = Nah.style(15.sp, 700, 20.sp, color = Color.White), maxLines = 1) }
            }
        }
    }
}

/** VerdictPill: compact, black, the word and optionally $/hr. */
@Composable
fun VerdictPill(v: Verdict, hourly: String? = null) {
    Row(
        Modifier.height(52.dp).clip(CircleShape).background(Nah.card).border(1.dp, Nah.cardEdge, CircleShape)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(NahCopy.word(v), style = Nah.style(24.sp, 900, 26.sp, (-0.05).em, Nah.verdictColor(v)))
        if (hourly != null) Text(rate(hourly, "/hr", 13.sp, Nah.cardMuted), style = Nah.style(22.sp, 800, 26.sp, (-0.03).em, Color.White))
    }
}

/** VerdictChip, for lists: a dot then the word ("3 bet"); nah is inverted to stand out. */
@Composable
fun VerdictChip(v: Verdict, text: String = NahCopy.chipWord(v)) {
    val inverted = v == Verdict.RED
    Row(
        Modifier.height(32.dp).clip(CircleShape).background(if (inverted) Nah.c.ink else Nah.c.surfaceRaised)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Nah.verdictColor(v)))
        Text(text, style = Nah.style(16.sp, 900, 20.sp, (-0.04).em, if (inverted) Nah.c.onInk else Nah.c.ink))
    }
}

/** StatusPill: "Reading offers" with a green dot when on. */
@Composable
fun StatusPill(on: Boolean, text: String) {
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(Nah.c.surfaceRaised).padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(18.dp).clip(CircleShape).background(Nah.bet.copy(alpha = 0.22f)))
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) Nah.bet else Nah.c.inkMuted))
        }
        Text(text, style = Nah.style(16.sp, 600, 20.sp, color = Nah.c.ink))
    }
}

enum class NahButtonKind { Primary, Secondary, Tertiary }

/** Button: primary (ink fill, never a verdict colour), secondary (raised), tertiary (underlined). */
@Composable
fun NahButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    kind: NahButtonKind = NahButtonKind.Primary, small: Boolean = false, enabled: Boolean = true,
) {
    val (bg, fg) = when (kind) {
        NahButtonKind.Primary -> Nah.c.ink to Nah.c.onInk
        NahButtonKind.Secondary -> Nah.c.surfaceRaised to Nah.c.ink
        NahButtonKind.Tertiary -> Color.Transparent to Nah.c.ink
    }
    val shape = if (small) CircleShape else RoundedCornerShape(10.dp)
    Box(
        modifier.heightIn(min = if (small) 40.dp else 56.dp).clip(shape).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = if (kind == NahButtonKind.Tertiary) 12.dp else if (small) 16.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Nah.style(if (small) 15.sp else 17.sp, 700, 24.sp, (-0.01).em, fg).copy(
                textDecoration = if (kind == NahButtonKind.Tertiary) TextDecoration.Underline else null,
            ),
        )
    }
}

/** Switch: outline when off, ink when on. */
@Composable
fun NahSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    val c = Nah.c
    Box(
        Modifier.size(52.dp, 32.dp).clip(CircleShape)
            .background(if (checked) c.ink else Color.Transparent)
            .border(2.dp, if (checked) c.ink else c.controlBorder, CircleShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label },
    ) {
        // Thumb 20dp, 4dp inside the 2dp border.
        Box(
            Modifier.offset(x = if (checked) 26.dp else 6.dp, y = 6.dp).size(20.dp)
                .clip(CircleShape).background(if (checked) c.onInk else c.controlBorder),
        )
    }
}

/** A list group: rows on `surface`, separated by hairlines. */
@Composable
fun NahGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nah.c.surface), content = content)
}

@Composable
fun NahDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Nah.c.line))

@Composable
fun SectionLabel(text: String) {
    Text(text, style = Nah.style(14.sp, 600, 20.sp, color = Nah.c.inkMuted), modifier = Modifier.padding(bottom = 8.dp))
}

/** A row: optional leading, title over description, optional trailing. */
@Composable
fun NahRow(
    title: String, desc: String? = null, descColor: Color = Nah.c.inkMuted, minHeight: Dp = 64.dp,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Nah.style(16.sp, 600, 22.sp, color = Nah.c.ink))
            if (desc != null) Text(desc, style = Nah.style(14.sp, 400, 20.sp, color = descColor))
        }
        trailing?.invoke(this)
    }
}

enum class StepState { Done, Todo, Manual }

/** SetupStep: an ink check when done, a red-ringed "!" when to do, a neutral dot when it can't be checked. */
@Composable
fun SetupStep(state: StepState, title: String, desc: String, action: String? = null, onAction: () -> Unit = {}) {
    val c = Nah.c
    val trailing: (@Composable RowScope.() -> Unit)? =
        if (action != null) { { NahButton(action, onAction, kind = NahButtonKind.Secondary, small = true) } } else null
    NahRow(
        title = title, desc = desc,
        descColor = if (state == StepState.Todo) c.nahInk else c.inkMuted,
        leading = {
            val mod = Modifier.size(32.dp).clip(CircleShape)
            when (state) {
                StepState.Done -> Box(mod.background(c.ink), contentAlignment = Alignment.Center) {
                    Icon(NahIcons.check, contentDescription = "Done", tint = c.onInk, modifier = Modifier.size(18.dp))
                }
                StepState.Todo -> Box(mod.background(c.surfaceRaised).border(2.dp, c.nahInk, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(NahIcons.alert, contentDescription = "To do", tint = c.nahInk, modifier = Modifier.size(18.dp))
                }
                StepState.Manual -> Box(mod.background(c.surfaceRaised), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.inkMuted))
                }
            }
        },
        trailing = trailing,
    )
}

/** ThresholdField: label, big value with unit, hint, and round −/+ steppers. */
@Composable
fun ThresholdField(
    label: String, value: String, unit: String?, hint: String,
    minusLabel: String, plusLabel: String, onMinus: () -> Unit, onPlus: () -> Unit,
) {
    val c = Nah.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = Nah.style(14.sp, 500, 20.sp, color = c.inkMuted))
            Text(
                if (unit == null) buildAnnotatedString { append(value) } else rate(value, unit, 15.sp, c.inkMuted),
                style = Nah.style(24.sp, 800, 28.sp, (-0.03).em, c.ink),
            )
            Text(hint, style = Nah.style(13.sp, 400, 18.sp, color = c.inkMuted))
        }
        RoundButton(NahIcons.minus, minusLabel, onMinus)
        RoundButton(NahIcons.plus, plusLabel, onPlus)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Nah.c.surfaceRaised)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Nah.c.ink, modifier = Modifier.size(20.dp))
    }
}

/** Screen padding used by every tab: 20dp top, 16dp sides. */
val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)
