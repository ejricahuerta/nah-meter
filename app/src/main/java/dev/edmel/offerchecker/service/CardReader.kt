package dev.edmel.offerchecker.service

import android.view.accessibility.AccessibilityNodeInfo
import dev.edmel.offerchecker.core.OfferParser

/**
 * Finds the offer card in Uber's view tree and returns only the text inside it,
 * so map labels and street names elsewhere on screen are never read.
 *
 * The trip line is the smallest view whose text (its own plus its children's, joined) reads
 * "… min (… km) total", which also covers Uber drawing that line as several text pieces.
 * The card is the smallest ancestor of the trip line that also holds a pay amount.
 */
object CardReader {
    private const val MAX_NODES = 3000
    private const val MAX_DEPTH = 60
    private const val MAX_ANCESTORS = 12
    private const val MAX_JOINED = 400

    class Scan(
        /** Texts inside the offer card, or null when no card is on screen. */
        val cardTexts: List<String>?,
        /** Every text on Uber's screen, for the diagnostics. */
        val screenTexts: List<String>,
        /** The card's top edge in screen px, to dock the verdict card above it. */
        val cardTop: Int? = null,
    )

    fun scan(roots: List<AccessibilityNodeInfo>): Scan {
        val screen = roots.flatMap { collectTexts(it) }.distinct()
        for (root in roots) {
            val finder = TripFinder()
            finder.visit(root, 0)
            val trip = finder.trip ?: continue
            var node: AccessibilityNodeInfo? = trip
            var steps = 0
            while (node != null && steps <= MAX_ANCESTORS) {
                val texts = collectTexts(node)
                if (OfferParser.containsPay(texts)) {
                    val card = node
                    val top = android.graphics.Rect().also { card.getBoundsInScreen(it) }.top
                    return Scan(texts + finder.tripText, screen, top)
                }
                node = node.parent
                steps++
            }
            // Trip line with no pay nearby: the parser reports it as unreadable.
            return Scan(collectTexts(trip) + finder.tripText, screen)
        }
        return Scan(null, screen)
    }

    private fun nodeTexts(n: AccessibilityNodeInfo): List<String> =
        listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).filter { it.isNotBlank() }

    /** Depth-first search for the deepest node whose joined text is a trip line. */
    private class TripFinder {
        var trip: AccessibilityNodeInfo? = null
        var tripText: String = ""
        private var visited = 0

        /** Returns the node's joined text (capped), and records the trip node on the way back up. */
        fun visit(n: AccessibilityNodeInfo, depth: Int): String {
            // A card Uber has closed but left in its view tree is off screen: it isn't an offer.
            if (trip != null || visited++ >= MAX_NODES || !n.isVisibleToUser) return ""
            val parts = nodeTexts(n).toMutableList()
            if (depth < MAX_DEPTH) {
                for (i in 0 until n.childCount) {
                    val child = n.getChild(i) ?: continue
                    val t = visit(child, depth + 1)
                    if (trip != null) return ""
                    if (t.isNotEmpty()) parts += t
                }
            }
            val joined = parts.joinToString(" ").replace('\n', ' ').take(MAX_JOINED)
            if (OfferParser.isTripLine(joined)) {
                trip = n
                tripText = joined
            }
            return joined
        }
    }

    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val out = mutableListOf<String>()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val (n, depth) = stack.removeLast()
            visited++
            out += nodeTexts(n)
            if (depth < MAX_DEPTH) {
                for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.addLast(it to depth + 1) }
            }
        }
        return out.distinct()
    }
}
