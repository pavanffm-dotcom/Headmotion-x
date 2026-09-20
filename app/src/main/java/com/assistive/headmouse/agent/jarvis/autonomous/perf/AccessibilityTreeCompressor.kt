package com.assistive.headmouse.agent.jarvis.autonomous.perf

import com.assistive.headmouse.agent.jarvis.autonomous.state.SemanticNode
import com.assistive.headmouse.agent.jarvis.autonomous.state.WorldState

/**
 * Phase 20 Target 2: Accessibility Tree Compression.
 *
 * Formats live screen state into an ultra-compact semantic representation (< 150 tokens)
 * compared to verbose legacy dumps (> 600 tokens).
 * Retains exact node indices, actionable types, concise labels, and target coordinates.
 */
object AccessibilityTreeCompressor {

    /**
     * Compresses [worldState] with optional [relevantNodes] into a high-signal, token-efficient representation.
     */
    fun compress(
        worldState: WorldState,
        relevantNodes: List<SemanticNode>? = null,
        goal: String? = null,
        subgoal: String? = null,
        maxNodes: Int = 18
    ): String {
        val nodesToFormat = relevantNodes ?: RelevantNodeFilter.filterRelevantNodes(
            nodes = worldState.nodes,
            goal = goal,
            subgoal = subgoal,
            maxNodes = maxNodes
        )

        return buildString {
            append("[SCREEN] APP: ").append(worldState.foregroundPackage).append(" | pkg=").append(worldState.foregroundPackage)
            if (!worldState.foregroundActivity.isNullOrBlank()) {
                append(" act=").append(worldState.foregroundActivity.substringAfterLast('.'))
            }
            append(" kb=").append(if (worldState.isKeyboardVisible) "YES" else "NO")
            if (worldState.isDialogBlocking) append(" dl=YES")
            if (worldState.isLoadingIndicatorPresent) append(" loading=YES")
            append("\n")

            worldState.scrollState?.let { sc ->
                if (sc.isScrollable) {
                    append("[SCROLL] y=").append(sc.scrollOffsetY).append("px")
                    if (sc.totalItems > 0) append(" items=").append(sc.firstVisibleItem).append("/").append(sc.totalItems)
                    append("\n")
                }
            }

            worldState.lastActionSummary()?.let {
                append("[LAST_ACT] ").append(it).append("\n")
            }

            if (nodesToFormat.isEmpty()) {
                append("[NODES] None interactive\n")
            } else {
                append("[NODES] (").append(nodesToFormat.size).append("):\n")
                for (node in nodesToFormat) {
                    append(formatCompactNode(node)).append("\n")
                }
            }
        }.trim()
    }

    /**
     * Compact node string format:
     * e.g. #3[Btn]"Search"(540,210)
     *      #5[Inp]"Search apps & games"(400,210)
     */
    fun formatCompactNode(node: SemanticNode): String {
        val typeTag = when {
            node.isEditable -> "[Inp]"
            node.isClickable -> "[Btn]"
            node.isCheckable -> if (node.isChecked) "[Chk:ON]" else "[Chk:OFF]"
            node.isScrollable -> "[List]"
            else -> "[Txt]"
        }

        val cleanLabel = node.label.replace("\n", " ").replace("\"", "'").trim()
        val truncatedLabel = if (cleanLabel.length > 28) cleanLabel.take(25) + "..." else cleanLabel
        val coords = "(${node.centerX.toInt()},${node.centerY.toInt()})"

        return "#${node.index}$typeTag\"$truncatedLabel\"$coords"
    }
}
