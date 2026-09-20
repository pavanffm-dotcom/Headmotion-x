package com.assistive.headmouse.agent.jarvis.autonomous

import android.util.Log
import com.assistive.headmouse.agent.jarvis.autonomous.model.ModelDecisionRequest
import com.assistive.headmouse.agent.jarvis.autonomous.model.StructuredModelResult
import com.assistive.headmouse.agent.jarvis.autonomous.state.WorldState
import com.assistive.headmouse.agent.jarvis.autonomous.tools.ActionResult
import com.assistive.headmouse.agent.jarvis.autonomous.tools.ToolCall
import com.assistive.headmouse.agent.jarvis.autonomous.tools.ToolDefinition
import com.assistive.headmouse.agent.jarvis.autonomous.verification.VerificationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * PHASE 19 — J.A.R.V.I.S. AGENT DEBUGGER & TRACE ENGINE
 *
 * Implements the mandatory 18-element mission trace:
 * MISSION START, GOAL, OBSERVE, WORLD STATE ID, MODEL REQUEST, MODEL PROVIDER,
 * MODEL, TOOL SCHEMA, MODEL DECISION, TOOL CALL, ACTION, ACTION RESULT,
 * WAIT, VERIFY, VERIFICATION RESULT, NEXT OBSERVATION, REPLAN, FINAL RESULT.
 *
 * Safety Invariants:
 * 1. ZERO credential leakage (API keys/tokens sanitized).
 * 2. Sensitive inputs (passwords/PINs) redacted.
 * 3. Exact root-cause failure diagnosis without guessing.
 */
data class MissionStepTrace(
    val stepIndex: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val goal: String,
    val observe: String,
    val worldStateId: String,
    val modelRequest: String,
    val modelProvider: String,
    val model: String,
    val toolSchema: String,
    val modelDecision: String,
    val toolCall: String,
    val action: String,
    val actionResult: String,
    val waitDurationMs: Long,
    val verify: String,
    val verificationResult: String,
    val nextObservation: String,
    val replan: String,
    val finalResult: String,
    val failureReason: String? = null,
    val tokenUsage: Int = 0,
    val latencyMs: Long = 0L
) {
    fun toFormattedTraceString(): String {
        return buildString {
            appendLine("=== STEP #$stepIndex ===")
            appendLine("GOAL: $goal")
            appendLine("OBSERVE: $observe")
            appendLine("WORLD STATE ID: $worldStateId")
            appendLine("MODEL REQUEST: $modelRequest")
            appendLine("MODEL PROVIDER: $modelProvider")
            appendLine("MODEL: $model")
            appendLine("TOOL SCHEMA: $toolSchema")
            appendLine("MODEL DECISION: $modelDecision")
            appendLine("TOOL CALL: $toolCall")
            appendLine("ACTION: $action")
            appendLine("ACTION RESULT: $actionResult")
            appendLine("WAIT: ${waitDurationMs}ms (settle delay)")
            appendLine("VERIFY: $verify")
            appendLine("VERIFICATION RESULT: $verificationResult")
            appendLine("NEXT OBSERVATION: $nextObservation")
            appendLine("REPLAN: $replan")
            appendLine("FINAL RESULT: $finalResult")
            if (!failureReason.isNullOrBlank() && failureReason != "None") {
                appendLine("FAILURE ROOT CAUSE: $failureReason")
            }
            appendLine("TIME: ${latencyMs}ms | TOKENS: $tokenUsage")
            appendLine("----------------------------------------")
        }
    }
}

/**
 * Encapsulates the complete, traceable record of an autonomous mission.
 */
