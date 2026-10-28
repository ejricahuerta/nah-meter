package dev.edmel.offerchecker.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import dev.edmel.offerchecker.core.Evaluation
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.BadgeSize
import kotlin.math.abs

/**
 * The floating pill drawn over Uber as a TYPE_ACCESSIBILITY_OVERLAY window, so no
 * "draw over other apps" permission is needed. Drag to move; tap to dismiss.
 */
class BadgeOverlay(
    private val context: Context,
    private val onMoved: (x: Int, y: Int) -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }
    private var view: TextView? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    val isShowing: Boolean get() = view != null

    fun show(e: Evaluation, size: BadgeSize, x: Int, y: Int) {
        val v = view ?: createView().also {
            params.x = x
            params.y = y
            wm.addView(it, params)
            view = it
        }
        val (bg, fg) = colors(e.verdict)
        v.background = GradientDrawable().apply {
            cornerRadius = dp(24f)
            setColor(bg)
        }
        v.setTextColor(fg)
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * size.scale)
        val pad = dp(14f * size.scale).toInt()
        v.setPadding(pad, pad / 2, pad, pad / 2)
        v.text = label(e)
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, AUTO_HIDE_MS)
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun label(e: Evaluation): String {
        val stacked = if (e.offer?.stacked == true) "  ×2" else ""
        val second = when (e.verdict) {
            Verdict.GREY -> "check the offer yourself"
            Verdict.GREEN -> "meets your targets"
            else -> e.failedRule.orEmpty()
        }
        return "${e.ratesText}$stacked\n$second"
    }

    private fun colors(v: Verdict): Pair<Int, Int> = when (v) {
        Verdict.GREEN -> Color.rgb(0x1E, 0x8E, 0x3E) to Color.WHITE
        Verdict.YELLOW -> Color.rgb(0xF9, 0xAB, 0x00) to Color.BLACK
        Verdict.RED -> Color.rgb(0xD9, 0x30, 0x25) to Color.WHITE
        Verdict.GREY -> Color.rgb(0x5F, 0x63, 0x68) to Color.WHITE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): TextView = TextView(context).apply {
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        elevation = dp(6f)
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = params.x; startY = params.y
                    dragged = false
                    handler.removeCallbacks(autoHide)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (dragged || abs(dx) > dp(6f) || abs(dy) > dp(6f)) {
                        dragged = true
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        view?.let { wm.updateViewLayout(it, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) {
                        onMoved(params.x, params.y)
                        handler.postDelayed(autoHide, AUTO_HIDE_MS)
                    } else {
                        hide()
                    }
                }
            }
            true
        }
    }

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    companion object {
        const val AUTO_HIDE_MS = 20_000L
    }
}
