package com.assistive.headmouse.agent.jarvis.autonomous

import com.assistive.headmouse.agent.jarvis.autonomous.model.ModelDecisionRequest

/**
 * Context Optimizer & Compressor (R27).
 * Filters out stale, verbose history and packages only high-signal screen state,
 * active objective, navigation breadcrumbs, and recent verified results.
 */
object ContextCompressor {

    fun buildOptimizedPrompt(
        userGoal: String,
        currentObjective: MissionObjective?,
        screenContext: ScreenContextSummary,
        navigationTracker: NavigationTracker,
        missionMemory: MissionMemory,
        toolRegistry: ToolRegistry
    ): String {
        val timeStr = InformationTools.getCurrentTime()
        val breadcrumbs = navigationTracker.getBreadcrumbTrail()
        val memorySummary = missionMemory.getPromptSummary(maxPastActions = 3)
        val toolsSummary = toolRegistry.getPromptToolSummary()

        return """
You are J.A.R.V.I.S., an autonomous Android GUI agent with direct device accessibility control.
Speak politely in a crisp British cadence. Address the user as 'Sir'. Keep responses concise.

=== ACTIVE MISSION ===
Overall Goal: "$userGoal"
Current Objective: "${currentObjective?.title ?: userGoal}"
Navigation Trail: $breadcrumbs
Device Time: $timeStr

=== LIVE SCREEN STATE ===
$screenContext.formattedSummary

=== MISSION MEMORY ===
$memorySummary

=== AVAILABLE CAPABILITIES ===
$toolsSummary

CRITICAL RULES:
1. Ground actions strictly in the live screen state.
2. If an action is required, output the structured action tag or tool call e.g. [ACTION:TAP:#1] or [ACTION:OPEN_APP:pkg].
3. NEVER return 'null'. If an action is not required or information is requested, respond conversationally.
""".trimIndent()
    }

    /**
     * Builds a token-bounded, high-signal decision prompt for the closed-loop autonomous agent.
     * Prioritizes:
     * 1. Immutable original goal & active subgoal
     * 2. Live screen compressed semantic index (< 300 tokens)
     * 3. Last action & verified postcondition result
     * 4. Bounded recent action history (max 4 steps, no raw past screen dumps)
     * 
     * Guarantees total prompt remains under [maxTokenBudget] (default 1,000 tokens).
     */
    fun buildCompressedDecisionPrompt(
        originalGoal: String,
        currentSubgoal: String?,
        compressedScreenIndex: String,
        actionHistory: List<String>,
        maxHistorySteps: Int = 3,
        maxTokenBudget: Int = 800,
        historicalMemoryContext: String? = null
    ): String {
        val sanitizedGoal = originalGoal.trim()
        val sanitizedSubgoal = currentSubgoal?.trim()?.takeIf { it.isNotBlank() && !it.equals(sanitizedGoal, ignoreCase = true) }

        // Phase 20 Target 5: Bounded recent history with older step summarization
        val totalHistorySize = actionHistory.size
        val filteredHistory = actionHistory.takeLast(maxHistorySteps).map { line ->
            if (line.length > 120) line.take(117) + "..." else line
        }
        val omittedHistoryCount = totalHistorySize - filteredHistory.size

        // Phase 20 Target 4: Bounded screen index
        val boundedScreen = if (compressedScreenIndex.length > 1800) {
            val lines = compressedScreenIndex.lines()
            if (lines.size > 20) {
                lines.take(20).joinToString("\n") + "\n... [${lines.size - 20} more nodes omitted for token budget]"
            } else {
                compressedScreenIndex.take(1750) + "\n... [truncated]"
            }
        } else {
            compressedScreenIndex
        }

        val prompt = buildString {
            append("ORIGINAL GOAL: \"").append(sanitizedGoal).append("\"\n")
            if (sanitizedSubgoal != null) {
                append("CURRENT SUBGOAL: \"").append(sanitizedSubgoal).append("\"\n")
            }
            if (!historicalMemoryContext.isNullOrBlank()) {
                val boundedMemory = if (historicalMemoryContext.length > 300) {
                    historicalMemoryContext.take(297) + "..."
                } else {
                    historicalMemoryContext
                }
                append("\nRELEVANT MEMORY & LEARNED PATTERNS:\n").append(boundedMemory.trim()).append("\n")
            }
            if (filteredHistory.isNotEmpty()) {
                append("\nRECENT ACTION HISTORY:\n")
                if (omittedHistoryCount > 0) {
                    append("- [+$omittedHistoryCount earlier completed steps]\n")
                }
                filteredHistory.forEach { append("- ").append(it).append("\n") }
            }
            append("\nCURRENT SCREEN OBSERVATION:\n")
            append(boundedScreen).append("\n\n")
            append("Based on the live display, decide the EXACT NEXT SINGLE ATOMIC ACTION to make progress toward the goal. Call the appropriate tool.")
        }

        // Token budget enforcement (roughly 4 chars per token)
        val estimatedTokens = estimateTokenCount(prompt)
        if (estimatedTokens > maxTokenBudget) {
            val maxChars = maxTokenBudget * 4
            return prompt.take(maxChars - 50) + "\n... [Context truncated to fit token budget]"
        }

        return prompt
    }

    /**
     * Heuristic token estimation (~4 characters per token in English).
     */
    fun estimateTokenCount(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length + 3) / 4
    }

    /**
     * Estimates tokens for a structured [ModelDecisionRequest].
     */
    fun estimateTokenCount(request: ModelDecisionRequest): Int {
        val chars = (request.systemInstruction?.length ?: 0) +
                (request.originalUserGoal?.length ?: 0) +
                (request.currentSubgoal?.length ?: 0) +
                (request.compressedScreenIndex?.length ?: 0) +
                (request.actionHistory?.sumOf { it.length } ?: 0) +
                (request.memoryContext?.length ?: 0)
        return (chars + 3) / 4
    }
}