data class MissionTraceRecord(
    val missionId: String,
    val correlationId: String,
    val goal: String,
    val startTimeMs: Long = System.currentTimeMillis(),
    var endTimeMs: Long? = null,
    var status: String = "IN_PROGRESS", // IN_PROGRESS, SUCCESS, FAILED
    val modelProvider: String = "LOCAL_HEURISTIC",
    val modelName: String = "gemini-1.5-flash",
    val steps: MutableList<MissionStepTrace> = mutableListOf(),
    var totalTokens: Int = 0,
    var failureStepIndex: Int? = null,
    var failureDiagnosis: String? = null
) {
    val durationMs: Long
        get() = (endTimeMs ?: System.currentTimeMillis()) - startTimeMs

    fun toFormattedTrace(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            appendLine("#################################################################")
            appendLine("MISSION START: $missionId")
            appendLine("CORRELATION ID: $correlationId")
            appendLine("STARTED AT: ${dateFormat.format(Date(startTimeMs))}")
            appendLine("GOAL: $goal")
            appendLine("MODEL PROVIDER: $modelProvider")
            appendLine("MODEL: $modelName")
            appendLine("STATUS: $status | DURATION: ${durationMs}ms | TOTAL STEPS: ${steps.size}")
            if (status == "FAILED") {
                appendLine("BREAKDOWN: Broken at Step #${failureStepIndex ?: "?"} -> ${failureDiagnosis ?: "Unknown"}")
            }
            appendLine("#################################################################")
            appendLine()
            steps.forEach { step ->
                appendLine(step.toFormattedTraceString())
            }
            appendLine("FINAL RESULT: $status")
            appendLine("=================================================================")
        }
    }

    fun exportSafeJson(): String {
        val safeGoal = JarvisMissionDebugger.sanitize(goal)
        val safeDiag = JarvisMissionDebugger.sanitize(failureDiagnosis ?: "None")
        return buildString {
            append("{")
            append("\"missionId\":\"$missionId\",")
            append("\"correlationId\":\"$correlationId\",")
            append("\"goal\":\"$safeGoal\",")
            append("\"status\":\"$status\",")
            append("\"durationMs\":$durationMs,")
            append("\"modelProvider\":\"$modelProvider\",")
            append("\"model\":\"$modelName\",")
            append("\"totalSteps\":${steps.size},")
            append("\"failureStepIndex\":${failureStepIndex ?: -1},")
            append("\"failureDiagnosis\":\"$safeDiag\",")
            append("\"steps\":[")
            steps.forEachIndexed { idx, s ->
                if (idx > 0) append(",")
                append("{")
                append("\"step\":${s.stepIndex},")
                append("\"worldStateId\":\"${s.worldStateId}\",")
                append("\"decision\":\"${JarvisMissionDebugger.sanitize(s.modelDecision)}\",")
                append("\"toolCall\":\"${JarvisMissionDebugger.sanitize(s.toolCall)}\",")
                append("\"verification\":\"${JarvisMissionDebugger.sanitize(s.verificationResult)}\",")
                append("\"replan\":\"${JarvisMissionDebugger.sanitize(s.replan)}\",")
                append("\"finalResult\":\"${s.finalResult}\"")
                append("}")
            }
            append("]}")
        }
    }
}

/**
 * Singleton debugger hub that records, organizes, and exposes mission traces
 * for instant root-cause analysis and developer verification.
 */
object JarvisMissionDebugger {

    private const val TAG = "JarvisMissionDebugger"
    private const val MAX_HISTORY = 50

    private val missionCounter = AtomicInteger(1)
    private val missionHistory = mutableListOf<MissionTraceRecord>()
    private val _historyFlow = MutableStateFlow<List<MissionTraceRecord>>(emptyList())
    val historyFlow: StateFlow<List<MissionTraceRecord>> = _historyFlow.asStateFlow()

    @Synchronized
    fun startMission(
        goal: String,
        provider: String = "LOCAL",
        modelName: String = "gemini-1.5-flash"
    ): MissionTraceRecord {
        val idNumber = missionCounter.getAndIncrement()
        val missionId = "MISSION #$idNumber"
        val correlationId = "corr-" + UUID.randomUUID().toString().take(8)

        val record = MissionTraceRecord(
            missionId = missionId,
            correlationId = correlationId,
            goal = sanitize(goal),
            modelProvider = provider,
            modelName = modelName
        )

        missionHistory.add(0, record)
        if (missionHistory.size > MAX_HISTORY) {
            missionHistory.removeAt(missionHistory.lastIndex)
        }
        _historyFlow.value = missionHistory.toList()
        Log.i(TAG, "[$missionId] Started: '$goal' (correlation=$correlationId)")
        return record
    }

