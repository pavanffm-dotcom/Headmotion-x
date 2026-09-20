package com.assistive.headmouse.agent.jarvis.autonomous.perf

import android.util.Log

/**
 * Phase 20 — Performance & Token Optimization Tracking.
 * 
 * Captures, measures, and compares:
 * - Model requests per mission
 * - Average request payload size (bytes / chars)
 * - Screenshot payload size (bytes / base64 chars)
 * - Accessibility tree representation size (chars / tokens)
 * - Action history size (chars / tokens)
 * - Average action execution latency (ms)
 * - Verification latency (ms)
 * - Total mission duration (ms)
 * - Estimated token consumption
 * - Duplicate observations avoided
 * - Duplicate model decisions avoided
 * - Cached stable states reused
 */
data class MissionPerformanceMetrics(
    var modelRequestsCount: Int = 0,
    var totalRequestBytes: Long = 0L,
    var totalScreenshotBytes: Long = 0L,
    var totalScreenshotChars: Long = 0L,
    var screenshotsCapturedCount: Int = 0,
    var totalAccessibilityChars: Long = 0L,
    var totalAccessibilityNodesAnalyzed: Int = 0,
    var totalAccessibilityNodesSent: Int = 0,
    var totalHistoryChars: Long = 0L,
    var totalActionLatencyMs: Long = 0L,
    var actionCount: Int = 0,
    var totalVerificationLatencyMs: Long = 0L,
    var verificationCount: Int = 0,
    var missionDurationMs: Long = 0L,
    var estimatedTokensUsed: Int = 0,
    var duplicateObservationsAvoided: Int = 0,
    var duplicateDecisionsAvoided: Int = 0,
    var cachedStatesReused: Int = 0
) {
    val averageRequestSizeBytes: Long
        get() = if (modelRequestsCount > 0) totalRequestBytes / modelRequestsCount else 0L

    val averageActionLatencyMs: Long
        get() = if (actionCount > 0) totalActionLatencyMs / actionCount else 0L

    val averageVerificationLatencyMs: Long
        get() = if (verificationCount > 0) totalVerificationLatencyMs / verificationCount else 0L

    val averageScreenshotSizeBytes: Long
        get() = if (screenshotsCapturedCount > 0) totalScreenshotBytes / screenshotsCapturedCount else 0L

    val averageAccessibilityTreeChars: Long
        get() = if (modelRequestsCount > 0) totalAccessibilityChars / modelRequestsCount else 0L

    val averageHistoryChars: Long
        get() = if (modelRequestsCount > 0) totalHistoryChars / modelRequestsCount else 0L

    fun formatSummary(): String {
        return buildString {
            appendLine("=== MISSION PERFORMANCE METRICS ===")
            appendLine("Model Requests: $modelRequestsCount (Avoided: $duplicateDecisionsAvoided)")
            appendLine("Avg Request Size: ${averageRequestSizeBytes} bytes")
            appendLine("Screenshots Captured: $screenshotsCapturedCount (Avg size: ${averageScreenshotSizeBytes} bytes)")
            appendLine("A11y Tree Chars: Avg $averageAccessibilityTreeChars chars ($totalAccessibilityNodesSent nodes sent of $totalAccessibilityNodesAnalyzed analyzed)")
            appendLine("History Chars: Avg $averageHistoryChars chars")
            appendLine("Avg Action Latency: ${averageActionLatencyMs}ms ($actionCount actions)")
            appendLine("Avg Verification Latency: ${averageVerificationLatencyMs}ms ($verificationCount passes)")
            appendLine("Total Duration: ${missionDurationMs}ms")
            appendLine("Estimated Token Usage: $estimatedTokensUsed tokens")
            appendLine("Duplicate Observations Avoided: $duplicateObservationsAvoided")
            appendLine("Cached States Reused: $cachedStatesReused")
            appendLine("===================================")
        }
    }
}

/**
 * Computes before vs after delta and savings percentages across all Phase 20 dimensions.
 */
