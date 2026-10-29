package dev.edmel.offerchecker.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import dev.edmel.offerchecker.R
import dev.edmel.offerchecker.core.Evaluation
import dev.edmel.offerchecker.core.Thresholds
import dev.edmel.offerchecker.core.Verdict

/**
 * The nahmeter verdict, drawn over Uber as a TYPE_ACCESSIBILITY_OVERLAY window (no "draw over
 * other apps" permission needed). Always black, in both themes, so it lifts off any map:
 *  - the verdict card (`nah-card`): docked 20dp above Uber's offer card, 16dp from each side,
 *    the word on the left in its colour, $/hr on the right, the reason underneath;
 *  - the compact pill (`nah-pill`): at the top of the screen; tap it to expand to the card.
 * It stays until Uber's card goes (closed or accepted); tapping it dismisses it sooner.
 * It never takes focus from Uber.
 */
class BadgeOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Screen coordinates, so y matches the offer card's bounds; without IN_SCREEN the window is
        // placed below the status bar and the card lands that much too low, over Uber's card.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    val isShowing: Boolean get() = view != null

    /** Where the verdict is on screen, so the screenshot reader can ignore its own text. */
    val screenRect: Rect?
        get() = view?.let {
            val loc = IntArray(2).also(it::getLocationOnScreen)
            val (x, y) = if (it.isAttachedToWindow && it.width > 0) loc[0] to loc[1] - it.translationY.toInt() else params.x to params.y
            Rect(x, y, x + maxOf(it.width, it.measuredWidth), y + maxOf(it.height, it.measuredHeight))
        }

    private val font500 = font(R.font.geist_500)
    private val font600 = font(R.font.geist_600)
    private val font800 = font(R.font.geist_800)
    private val font900 = font(R.font.geist_900)

    /**
     * Shows [e]. The card docks above [cardTop] (the offer card's top edge in screen px); when
     * that's unknown it sits near the top of the screen like the pill.
     */
    fun show(e: Evaluation, t: Thresholds, compact: Boolean, cardTop: Int?) {
        hide(animate = false)
        val screen = context.resources.displayMetrics
        val v = if (compact) pill(e) else card(e, t)
        v.contentDescription = NahCopy.spoken(e, t)
        v.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        // Card: tap to dismiss. Pill: tap to expand to the full card.
        v.setOnClickListener { if (compact) show(e, t, compact = false, cardTop = cardTop) else hide() }

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
            // Anchored by its bottom edge to the top of Uber's card, so it never covers the card.
            val docked = cardTop?.let { it - dp(DOCK_GAP_DP) - v.measuredHeight }
            params.y = if (docked != null && docked >= dp(TOP_MARGIN_DP)) docked else dp(TOP_MARGIN_DP)
        }
        wm.addView(v, params)
        view = v
        // Some devices still offset overlay windows (status bar, cutout). Once laid out, measure
        // where the card actually is and move it so its bottom sits where it should.
        val wantedY = params.y
        v.post {
            if (view !== v) return@post
            val loc = IntArray(2).also(v::getLocationOnScreen)
            // Ignore the card-in animation's rise, which getLocationOnScreen includes.
            val off = loc[1] - v.translationY.toInt() - wantedY
            if (off != 0) {
                params.y = wantedY - off
                runCatching { wm.updateViewLayout(v, params) }
            }
        }
        // card-in: fade in and rise 12dp. No bounce; haptics carry the attention.
        v.alpha = 0f
        v.translationY = dp(12f)
        v.animate().alpha(1f).translationY(0f).setDuration(IN_MS).setInterpolator(DecelerateInterpolator()).start()
        // No timer: it stays while Uber's card does, and goes when the reader sees the card go
        // (closed or accepted). Tapping it still dismisses it.
    }

    fun hide(animate: Boolean = true) {
        val v = view ?: return
        view = null
        if (!animate) {
            runCatching { wm.removeView(v) }
            return
        }
        // card-out
        v.animate().alpha(0f).setDuration(OUT_MS).setInterpolator(AccelerateInterpolator())
            .withEndAction { runCatching { wm.removeView(v) } }.start()
    }

    // ---------- Verdict card (nah-card) ----------

    private fun card(e: Evaluation, t: Thresholds): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = blackShape(dp(22f))
            elevation = dp(12f)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val unknown = e.verdict == Verdict.GREY
        row.addView(
            text(NahCopy.word(e.verdict), if (unknown) 44f else 64f, font900, wordColor(e.verdict), if (unknown) -0.05f else -0.07f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (!unknown) {
            row.addView(
                text(rate("$${e.hourly!!.toPlainString()}", "/hr", 15), 36f, font800, Color.WHITE, -0.04f)
                    .apply { maxLines = 1; setPadding(dp(12), 0, 0, dp(6)) },
            )
        }
        root.addView(row)
        root.addView(
            text(NahCopy.cardReason(e, t), 15f, font500, CARD_MUTED),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) },
        )
        return root
    }

    // ---------- Compact pill (nah-pill) ----------

    private fun pill(e: Evaluation): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(22), 0)
            minimumHeight = dp(52)
            background = blackShape(dp(999f))
            elevation = dp(12f)
        }
        root.addView(text(NahCopy.word(e.verdict), 24f, font900, wordColor(e.verdict), -0.05f))
        if (e.verdict != Verdict.GREY) {
            root.addView(
                text(rate("$${e.hourly!!.toPlainString()}", "/hr", 13), 22f, font800, Color.WHITE, -0.03f),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(10) },
            )
        }
        return root
    }

    // ---------- Pieces ----------

    /** Black with a 1dp `card-edge` hairline. */
    private fun blackShape(radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Color.BLACK)
        setStroke(dp(1f).toInt().coerceAtLeast(1), CARD_EDGE)
    }

    /** "$15.83" with a smaller, muted "/hr". */
    private fun rate(amount: String, unit: String, unitSp: Int): CharSequence {
        val sb = SpannableStringBuilder(amount)
        val start = sb.length
        sb.append(unit)
        sb.setSpan(AbsoluteSizeSpan(unitSp, true), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(FontSpan(font600, 0f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(CARD_MUTED), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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

    /** A typeface (and tracking) for part of a text; TypefaceSpan(Typeface) needs Android 9. */
    private class FontSpan(private val face: Typeface, private val tracking: Float) : MetricAffectingSpan() {
        override fun updateMeasureState(paint: TextPaint) { paint.typeface = face; paint.letterSpacing = tracking }
        override fun updateDrawState(paint: TextPaint) { paint.typeface = face; paint.letterSpacing = tracking }
    }

    private fun font(id: Int): Typeface = ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT_BOLD

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    companion object {
        private const val IN_MS = 160L
        private const val OUT_MS = 100L
        private const val SIDE_MARGIN_DP = 16
        // The OCR estimate of Uber's card top runs about 8dp low, so this leaves a visible ~12dp gap.
        private const val DOCK_GAP_DP = 20
        private const val TOP_MARGIN_DP = 44
        private val CARD_EDGE = Color.rgb(0x2A, 0x2A, 0x2A)
        private val CARD_MUTED = Color.rgb(0xA8, 0xA8, 0xA8)

        /** The verdict word's colour on the black card: bet green, eh yellow, nah red. */
        fun wordColor(v: Verdict): Int = when (v) {
            Verdict.GREEN -> Color.rgb(0x30, 0xD1, 0x58)
            Verdict.YELLOW -> Color.rgb(0xFF, 0xD6, 0x0A)
            Verdict.RED -> Color.rgb(0xFF, 0x3B, 0x30)
            Verdict.GREY -> Color.rgb(0x8E, 0x8E, 0x93)
        }
    }
}
