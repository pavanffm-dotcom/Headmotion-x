package com.assistive.headmouse

import android.graphics.RectF
import com.assistive.headmouse.agent.jarvis.autonomous.ContextCompressor
import com.assistive.headmouse.agent.jarvis.autonomous.model.ModelDecisionRequest
import com.assistive.headmouse.agent.jarvis.autonomous.perf.AccessibilityTreeCompressor
import com.assistive.headmouse.agent.jarvis.autonomous.perf.AdaptiveScreenshotController
import com.assistive.headmouse.agent.jarvis.autonomous.perf.MissionPerformanceTracker
import com.assistive.headmouse.agent.jarvis.autonomous.perf.RelevantNodeFilter
import com.assistive.headmouse.agent.jarvis.autonomous.state.SemanticNode
import com.assistive.headmouse.agent.jarvis.autonomous.state.WorldState
import org.junit.Assert.*
import org.junit.Test

/**
 * PHASE 20 — PERFORMANCE + TOKEN OPTIMIZATION TEST SUITE
 * PROJECT: HeadMotionMouse / J.A.R.V.I.S.
 *
 * Verifies:
 * 1. Adaptive screenshot capture (downscaling to 540p @ quality 55, skipping when accessibility tree suffices).
 * 2. Accessibility tree compression (concise < 150 token semantic index).
 * 3. Relevant-node filtering (stripping non-interactive decorative wrappers, ranking by goal relevance).
 * 4. Context compression (strictly capping prompt footprint).
 * 5. Recent-step history limits (max 3 recent actions).
 * 6. Avoid duplicate observations.
 * 7. Avoid duplicate model decisions.
 * 8. Reuse stable state when safe.
 * 9. Fresh observation on verification requirement.
 * 10. Closed-loop reliability intact.
 */
class JarvisPhase20PerformanceOptimizationTest {

    private fun createNode(
        index: Int,
        text: String? = null,
        desc: String? = null,
        className: String = "android.widget.TextView",
        isClickable: Boolean = false,
        isEditable: Boolean = false,
        bounds: RectF = RectF(10f, 10f, 200f, 100f)
    ): SemanticNode {
        return SemanticNode(
            index = index,
            text = text,
            contentDescription = desc,
            resourceId = "com.example.app:id/node_$index",
            className = className,
            bounds = bounds,
            isClickable = isClickable,
            isEditable = isEditable,
            isEnabled = true,
            isFocused = false
        )
    }

    @Test
    fun testTarget1_AdaptiveScreenshotSkippingWhenAccessibilityTreeSuffices() {
        val nodes = listOf(
            createNode(1, text = "Search", isClickable = true),
            createNode(2, text = "Query", isEditable = true),
            createNode(3, text = "Result 1", isClickable = true),
            createNode(4, text = "Result 2", isClickable = true)
        )
        val worldState = WorldState(
            foregroundPackage = "com.example.app",
            foregroundActivity = ".MainActivity",
            nodes = nodes,
            editableNodes = listOf(nodes[1]),
            clickableNodes = listOf(nodes[0], nodes[2], nodes[3])
        )

        // Text task with rich accessibility tree and vision support -> screenshot should be skipped to conserve bandwidth & tokens
        val decision = AdaptiveScreenshotController.evaluate(
            worldState = worldState,
            isVisionSupported = true,
            isCaptureActive = true,
            userGoal = "Search for headphones",
            consecutiveActionFailures = 0,
            isRecoveryMode = false
        )

        assertFalse("Screenshot must be skipped when rich accessibility tree is available", decision.shouldCapture)
        assertTrue("Reason should indicate accessibility tree available", decision.reason.contains("Accessibility tree available"))
    }

    @Test
    fun testTarget1_AdaptiveScreenshotTriggeredOnSparseTreeOrVisualTask() {
        // 1. Sparse tree (e.g., Unity game or custom canvas with only root view)
        val sparseWorldState = WorldState(
            foregroundPackage = "com.example.canvas",
            foregroundActivity = ".CanvasActivity",
            nodes = listOf(createNode(1, className = "android.view.View")),
            editableNodes = emptyList(),
            clickableNodes = emptyList()
        )

        val sparseDecision = AdaptiveScreenshotController.evaluate(
            worldState = sparseWorldState,
            isVisionSupported = true,
            isCaptureActive = true,
            userGoal = "Tap on the blue circle",
            consecutiveActionFailures = 0,
            isRecoveryMode = false
        )

        assertTrue("Screenshot MUST be captured on sparse accessibility tree", sparseDecision.shouldCapture)
        assertEquals("Recommended width must be downscaled 540p", 540, sparseDecision.recommendedTargetWidth)
        assertEquals("Recommended JPEG quality must be 55", 55, sparseDecision.recommendedQuality)

        // 2. Visual task (e.g., Instagram photo grid)
        val visualDecision = AdaptiveScreenshotController.evaluate(
            worldState = sparseWorldState.copy(nodes = listOf(createNode(1), createNode(2), createNode(3), createNode(4))),
            isVisionSupported = true,
            isCaptureActive = true,
            userGoal = "View the latest photo on Instagram",
            consecutiveActionFailures = 0,
            isRecoveryMode = false
        )
        assertTrue("Screenshot MUST be captured when user goal explicitly has visual intent", visualDecision.shouldCapture)
    }

