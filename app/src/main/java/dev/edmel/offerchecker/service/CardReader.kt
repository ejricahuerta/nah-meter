package dev.edmel.offerchecker.service

import android.view.accessibility.AccessibilityNodeInfo
import dev.edmel.offerchecker.core.OfferParser

/**
 * Finds the offer card in Uber's view tree and returns only the text inside it,
 * so map labels and street names elsewhere on screen are never read.
 *
 * The card is the smallest ancestor of the "min (… km) total" line that also holds a pay amount.
 */
object CardReader {
    private const val MAX_NODES = 3000
    private const val MAX_DEPTH = 60
    private const val MAX_ANCESTORS = 12

    /** Card texts, or null when no offer card is on screen. */
    fun findCardTexts(roots: List<AccessibilityNodeInfo>): List<String>? {
        for (root in roots) {
            val trip = findTripNode(root) ?: continue
            var node: AccessibilityNodeInfo? = trip
            var steps = 0
            while (node != null && steps <= MAX_ANCESTORS) {
                val texts = collectTexts(node)
                if (OfferParser.containsPay(texts)) return texts
                node = node.parent
                steps++
            }
            // Trip line with no pay nearby: the parser reports it as unreadable.
            return collectTexts(trip)
        }
        return null
    }

    private fun nodeTexts(n: AccessibilityNodeInfo): List<String> =
        listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).filter { it.isNotBlank() }

    private fun findTripNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val (n, depth) = stack.removeLast()
            visited++
            if (nodeTexts(n).any(OfferParser::isTripLine)) return n
            if (depth < MAX_DEPTH) {
                for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.addLast(it to depth + 1) }
            }
        }
        return null
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
