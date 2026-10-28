package dev.edmel.offerchecker.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import dev.edmel.offerchecker.R
import dev.edmel.offerchecker.core.Evaluation
import dev.edmel.offerchecker.core.Verdict

/**
 * The Worth It verdict, drawn over Uber as a TYPE_ACCESSIBILITY_OVERLAY window (no "draw over
 * other apps" permission needed). Two looks, from the design system:
 *  - the verdict card (`wi-badge`), docked 8dp above Uber's offer card at the same width;
 *  - the compact pill (`wi-pill`), centred at the top of the screen.
 * Tap to dismiss. It never takes focus from Uber.
 */
class BadgeOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }
    private var view: View? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    val isShowing: Boolean get() = view != null

    /** Where the badge is on screen, so the screenshot reader can ignore its own text. */
    val screenRect: Rect?
        get() = view?.let {
            Rect(params.x, params.y, params.x + maxOf(it.width, it.measuredWidth), params.y + maxOf(it.height, it.measuredHeight))
        }

    private val font400 = font(R.font.geist_400)
    private val font600 = font(R.font.geist_600)
    private val font700 = font(R.font.geist_700)
    private val font800 = font(R.font.geist_800)

    /**
     * Shows [e]. In card mode it docks above [cardTop] (the offer card's top edge in screen px);
     * when that's unknown it sits near the top of the screen like the pill.
     */
    fun show(e: Evaluation, compact: Boolean, cardTop: Int?) {
        hide(animate = false)
        val screen = context.resources.displayMetrics
        val v = if (compact) pill(e) else card(e)
        v.contentDescription = spoken(e)
        v.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        v.setOnClickListener { hide() }

        if (compact) {
            v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = (screen.widthPixels - v.measuredWidth) / 2
            params.y = dp(TOP_MARGIN_DP)
        } else {
            val width = screen.widthPixels - 2 * dp(SIDE_MARGIN_DP)
            v.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED,
            )
            params.width = width
            params.x = dp(SIDE_MARGIN_DP)
            val docked = cardTop?.let { it - dp(DOCK_GAP_DP) - v.measuredHeight }
            params.y = if (docked != null && docked >= dp(TOP_MARGIN_DP)) docked else dp(TOP_MARGIN_DP)
        }
        wm.addView(v, params)
        view = v
        // badge-in: fade in and rise 12dp; no bounce.
        v.alpha = 0f
        v.translationY = dp(12f)
        v.animate().alpha(1f).translationY(0f).setDuration(IN_MS).setInterpolator(DecelerateInterpolator()).start()
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, AUTO_HIDE_MS)
    }

    fun hide(animate: Boolean = true) {
        handler.removeCallbacks(autoHide)
        val v = view ?: return
        view = null
        if (!animate) {
            runCatching { wm.removeView(v) }
            return
        }
        // badge-out
        v.animate().alpha(0f).setDuration(OUT_MS).setInterpolator(AccelerateInterpolator())
            .withEndAction { runCatching { wm.removeView(v) } }.start()
    }

    // ---------- Verdict card (wi-badge) ----------

    private fun card(e: Evaluation): View {
        val (fill, onFill) = colors(e.verdict)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(fill, dp(16f))
            elevation = dp(12f)
        }
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
        }
        head.addView(disc(e.verdict, discColor = onFill, glyphColor = fill), LinearLayout.LayoutParams(dp(40), dp(40)))
        head.addView(
            text(word(e.verdict), 22f, font800, onFill, letterSpacing = 0.02f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) },
        )
        if (e.verdict != Verdict.GREY) {
            head.addView(
                text(rate(e.hourly!!.toPlainString(), "/hr", 44f, 18f, onFill, null), 44f, font700, onFill, letterSpacing = -0.03f)
                    .apply { maxLines = 1 },
            )
        }
        root.addView(head)

        // Hairline in the text colour at 20%, then $/km and the reason.
        root.addView(
            View(context).apply { setBackgroundColor(onFill); alpha = 0.2f },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1.5f).toInt().coerceAtLeast(1))
                .apply { topMargin = dp(12) },
        )
        val foot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        if (e.verdict != Verdict.GREY) {
            val km = (e.perKm?.let { "$${it.toPlainString()}/km" } ?: "—/km") +
                if (e.offer?.stacked == true) " · ×2" else ""
            foot.addView(text(km, 17f, font700, onFill))
        }
        foot.addView(
            text(reason(e), 15f, font600, onFill).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) },
        )
        root.addView(foot)
        return root
    }

    // ---------- Compact pill (wi-pill) ----------

    private fun pill(e: Evaluation): View {
        val (fill, onFill) = colors(e.verdict)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(20), dp(6))
            minimumHeight = dp(52)
            background = rounded(Color.BLACK, dp(999f))
            elevation = dp(12f)
        }
        root.addView(disc(e.verdict, discColor = fill, glyphColor = onFill), LinearLayout.LayoutParams(dp(40), dp(40)))
        root.addView(
            text(word(e.verdict), 15f, font800, Color.WHITE, letterSpacing = 0.04f),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(12) },
        )
        if (e.verdict != Verdict.GREY) {
            root.addView(
                text(rate(e.hourly!!.toPlainString(), "/hr", 24f, 14f, Color.WHITE, INK_MUTED), 24f, font700, Color.WHITE, letterSpacing = -0.02f),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(12) },
            )
        }
        return root
    }

    // ---------- Pieces ----------

    /** The verdict glyph in a filled disc. */
    private fun disc(v: Verdict, discColor: Int, glyphColor: Int): View = FrameLayout(context).apply {
        background = rounded(discColor, dp(999f))
        addView(
            ImageView(context).apply {
                setImageResource(glyph(v))
                setColorFilter(glyphColor)
            },
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER),
        )
    }

    /** "$15.83" big with a smaller "/hr". */
    private fun rate(amount: String, unit: String, bigSp: Float, smallSp: Float, color: Int, unitColor: Int?): CharSequence {
        val sb = SpannableStringBuilder("$$amount")
        val start = sb.length
        sb.append(unit)
        sb.setSpan(AbsoluteSizeSpan(smallSp.toInt(), true), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(FontSpan(font600), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(unitColor ?: color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    private fun text(s: CharSequence, sp: Float, face: Typeface, color: Int, letterSpacing: Float = 0f) =
        TextView(context).apply {
            text = s
            typeface = face
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            this.letterSpacing = letterSpacing
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    private fun font(id: Int): Typeface = ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT_BOLD

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    /** A typeface for part of a text (TypefaceSpan(Typeface) needs Android 9). */
    private class FontSpan(private val face: Typeface) : MetricAffectingSpan() {
        override fun updateMeasureState(paint: TextPaint) { paint.typeface = face }
        override fun updateDrawState(paint: TextPaint) { paint.typeface = face }
    }

    companion object {
        const val AUTO_HIDE_MS = 20_000L
        private const val IN_MS = 180L
        private const val OUT_MS = 120L
        private const val SIDE_MARGIN_DP = 12
        private const val DOCK_GAP_DP = 8
        private const val TOP_MARGIN_DP = 48
        private val INK_MUTED = Color.rgb(0xA6, 0xA6, 0xA6)

        /** Fill and the text/glyph colour on it (tokens go/on-go, maybe/on-maybe, skip/on-skip, unknown/on-unknown). */
        fun colors(v: Verdict): Pair<Int, Int> = when (v) {
            Verdict.GREEN -> Color.rgb(0x3E, 0xDB, 0x84) to Color.BLACK
            Verdict.YELLOW -> Color.rgb(0xFF, 0xB0, 0x20) to Color.BLACK
            Verdict.RED -> Color.rgb(0xC8, 0x23, 0x1B) to Color.WHITE
            Verdict.GREY -> Color.rgb(0x54, 0x54, 0x54) to Color.WHITE
        }

        fun word(v: Verdict) = when (v) {
            Verdict.GREEN -> "GO"
            Verdict.YELLOW -> "MAYBE"
            Verdict.RED -> "SKIP"
            Verdict.GREY -> "CAN'T READ"
        }

        fun glyph(v: Verdict) = when (v) {
            Verdict.GREEN -> R.drawable.ic_verdict_go
            Verdict.YELLOW -> R.drawable.ic_verdict_maybe
            Verdict.RED -> R.drawable.ic_verdict_skip
            Verdict.GREY -> R.drawable.ic_verdict_unknown
        }

        fun reason(e: Evaluation) = when (e.verdict) {
            Verdict.GREEN -> "Meets all targets"
            Verdict.GREY -> "Check this offer yourself"
            else -> e.failedRule.orEmpty()
        }

        /** What TalkBack announces: "SKIP: $15.83 per hour, $0.53 per km. Under $20/hr target." */
        fun spoken(e: Evaluation): String =
            if (e.verdict == Verdict.GREY) "Can't read this offer. Check it yourself."
            else "${word(e.verdict)}: $${e.hourly} per hour, " +
                (e.perKm?.let { "$$it per km" } ?: "no distance") + ". ${reason(e)}."
    }
}