    @Synchronized
    fun recordStep(
        missionId: String,
        stepTrace: MissionStepTrace
    ) {
        val record = missionHistory.find { it.missionId == missionId } ?: return
        val sanitizedStep = stepTrace.copy(
            goal = sanitize(stepTrace.goal),
            observe = sanitize(stepTrace.observe),
            modelRequest = sanitize(stepTrace.modelRequest),
            toolCall = sanitize(stepTrace.toolCall),
            action = sanitize(stepTrace.action),
            verify = sanitize(stepTrace.verify),
            verificationResult = sanitize(stepTrace.verificationResult),
            replan = sanitize(stepTrace.replan),
            failureReason = sanitize(stepTrace.failureReason ?: "")
        )
        record.steps.add(sanitizedStep)
        record.totalTokens += stepTrace.tokenUsage
        _historyFlow.value = missionHistory.toList()
        Log.d(TAG, "[$missionId] Step #${stepTrace.stepIndex}: ${sanitizedStep.toolCall} -> ${sanitizedStep.finalResult}")
    }

    @Synchronized
    fun completeMission(
        missionId: String,
        success: Boolean,
        failureReason: String? = null,
        failedStepIndex: Int? = null
    ) {
        val record = missionHistory.find { it.missionId == missionId } ?: return
        record.endTimeMs = System.currentTimeMillis()
        record.status = if (success) "SUCCESS" else "FAILED"
        if (!success) {
            record.failureStepIndex = failedStepIndex ?: record.steps.size
            record.failureDiagnosis = failureReason ?: "Unspecified failure"
            Log.w(TAG, "[$missionId] FAILED at Step #${record.failureStepIndex}: ${record.failureDiagnosis}")
        } else {
            Log.i(TAG, "[$missionId] SUCCESS in ${record.durationMs}ms with ${record.steps.size} steps")
        }
        _historyFlow.value = missionHistory.toList()
    }

    @Synchronized
    fun getAllMissions(): List<MissionTraceRecord> {
        return missionHistory.toList()
    }

    @Synchronized
    fun getMission(missionId: String): MissionTraceRecord? {
        return missionHistory.find { it.missionId == missionId }
    }

    @Synchronized
    fun getLatestMission(): MissionTraceRecord? {
        return missionHistory.firstOrNull()
    }

    @Synchronized
    fun clearHistory() {
        missionHistory.clear()
        _historyFlow.value = emptyList()
    }

    @Synchronized
    fun filterMissions(statusFilter: String? = null, searchQuery: String? = null): List<MissionTraceRecord> {
        return missionHistory.filter { mission ->
            val matchesStatus = when (statusFilter?.uppercase()) {
                "SUCCESS" -> mission.status == "SUCCESS"
                "FAILED" -> mission.status == "FAILED"
                "IN_PROGRESS" -> mission.status == "IN_PROGRESS"
                else -> true
            }
            val matchesQuery = if (searchQuery.isNullOrBlank()) true else {
                val q = searchQuery.lowercase()
                mission.missionId.lowercase().contains(q) ||
                    mission.goal.lowercase().contains(q) ||
                    mission.correlationId.lowercase().contains(q) ||
                    mission.steps.any { it.toolCall.lowercase().contains(q) || it.observe.lowercase().contains(q) }
            }
            matchesStatus && matchesQuery
        }
    }

    /**
     * Sanitizes strings to eliminate API keys, sensitive tokens, passwords, and PINs.
     */
    fun sanitize(input: String): String {
        if (input.isBlank()) return input
        var result = input

        // 1. Redact Google API keys (AIzaSy...)
        result = result.replace(Regex("AIzaSy[A-Za-z0-9_-]{20,}"), "[REDACTED_API_KEY]")
        // 2. Redact DeepSeek / OpenAI / Bearer keys (sk-...)
        result = result.replace(Regex("sk-[A-Za-z0-9_-]{20,}"), "[REDACTED_API_KEY]")
        // 3. Redact generic auth bearer tokens
        result = result.replace(Regex("Bearer\\s+[A-Za-z0-9_.-]{16,}", RegexOption.IGNORE_CASE), "Bearer [REDACTED_TOKEN]")
        // 4. Redact password/PIN parameters in arguments
        result = result.replace(Regex("(?i)(password|pin|passcode|secret)\\s*=\\s*[^,}\\]]+"), "$1=[REDACTED_SENSITIVE]")

        return result
    }

