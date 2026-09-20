package com.assistive.headmouse.agent.jarvis.autonomous.perf

import com.assistive.headmouse.agent.jarvis.autonomous.state.SemanticNode
import java.util.Locale

/**
 * Phase 20 Target 3: Relevant-Node Filtering.
 * 
 * Intelligently filters out background layout containers, decorative icons, status bar
 * elements, and duplicate bounding boxes from the raw Accessibility tree.
 * Ranks nodes by semantic relevance to the active goal & subgoal.
 * Preserves the exact original [SemanticNode.index] so tool actions remain 100% accurate.
 */
object RelevantNodeFilter {

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "to", "for", "in", "on", "at", "of", "with", "by", "from",
        "app", "please", "sir", "open", "launch", "kholo", "chalao", "karo", "search", "dhoondho"
    )

    /**
     * Filters and prioritizes nodes from the live observation.
     * Guaranteed to keep high-signal interactive elements while strictly pruning non-actionable clutter.
     */
    fun filterRelevantNodes(
        nodes: List<SemanticNode>,
        goal: String? = null,
        subgoal: String? = null,
        maxNodes: Int = 18
    ): List<SemanticNode> {
        if (nodes.isEmpty()) return emptyList()

        val keywords = extractKeywords(goal, subgoal)
        val candidateNodes = mutableListOf<ScoredNode>()
        val seenBounds = mutableListOf<SemanticNode>()

        for (node in nodes) {
            // 1. Filter zero-area or invalid geometry (only when coordinates are positive)
            if (node.right > 0f && node.bottom > 0f && (node.left >= node.right || node.top >= node.bottom)) continue

            // 2. Filter system status bar non-interactive noise (battery, time, carrier)
            if (isSystemBarNoise(node, keywords)) continue

            // 3. Filter empty, non-interactive layout containers
            val hasLabel = node.label.isNotBlank()
            val isActionable = node.isClickable || node.isEditable || node.isScrollable || node.isCheckable
            if (!hasLabel && !isActionable) continue

            // 4. Overlapping node deduplication (e.g., FrameLayout wrapping TextView with identical bounds)
            val duplicate = seenBounds.find { existing ->
                isGeometryOverlapping(existing, node) && (existing.label == node.label || existing.label.isBlank() || node.label.isBlank())
            }
            if (duplicate != null) {
                // If the new node is more actionable, replace the previous entry
                if (isActionable && (!duplicate.isClickable && !duplicate.isEditable)) {
                    seenBounds.remove(duplicate)
                    candidateNodes.removeAll { it.node == duplicate }
                    seenBounds.add(node)
                } else {
                    continue // Skip redundant wrapper
                }
            } else {
                seenBounds.add(node)
            }

            // 5. Score relevance
            val score = scoreNodeRelevance(node, keywords)
            candidateNodes.add(ScoredNode(node, score))
        }

        // Rank by score descending, then retain original screen reading order (top-to-bottom, left-to-right)
        val topCandidates = candidateNodes
            .sortedByDescending { it.score }
            .take(maxNodes)
            .map { it.node }
            .sortedWith(compareBy({ it.top }, { it.left }))

        return topCandidates
    }

    private fun scoreNodeRelevance(node: SemanticNode, keywords: Set<String>): Int {
        var score = 0

        // High priority: Text inputs and focused elements
        if (node.isEditable) score += 20
        if (node.isFocused) score += 15

        // Intent keyword matching
        val nodeLabelLower = node.label.lowercase(Locale.ROOT)
        for (kw in keywords) {
            if (nodeLabelLower.contains(kw)) {
                score += 30
            }
        }

        // Interaction capability
        if (node.isClickable) score += 12
        if (node.isCheckable) score += 10
        if (node.isScrollable) score += 8

        // Non-empty label boost
        if (node.label.isNotBlank()) score += 5

        // Penalize offscreen or extreme edge coordinates
        if (node.top < 0f || node.bottom > 2500f) score -= 15

        return score
    }

    private fun isSystemBarNoise(node: SemanticNode, keywords: Set<String>): Boolean {
        val resId = node.resourceId?.lowercase(Locale.ROOT) ?: ""
        if (resId.contains("status_bar") || resId.contains("battery") || resId.contains("clock") || resId.contains("wifi")) {
            // Only keep if user explicitly targets status bar / battery / time
            return !keywords.any { it.contains("battery") || it.contains("wifi") || it.contains("time") }
        }
        // Top 40px non-interactive clock / status bar text
        if (node.top < 45f && !node.isClickable && !node.isEditable) {
            return true
        }
        return false
    }

    private fun isGeometryOverlapping(a: SemanticNode, b: SemanticNode): Boolean {
        if (a.right <= 0f || b.right <= 0f || a.bottom <= 0f || b.bottom <= 0f) return false
        val dx = kotlin.math.abs(a.centerX - b.centerX)
        val dy = kotlin.math.abs(a.centerY - b.centerY)
        val widthDiff = kotlin.math.abs((a.right - a.left) - (b.right - b.left))
        val heightDiff = kotlin.math.abs((a.bottom - a.top) - (b.bottom - b.top))
        return dx < 6f && dy < 6f && widthDiff < 10f && heightDiff < 10f
    }

    private fun extractKeywords(goal: String?, subgoal: String?): Set<String> {
        val combined = "${goal ?: ""} ${subgoal ?: ""}".lowercase(Locale.ROOT)
        return combined.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && !STOP_WORDS.contains(it) }
            .toSet()
    }

    private data class ScoredNode(val node: SemanticNode, val score: Int)
}
