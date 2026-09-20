package com.assistive.headmouse.agent.jarvis.autonomous.perf

import com.assistive.headmouse.agent.jarvis.autonomous.state.WorldState
import java.util.Locale

/**
 * Phase 20 Target 1: Adaptive Screenshot Capture.
 * 
 * Regulates visual frame capture to eliminate redundant 100KB+ payloads when
 * rich, high-confidence Accessibility tree semantics are already available.
 * 
 * Invariants:
 * 1. ZERO screenshot overhead when Accessibility tree provides sufficient interactive elements.
 * 2. Visual frame is ALWAYS captured when Accessibility is sparse (< 2 interactive elements),
 *    in recovery mode (after failed action / UNCHANGED state), or on explicit visual requests.
 * 3. When captured, downscales adaptively to 540p @ quality 55 to reduce payload by > 75%.
 */
object AdaptiveScreenshotController {

    data class Decision(
        val shouldCapture: Boolean,
        val reason: String,
        val recommendedQuality: Int = 55,
        val recommendedTargetWidth: Int = 540
    )

    private val VISUAL_INTENT_KEYWORDS = setOf(
        "look at", "picture", "photo", "image", "what is on screen", "describe screen",
        "canvas", "game", "camera", "color", "qr", "barcode", "see", "dekh"
    )

    /**
     * Determines whether a screenshot frame must be captured for the current step.
     */
    fun evaluate(
        worldState: WorldState,
        isVisionSupported: Boolean,
        isCaptureActive: Boolean,
        userGoal: String? = null,
        consecutiveActionFailures: Int = 0,
        isRecoveryMode: Boolean = false,
        forceVisual: Boolean = false
    ): Decision {
        if (!isVisionSupported) {
            return Decision(false, "Model does not support vision capabilities")
        }

        if (!isCaptureActive) {
            return Decision(false, "MediaProjection screen capture service is inactive")
        }

        if (forceVisual) {
            return Decision(true, "Visual capture explicitly requested by verification engine")
        }

        // 1. Recovery Mode: If previous action failed or produced UNCHANGED, vision is needed for re-grounding
        if (isRecoveryMode || consecutiveActionFailures > 0) {
            return Decision(true, "Recovery mode active ($consecutiveActionFailures failures); vision required for re-grounding")
        }

        // 2. Visual Intent: If user explicitly asked for visual understanding
        val goalLower = userGoal?.lowercase(Locale.ROOT) ?: ""
        if (VISUAL_INTENT_KEYWORDS.any { goalLower.contains(it) }) {
            return Decision(true, "User intent explicitly requires visual scene interpretation")
        }

        // 3. Sparse Accessibility Semantics: E.g., custom games, webviews, Flutter apps without a11y
        val interactiveCount = worldState.nodes.count { it.isClickable || it.isEditable }
        if (interactiveCount < 2) {
            return Decision(true, "Accessibility tree is sparse ($interactiveCount interactive elements); vision required")
        }

        // 4. Default: Standard Android GUI with rich semantic nodes -> Omit visual frame
        return Decision(
            shouldCapture = false,
            reason = "High-confidence Accessibility tree available ($interactiveCount interactive elements); visual frame omitted for speed & token efficiency"
        )
    }
}