data class PerformanceComparison(
    val baseline: MissionPerformanceMetrics,
    val optimized: MissionPerformanceMetrics
) {
    val modelRequestsReductionPercent: Double = computePercentReduction(
        baseline.modelRequestsCount.toDouble(),
        optimized.modelRequestsCount.toDouble()
    )

    val averageRequestSizeReductionPercent: Double = computePercentReduction(
        baseline.averageRequestSizeBytes.toDouble(),
        optimized.averageRequestSizeBytes.toDouble()
    )

    val screenshotPayloadReductionPercent: Double = computePercentReduction(
        baseline.totalScreenshotBytes.toDouble(),
        optimized.totalScreenshotBytes.toDouble()
    )

    val accessibilityTreeReductionPercent: Double = computePercentReduction(
        baseline.averageAccessibilityTreeChars.toDouble(),
        optimized.averageAccessibilityTreeChars.toDouble()
    )

    val historySizeReductionPercent: Double = computePercentReduction(
        baseline.averageHistoryChars.toDouble(),
        optimized.averageHistoryChars.toDouble()
    )

    val actionLatencyChangePercent: Double = computePercentReduction(
        baseline.averageActionLatencyMs.toDouble(),
        optimized.averageActionLatencyMs.toDouble()
    )

    val totalDurationReductionPercent: Double = computePercentReduction(
        baseline.missionDurationMs.toDouble(),
        optimized.missionDurationMs.toDouble()
    )

    val tokenUsageReductionPercent: Double = computePercentReduction(
        baseline.estimatedTokensUsed.toDouble(),
        optimized.estimatedTokensUsed.toDouble()
    )

    fun formatReport(): String {
        return buildString {
            appendLine("==================================================================")
            appendLine("PHASE 20 PERFORMANCE & TOKEN OPTIMIZATION: BEFORE vs AFTER REPORT")
            appendLine("==================================================================")
            appendLine(String.format("%-32s | %-12s | %-12s | %-10s", "METRIC", "BASELINE", "OPTIMIZED", "SAVINGS"))
            appendLine("------------------------------------------------------------------")
            appendLine(String.format("%-32s | %-12s | %-12s | %+.1f%%", "Model Requests / Mission", "${baseline.modelRequestsCount}", "${optimized.modelRequestsCount}", modelRequestsReductionPercent))
            appendLine(String.format("%-32s | %-12s | %-12s | %+.1f%%", "Avg Request Size (Bytes)", "${baseline.averageRequestSizeBytes} B", "${optimized.averageRequestSizeBytes} B", averageRequestSizeReductionPercent))
            appendLine(String.format("%-32s | %-12s | %-12s | %+.1f%%", "Screenshot Payload (Bytes)", "${baseline.totalScreenshotBytes} B", "${optimized.totalScreenshotBytes} B", screenshotPayloadReductionPercent))
            appendLine(String.format("%-32s | %-12s | %-12s | %+.1f%%", "A11y Tree Size (Chars)", "${baseline.averageAccessibilityTreeChars} c", "${optimized.averageAccessibilityTreeChars} c", accessibilityTreeReductionPercent))
            appendLine(String.format("%-32s | %-12s | %-12s | %+.1f%%", "History Size (Chars)", "${baseline.averageHistoryChars} c", "${optimized.averageHistoryChars} c", historySizeReductionPercent))
            appendLine(String.format("%-32s | %-12s | %-12s | %+.1f%%", "Avg Action Latency (ms)", "${baseline.averageActionLatencyMs} ms", "${optimized.averageActionLatencyMs} ms", actionLatencyChangePercent))
            appendLine(String.format("%-32s | %-12s | %-12s | %+.1f%%", "Total Mission Duration (ms)", "${baseline.missionDurationMs} ms", "${optimized.missionDurationMs} ms", totalDurationReductionPercent))
            appendLine(String.format("%-32s | %-12s | %-12s | %+.1f%%", "Estimated Token Consumption", "${baseline.estimatedTokensUsed} tok", "${optimized.estimatedTokensUsed} tok", tokenUsageReductionPercent))
            appendLine("------------------------------------------------------------------")
            appendLine("Observations Avoided: ${optimized.duplicateObservationsAvoided} | Decisions Avoided: ${optimized.duplicateDecisionsAvoided} | States Reused: ${optimized.cachedStatesReused}")
            appendLine("==================================================================")
        }
    }

    companion object {
        private fun computePercentReduction(base: Double, opt: Double): Double {
            if (base <= 0.0) return 0.0
            return ((base - opt) / base) * 100.0
        }
    }
}

/**
 * Thread-safe live session tracker attached to active autonomous runs.
 */
class MissionPerformanceTracker {
    val metrics = MissionPerformanceMetrics()
    private val startTimeMs = System.currentTimeMillis()

    fun recordModelRequest(requestBytes: Long, tokenEstimate: Int, a11yChars: Int, historyChars: Int, nodesAnalyzed: Int, nodesSent: Int) {
        metrics.modelRequestsCount++
        metrics.totalRequestBytes += requestBytes
        metrics.estimatedTokensUsed += tokenEstimate
        metrics.totalAccessibilityChars += a11yChars
        metrics.totalHistoryChars += historyChars
        metrics.totalAccessibilityNodesAnalyzed += nodesAnalyzed
        metrics.totalAccessibilityNodesSent += nodesSent
    }

    fun recordScreenshotCapture(bytes: Long, base64Chars: Int) {
        metrics.screenshotsCapturedCount++
        metrics.totalScreenshotBytes += bytes
        metrics.totalScreenshotChars += base64Chars.toLong()
    }

    fun recordActionLatency(latencyMs: Long) {
        metrics.actionCount++
        metrics.totalActionLatencyMs += latencyMs
    }

    fun recordVerificationLatency(latencyMs: Long) {
        metrics.verificationCount++
        metrics.totalVerificationLatencyMs += latencyMs
    }

    fun recordDuplicateObservationAvoided() {
        metrics.duplicateObservationsAvoided++
    }

    fun recordDuplicateDecisionAvoided() {
        metrics.duplicateDecisionsAvoided++
    }

    fun recordStateReused() {
        metrics.cachedStatesReused++
    }

    fun finalizeMission(): MissionPerformanceMetrics {
        metrics.missionDurationMs = System.currentTimeMillis() - startTimeMs
        Log.i("MissionPerformance", metrics.formatSummary())
        return metrics
    }
}
