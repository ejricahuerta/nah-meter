package dev.edmel.offerchecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.R
import dev.edmel.offerchecker.core.Verdict

/** Worth It design tokens (dark theme, the default), from the Worth It design system. */
object Wi {
    val ground = Color(0xFF000000)
    val surface = Color(0xFF141414)
    val surfaceRaised = Color(0xFF262626)
    val line = Color(0xFF2B2B2B)
    val controlBorder = Color(0xFF6B6B6B)
    val ink = Color(0xFFFFFFFF)
    val inkMuted = Color(0xFFA6A6A6)
    val onInk = Color(0xFF000000)
    val lane = Color(0xFF5B9BFF)
    val go = Color(0xFF3EDB84)
    val maybe = Color(0xFFFFB020)
    val skip = Color(0xFFC8231B)
    val unknown = Color(0xFF545454)
    val goInk = Color(0xFF3EDB84)
    val skipInk = Color(0xFFFF6B5E)

    val geist = FontFamily(
        Font(R.font.geist_400, FontWeight.W400),
        Font(R.font.geist_500, FontWeight.W500),
        Font(R.font.geist_600, FontWeight.W600),
        Font(R.font.geist_700, FontWeight.W700),
        Font(R.font.geist_800, FontWeight.W800),
    )

    fun style(size: TextUnit, weight: Int, line: TextUnit = TextUnit.Unspecified, tracking: TextUnit = 0.em, color: Color = ink) =
        TextStyle(
            fontFamily = geist, fontSize = size, fontWeight = FontWeight(weight), lineHeight = line,
            letterSpacing = tracking, color = color, fontFeatureSettings = "tnum",
        )

    val title = style(28.sp, 700, 34.sp, (-0.015).em)
    val heading = style(20.sp, 600, 26.sp, (-0.01).em)
    val body = style(16.sp, 400, 24.sp)
    val bodyStrong = style(16.sp, 600, 22.sp)
    val label = style(14.sp, 400, 20.sp, color = inkMuted)
    val sectionLabel = style(14.sp, 600, 20.sp, color = inkMuted)

    /** Fill and the colour on it (go/on-go, maybe/on-maybe, skip/on-skip, unknown/on-unknown). */
    fun fill(v: Verdict) = when (v) {
        Verdict.GREEN -> go to Color.Black
        Verdict.YELLOW -> maybe to Color.Black
        Verdict.RED -> skip to Color.White
        Verdict.GREY -> unknown to Color.White
    }

    fun word(v: Verdict) = when (v) {
        Verdict.GREEN -> "GO"
        Verdict.YELLOW -> "MAYBE"
        Verdict.RED -> "SKIP"
        Verdict.GREY -> "CAN'T READ"
    }
}