    /**
     * Helper to bridge a [MissionStepRecord] from MissionValidationOrchestrator into [MissionStepTrace].
     */
    fun createTraceFromRecord(
        record: MissionStepRecord,
        provider: String = "LOCAL_HEURISTIC",
        model: String = "gemini-1.5-flash",
        availableTools: List<ToolDefinition> = emptyList()
    ): MissionStepTrace {
        val toolSchemaStr = if (availableTools.isNotEmpty()) {
            availableTools.map { it.name }.toString()
        } else {
            "[launch_app, tap_element, type_text, scroll, press_navigation, wait, finish_task]"
        }

        val worldId = "WS-" + Integer.toHexString(record.worldState.screenHash.hashCode()).take(6)
        val decisionStr = record.modelDecision?.let {
            "${it.decision} (Reason='${it.reason}')"
        } ?: "HeuristicFallback (ClosedLoopMatcher)"

        return MissionStepTrace(
            stepIndex = record.stepIndex,
            goal = record.goal,
            observe = "package=${record.worldState.foregroundPackage}, nodes=${record.worldState.nodes.size}, editable=${record.worldState.editableNodes.size}",
            worldStateId = worldId,
            modelRequest = "Subgoal='${record.modelRequest?.currentSubgoal ?: record.goal}', SystemChars=${record.modelRequest?.systemInstruction?.length ?: 0}",
            modelProvider = provider,
            model = model,
            toolSchema = toolSchemaStr,
            modelDecision = decisionStr,
            toolCall = "${record.toolCall.name}(${record.toolCall.arguments})",
            action = record.action,
            actionResult = "success=${record.verification.verified}, latency=${record.timeMs}ms",
            waitDurationMs = record.waitDurationMs,
            verify = "State=${record.verification.state}, target verified=${record.verification.verified}",
            verificationResult = "verified=${record.verification.verified}, stateChanged=${record.verification.stateChanged}, explanation='${record.verification.explanation}'",
            nextObservation = "package=${record.nextObservation.foregroundPackage}, nodes=${record.nextObservation.nodes.size}, hash=${record.nextObservation.screenHash}",
            replan = record.replanning ?: "None (Progressing as planned)",
            finalResult = record.finalResult ?: if (record.verification.verified) "STEP_VERIFIED" else "REPLAN_REQUIRED",
            failureReason = record.failureReason ?: if (!record.verification.verified) record.verification.explanation else "None",
            tokenUsage = record.tokenUsage,
            latencyMs = record.timeMs
        )
    }