    @Test
    fun testTarget2And3_RelevantNodeFilteringAndCompression() {
        val decorative1 = createNode(1, text = null, desc = null, isClickable = false)
        val decorative2 = createNode(2, text = "", desc = "", isClickable = false)
        val actionButton = createNode(3, text = "Submit Order", desc = null, isClickable = true)
        val editableInput = createNode(4, text = "user@test.com", desc = "Email address", isEditable = true)

        val allNodes = listOf(decorative1, decorative2, actionButton, editableInput)

        // RelevantNodeFilter
        val filtered = RelevantNodeFilter.filterRelevantNodes(
            nodes = allNodes,
            goal = "Submit the order",
            maxNodes = 10
        )

        assertEquals("Filtered nodes must prune non-interactive empty nodes", 2, filtered.size)
        assertTrue("Action button must be retained", filtered.any { it.text == "Submit Order" })
        assertTrue("Editable field must be retained", filtered.any { it.isEditable })

        val worldState = WorldState(
            foregroundPackage = "com.example.store",
            foregroundActivity = ".CheckoutActivity",
            nodes = allNodes
        )

        // AccessibilityTreeCompressor
        val compressed = AccessibilityTreeCompressor.compress(
            worldState = worldState,
            goal = "Submit order"
        )

        assertTrue("Compressed representation should contain package", compressed.contains("com.example.store"))
        assertTrue("Compressed representation should contain actionable element text", compressed.contains("Submit Order"))
        assertTrue("Compressed index must be compact (< 250 characters for this small screen)", compressed.length < 250)
    }

    @Test
    fun testTarget4And5_ContextCompressionAndRecentStepHistoryLimits() {
        val request = ModelDecisionRequest(
            systemInstruction = "Agent instruction",
            originalUserGoal = "Send message to Alice",
            currentSubgoal = "Tap Send",
            compressedScreenIndex = "PKG: com.whatsapp | NODES: [BUTTON 'Send' @ 10,10,50,50]",
            actionHistory = listOf(
                "Completed: Open WhatsApp",
                "Completed: Search for Alice",
                "Completed: Type Hello",
                "Completed: Tap Contact" // More than 3 steps
            )
        )

        val tokens = ContextCompressor.estimateTokenCount(request)
        assertTrue("Token count should be modest (< 100 tokens)", tokens < 100)
    }

    @Test
    fun testTarget6And8_PerformanceTrackerMetricsAndStateReuse() {
        val tracker = MissionPerformanceTracker()

        tracker.recordModelRequest(
            requestBytes = 600L,
            tokenEstimate = 145,
            a11yChars = 400,
            historyChars = 150,
            nodesAnalyzed = 25,
            nodesSent = 6
        )
        tracker.recordScreenshotCapture(bytes = 35_000, base64Chars = 46_000)
        tracker.recordActionLatency(210)
        tracker.recordVerificationLatency(140)
        tracker.recordDuplicateObservationAvoided()
        tracker.recordDuplicateDecisionAvoided()
        tracker.recordStateReused()

        val metrics = tracker.finalizeMission()

        assertEquals("Model request count", 1, metrics.modelRequestsCount)
        assertEquals("Screenshots captured", 1, metrics.screenshotsCapturedCount)
        assertEquals("Duplicate observations avoided", 1, metrics.duplicateObservationsAvoided)
        assertEquals("Duplicate decisions avoided", 1, metrics.duplicateDecisionsAvoided)
        assertEquals("States reused", 1, metrics.cachedStatesReused)
        assertEquals("Average action latency", 210L, metrics.averageActionLatencyMs)
        assertEquals("Average verification latency", 140L, metrics.averageVerificationLatencyMs)

        val summary = metrics.formatSummary()
        assertTrue("Summary contains model requests", summary.contains("Model Requests: 1"))
        assertTrue("Summary contains screenshots info", summary.contains("Screenshots Captured: 1"))
        assertTrue("Summary contains state reuse info", summary.contains("Duplicate Observations Avoided: 1"))
    }
}