/** Outline icons drawn for Worth It on a 24px grid (verdict glyphs 3px, interface icons 2px). */
object WiIcons {
    private fun icon(name: String, stroke: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val go = icon("go", 3f, "M5 12.5l4.5 4.5L19 7.5")
    val maybe = icon("maybe", 3f, "M5 13.5c2-3 4-3 7-1.5s5 1.5 7-1.5")
    val skip = icon("skip", 3f, "M7 7l10 10M17 7L7 17")
    val unknown = icon("unknown", 3f, "M9.2 9.2a3 3 0 1 1 4.3 2.7c-1 .5-1.5 1.2-1.5 2.3", "M12 18h.01")
    val alert = icon("alert", 3f, "M12 7v6", "M12 17h.01")
    val status = icon("status", 2f, "M4 17a8 8 0 0 1 16 0", "M12 17l4-5")
    val sliders = icon(
        "sliders", 2f, "M4 7h10M18 7h2M4 17h4M12 17h8",
        "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )
    val list = icon("list", 2f, "M8 6h12M8 12h12M8 18h12", "M4 6h.01M4 12h.01M4 18h.01")
    val minus = icon("minus", 2.5f, "M6 12h12")
    val plus = icon("plus", 2.5f, "M6 12h12M12 6v12")

    fun verdict(v: Verdict) = when (v) {
        Verdict.GREEN -> go
        Verdict.YELLOW -> maybe
        Verdict.RED -> skip
        Verdict.GREY -> unknown
    }
}

/** The "Worth It" wordmark: Geist Bold, tight tracking, monochrome. */
@Composable
fun Wordmark(size: TextUnit = 24.sp) {
    Text("Worth It", style = Wi.style(size, 700, tracking = (-0.03).em))
}

/** "$15.83" with a smaller "/hr". */
private fun rateText(amount: String, unit: String, unitSize: TextUnit, unitColor: Color? = null) = buildAnnotatedString {
    append(amount)
    withStyle(SpanStyle(fontSize = unitSize, fontWeight = FontWeight.W600, letterSpacing = 0.em, color = unitColor ?: Color.Unspecified)) {
        append(unit)
    }
}

/** VerdictBadge: the verdict card (here in the app, without its floating shadow). */
@Composable
fun VerdictCard(verdict: Verdict, hourly: String?, footLeft: String?, reason: String, modifier: Modifier = Modifier) {
    val (fill, on) = Wi.fill(verdict)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fill).padding(16.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = if (verdict == Verdict.GREY) "Can't read this offer. $reason."
                else "${Wi.word(verdict)}: $hourly per hour. ${footLeft.orEmpty()}. $reason."
            },
    ) {
        Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Disc(verdict, on, fill, 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(Wi.word(verdict), style = Wi.style(22.sp, 800, 26.sp, 0.02.em, on), modifier = Modifier.weight(1f))
            if (hourly != null) {
                Text(rateText(hourly, "/hr", 18.sp), style = Wi.style(44.sp, 700, 44.sp, (-0.03).em, on), maxLines = 1)
            }
        }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.5.dp).background(on.copy(alpha = 0.2f)))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (footLeft != null) Text(footLeft, style = Wi.style(17.sp, 700, 22.sp, color = on))
            Text(
                reason, style = Wi.style(15.sp, 600, 20.sp, color = on).copy(textAlign = TextAlign.End),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
    }
}

/** The verdict glyph in a filled disc. */
@Composable
fun Disc(v: Verdict, discColor: Color, glyphColor: Color, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(discColor), contentAlignment = Alignment.Center) {
        Icon(WiIcons.verdict(v), contentDescription = null, tint = glyphColor, modifier = Modifier.size(size * 0.55f))
    }
}

/** VerdictPill: compact verdict, optionally with $/hr. */
@Composable
fun VerdictPill(v: Verdict, hourly: String? = null, background: Color = Wi.ground) {
    val (fill, on) = Wi.fill(v)
    Row(
        Modifier.height(52.dp).clip(CircleShape).background(background).padding(start = 6.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Disc(v, fill, on, 40.dp)
        Text(Wi.word(v), style = Wi.style(15.sp, 800, 20.sp, 0.04.em))
        if (hourly != null) Text(rateText(hourly, "/hr", 14.sp, Wi.inkMuted), style = Wi.style(24.sp, 700, 28.sp, (-0.02).em))
    }
}

/** VerdictChip, for lists: "GO", "3 SKIP". */
@Composable
fun VerdictChip(v: Verdict, text: String = Wi.word(v)) {
    val (fill, on) = Wi.fill(v)
    Row(
        Modifier.height(28.dp).clip(CircleShape).background(fill).padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(WiIcons.verdict(v), contentDescription = null, tint = on, modifier = Modifier.size(16.dp))
        Text(text, style = Wi.style(12.sp, 800, 16.sp, 0.05.em, on))
    }
}

/** StatusPill: "Reading offers" with a green dot when on. */
@Composable
fun StatusPill(on: Boolean, text: String) {
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(Wi.surfaceRaised).padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(18.dp).clip(CircleShape).background(Wi.goInk.copy(alpha = 0.22f)))
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) Wi.goInk else Wi.inkMuted))
        }
        Text(text, style = Wi.style(16.sp, 600, 20.sp))
    }
}

enum class WiButtonKind { Primary, Secondary, Tertiary }