    /**
     * Injects a realistic diagnostic mission trace into history.
     * Useful for developer validation and UI testing without needing a physical phone connected.
     */
    fun injectSimulatedMission(failAtStep2: Boolean = false): MissionTraceRecord {
        val mission = startMission(
            goal = "Search WhatsApp in Play Store",
            provider = "GEMINI",
            modelName = "gemini-1.5-flash"
        )

        // Step 1: Open Play Store
        val step1 = MissionStepTrace(
            stepIndex = 1,
            goal = "Open Google Play Store",
            observe = "package=com.android.launcher, activity=.LauncherActivity, nodes=14, editable=0",
            worldStateId = "WS-3a9f11",
            modelRequest = "Subgoal='Open Play Store', SystemPromptChars=380, ScreenIndex='[Launcher, Play Store, Settings]'",
            modelProvider = "GEMINI",
            model = "gemini-1.5-flash",
            toolSchema = "[launch_app, tap_element, type_text, scroll, press_navigation, finish_task]",
            modelDecision = "EXECUTE_TOOL (Reason='Target application is Google Play Store; initiating launch')",
            toolCall = "launch_app(package_or_name=\"com.android.vending\")",
            action = "Dispatched Context.startActivity(Intent for com.android.vending)",
            actionResult = "success=true, latency=210ms",
            waitDurationMs = 850L,
            verify = "Foreground package shifted to com.android.vending with active search bar",
            verificationResult = "VERIFIED (stateChanged=true, explanation='Google Play Store foregrounded and settled')",
            nextObservation = "package=com.android.vending, activity=.AssetBrowserActivity, nodes=28, editable=1",
            replan = "None (On track)",
            finalResult = "STEP_VERIFIED",
            latencyMs = 890L,
            tokenUsage = 340
        )
        recordStep(mission.missionId, step1)

        if (!failAtStep2) {
            // Step 2: Search WhatsApp
            val step2 = MissionStepTrace(
                stepIndex = 2,
                goal = "Search WhatsApp in Play Store",
                observe = "package=com.android.vending, activity=.AssetBrowserActivity, nodes=28, editable=1",
                worldStateId = "WS-7b4c29",
                modelRequest = "Subgoal='Type WhatsApp query', SystemPromptChars=410, ScreenIndex='[Search Google Play]'",
                modelProvider = "GEMINI",
                model = "gemini-1.5-flash",
                toolSchema = "[launch_app, tap_element, type_text, scroll, press_navigation, finish_task]",
                modelDecision = "EXECUTE_TOOL (Reason='Focus search field and enter WhatsApp query')",
                toolCall = "type_text(text=\"WhatsApp\", press_enter=true)",
                action = "Dispatched keyboard text injection 'WhatsApp' + KeyEvent.KEYCODE_ENTER",
                actionResult = "success=true, latency=140ms",
                waitDurationMs = 600L,
                verify = "Search results feed loaded showing WhatsApp Messenger package card",
                verificationResult = "VERIFIED (stateChanged=true, explanation='WhatsApp search results displayed with Install button')",
                nextObservation = "package=com.android.vending, nodes=32, target='WhatsApp Messenger (Install)'",
                replan = "None (On track)",
                finalResult = "STEP_VERIFIED",
                latencyMs = 750L,
                tokenUsage = 280
            )
            recordStep(mission.missionId, step2)
            completeMission(mission.missionId, success = true)
        } else {
            // Step 2: Broken / Failed Step
            val step2Failed = MissionStepTrace(
                stepIndex = 2,
                goal = "Search WhatsApp in Play Store",
                observe = "package=com.android.vending, activity=.AssetBrowserActivity, nodes=5, editable=0",
                worldStateId = "WS-000000",
                modelRequest = "Subgoal='Type WhatsApp query', SystemPromptChars=410, ScreenIndex='[]'",
                modelProvider = "GEMINI",
                model = "gemini-1.5-flash",
                toolSchema = "[launch_app, tap_element, type_text, scroll, press_navigation, finish_task]",
                modelDecision = "EXECUTE_TOOL (Reason='Attempting to tap search field')",
                toolCall = "tap_element(label=\"Search Google Play\")",
                action = "Dispatched gesture TAP at (540, 210)",
                actionResult = "success=false, errorCode=TARGET_NOT_FOUND",
                waitDurationMs = 500L,
                verify = "Search field focused and keyboard up",
                verificationResult = "FAILED (stateChanged=false, explanation='Target element \"Search Google Play\" not found in accessibility hierarchy. App stuck on splash screen.')",
                nextObservation = "package=com.android.vending, nodes=5, screenHash=UNCHANGED",
                replan = "ReplanningEngine: Injected WAIT(2000ms) for splash screen settling. Retried 3 times without mutation.",
                finalResult = "FAILED_AT_STEP",
                failureReason = "App stuck on splash screen; TargetResolver failed to resolve 'Search Google Play' after 3 recovery attempts.",
                latencyMs = 2100L,
                tokenUsage = 250
            )
            recordStep(mission.missionId, step2Failed)
            completeMission(
                missionId = mission.missionId,
                success = false,
                failureReason = "App stuck on splash screen; TargetResolver failed to resolve 'Search Google Play' after 3 recovery attempts.",
                failedStepIndex = 2
            )
        }
        return mission
    }
}