/** Button: primary (ink fill), secondary (raised), tertiary (underlined text). At least 56dp tall unless small. */
@Composable
fun WiButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    kind: WiButtonKind = WiButtonKind.Primary, small: Boolean = false, enabled: Boolean = true,
) {
    val (bg, fg) = when (kind) {
        WiButtonKind.Primary -> Wi.ink to Wi.onInk
        WiButtonKind.Secondary -> Wi.surfaceRaised to Wi.ink
        WiButtonKind.Tertiary -> Color.Transparent to Wi.ink
    }
    val shape = if (small) CircleShape else RoundedCornerShape(8.dp)
    Box(
        modifier.heightIn(min = if (small) 40.dp else 56.dp).clip(shape).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = if (kind == WiButtonKind.Tertiary) 12.dp else if (small) 16.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Wi.style(if (small) 15.sp else 17.sp, 600, 24.sp, color = fg).copy(
                textDecoration = if (kind == WiButtonKind.Tertiary) TextDecoration.Underline else null,
            ),
        )
    }
}

/** Switch: outline when off, ink when on. */
@Composable
fun WiSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Box(
        Modifier.size(52.dp, 32.dp).clip(CircleShape)
            .background(if (checked) Wi.ink else Color.Transparent)
            .border(2.dp, if (checked) Wi.ink else Wi.controlBorder, CircleShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label },
    ) {
        // Thumb 20dp, 4dp inside the 2dp border.
        Box(
            Modifier.offset(x = if (checked) 26.dp else 6.dp, y = 6.dp).size(20.dp)
                .clip(CircleShape).background(if (checked) Wi.onInk else Wi.controlBorder),
        )
    }
}

/** A list group: rows on `surface`, separated by hairlines. */
@Composable
fun WiGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Wi.surface), content = content)
}

@Composable
fun WiDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Wi.line))

/** A row: optional leading, title over description, optional trailing. */
@Composable
fun WiRow(
    title: String, desc: String? = null, descColor: Color = Wi.inkMuted, minHeight: Dp = 64.dp,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Wi.style(16.sp, 600, 22.sp))
            if (desc != null) Text(desc, style = Wi.style(14.sp, 400, 20.sp, color = descColor))
        }
        trailing?.invoke(this)
    }
}

enum class StepState { Done, Todo, Manual }

/** SetupStep: a green check when done, an outlined "!" when to do, a neutral dot when it can't be checked. */
@Composable
fun SetupStep(state: StepState, title: String, desc: String, action: String? = null, onAction: () -> Unit = {}) {
    val trailing: (@Composable RowScope.() -> Unit)? =
        if (action != null) { { WiButton(action, onAction, kind = WiButtonKind.Secondary, small = true) } } else null
    WiRow(
        title = title, desc = desc,
        descColor = if (state == StepState.Todo) Wi.skipInk else Wi.inkMuted,
        leading = {
            val mod = Modifier.size(32.dp).clip(CircleShape)
            when (state) {
                StepState.Done -> Box(mod.background(Wi.go), contentAlignment = Alignment.Center) {
                    Icon(WiIcons.go, contentDescription = "Done", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
                StepState.Todo -> Box(mod.background(Wi.surfaceRaised).border(2.dp, Wi.skipInk, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(WiIcons.alert, contentDescription = "To do", tint = Wi.skipInk, modifier = Modifier.size(18.dp))
                }
                StepState.Manual -> Box(mod.background(Wi.surfaceRaised), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Wi.inkMuted))
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
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = Wi.style(14.sp, 500, 20.sp, color = Wi.inkMuted))
            Text(
                if (unit == null) buildAnnotatedString { append(value) } else rateText(value, unit, 15.sp, Wi.inkMuted),
                style = Wi.style(24.sp, 700, 28.sp, (-0.02).em),
            )
            Text(hint, style = Wi.style(13.sp, 400, 18.sp, color = Wi.inkMuted))
        }
        RoundButton(WiIcons.minus, minusLabel, onMinus)
        RoundButton(WiIcons.plus, plusLabel, onPlus)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Wi.surfaceRaised)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Wi.ink, modifier = Modifier.size(20.dp))
    }
}

/** Screen padding used by every tab: 20dp top, 16dp sides. */
val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)
