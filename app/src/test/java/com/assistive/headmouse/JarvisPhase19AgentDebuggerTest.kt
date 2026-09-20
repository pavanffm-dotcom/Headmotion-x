package com.assistive.headmouse

import com.assistive.headmouse.agent.jarvis.autonomous.DynamicPlanner
import com.assistive.headmouse.agent.jarvis.autonomous.JarvisMissionDebugger
import com.assistive.headmouse.agent.jarvis.autonomous.MissionExecutionTrail
import com.assistive.headmouse.agent.jarvis.autonomous.MissionStepTrace
import com.assistive.headmouse.agent.jarvis.autonomous.MissionTraceRecord
import com.assistive.headmouse.agent.jarvis.autonomous.MissionValidationOrchestrator
import com.assistive.headmouse.agent.jarvis.autonomous.ReplanningEngine
import com.assistive.headmouse.agent.jarvis.autonomous.ToolCapabilityManager
import com.assistive.headmouse.agent.jarvis.autonomous.ToolRegistry
import com.assistive.headmouse.agent.jarvis.autonomous.model.ModelClient
import com.assistive.headmouse.agent.jarvis.autonomous.model.ModelDecisionRequest
import com.assistive.headmouse.agent.jarvis.autonomous.model.ModelDiagnostics
import com.assistive.headmouse.agent.jarvis.autonomous.model.ModelResponse
import com.assistive.headmouse.agent.jarvis.autonomous.model.StructuredModelResult
import com.assistive.headmouse.agent.jarvis.autonomous.state.MissionState
import com.assistive.headmouse.agent.jarvis.autonomous.state.SemanticNode
import com.assistive.headmouse.agent.jarvis.autonomous.state.WorldState
import com.assistive.headmouse.agent.jarvis.autonomous.tools.CanonicalTools
import com.assistive.headmouse.agent.jarvis.autonomous.tools.ToolCall
import com.assistive.headmouse.agent.jarvis.autonomous.verification.VerificationEngine
import com.assistive.headmouse.agent.jarvis.autonomous.verification.VerificationResult
import com.assistive.headmouse.agent.jarvis.autonomous.verification.VerificationState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * PHASE 19 — J.A.R.V.I.S. AGENT DEBUGGER & TRACE VERIFICATION TEST SUITE
 * PROJECT: HeadMotionMouse / J.A.R.V.I.S.
 *
 * OBJECTIVE:
 * Verify that the developer/debugging view makes every autonomous mission traceable
 * across all 18 mandatory fields, redacts secrets, assigns correlation IDs,
 * and allows developers to determine exactly where a failed mission broke without guessing.
 */
class JarvisPhase19AgentDebuggerTest {

    @Before
    fun setup() {
        JarvisMissionDebugger.clearHistory()
    }

    @Test
    fun testAll18MandatoryTraceFieldsPresent() {
        val mission = JarvisMissionDebugger.startMission(
            goal = "Search WhatsApp in Play Store",
            provider = "GEMINI",
            modelName = "gemini-1.5-flash"
        )

        val step = MissionStepTrace(
            stepIndex = 1,
            goal = "Search WhatsApp in Play Store",
            observe = "package=com.android.vending, nodes=12",
            worldStateId = "WS-4a8f1b",
            modelRequest = "Subgoal='Search WhatsApp', SystemChars=200",
            modelProvider = "GEMINI",
            model = "gemini-1.5-flash",
            toolSchema = "[launch_app, tap_element, type_text, scroll, finish_task]",
            modelDecision = "EXECUTE_TOOL (Reason='Focus search bar')",
            toolCall = "tap_element(label=\"Search\")",
            action = "Dispatched gesture TAP at (540, 210)",
            actionResult = "success=true, latency=45ms",
            waitDurationMs = 450L,
            verify = "Search screen active with focused search query box",
            verificationResult = "search_screen=true, stateChanged=true",
            nextObservation = "package=com.android.vending, nodes=16, isKeyboardVisible=true",
            replan = "None",
            finalResult = "STEP_VERIFIED"
        )

        JarvisMissionDebugger.recordStep(mission.missionId, step)
        JarvisMissionDebugger.completeMission(mission.missionId, true)

        val formattedTrace = mission.toFormattedTrace()

        // Verify all 18 fields are present in the trace text
        val requiredFields = listOf(
            "MISSION START",
            "GOAL",
            "OBSERVE",
            "WORLD STATE ID",
            "MODEL REQUEST",
            "MODEL PROVIDER",
            "MODEL",
            "TOOL SCHEMA",
            "MODEL DECISION",
            "TOOL CALL",
            "ACTION",
            "ACTION RESULT",
            "WAIT",
            "VERIFY",
            "VERIFICATION RESULT",
            "NEXT OBSERVATION",
            "REPLAN",
            "FINAL RESULT"
        )

        for (field in requiredFields) {
            assertTrue("Trace missing mandatory field: $field", formattedTrace.contains(field))
        }
    }

    @Test
    fun testCorrelationIdsAndMissionIds() {
        val m1 = JarvisMissionDebugger.startMission("Goal 1")
        val m2 = JarvisMissionDebugger.startMission("Goal 2")

        assertTrue("Mission ID format must start with 'MISSION #'", m1.missionId.startsWith("MISSION #"))
        assertTrue("Mission ID format must start with 'MISSION #'", m2.missionId.startsWith("MISSION #"))
        assertNotEquals("Mission IDs must be unique", m1.missionId, m2.missionId)

        assertTrue("Correlation ID must start with 'corr-'", m1.correlationId.startsWith("corr-"))
        assertTrue("Correlation ID must start with 'corr-'", m2.correlationId.startsWith("corr-"))
        assertNotEquals("Correlation IDs must be unique per mission", m1.correlationId, m2.correlationId)
    }

    @Test
    fun testCredentialAndSensitiveDataRedaction() {
        // Test API key redaction
        val rawPromptWithGeminiKey = "Using AIzaSyD98734hksdjfhsdf98234kjhsdf234 for Gemini call"
        val rawPromptWithOpenAiKey = "Using sk-proj-12345678901234567890abcdef for OpenAI call"
        val rawAuthHeader = "Authorization: Bearer mySecretToken12345678"
        val rawPasswordArg = "type_text(password=SuperSecret123, username=admin)"

        val sanitized1 = JarvisMissionDebugger.sanitize(rawPromptWithGeminiKey)
        val sanitized2 = JarvisMissionDebugger.sanitize(rawPromptWithOpenAiKey)
        val sanitized3 = JarvisMissionDebugger.sanitize(rawAuthHeader)
        val sanitized4 = JarvisMissionDebugger.sanitize(rawPasswordArg)

        assertFalse("Gemini API key must not leak", sanitized1.contains("AIzaSyD98734hksdjfhsdf98234kjhsdf234"))
        assertTrue("Gemini API key should be redacted", sanitized1.contains("[REDACTED_API_KEY]"))

        assertFalse("OpenAI API key must not leak", sanitized2.contains("sk-proj-12345678901234567890abcdef"))
        assertTrue("OpenAI API key should be redacted", sanitized2.contains("[REDACTED_API_KEY]"))

        assertFalse("Auth token must not leak", sanitized3.contains("mySecretToken12345678"))
        assertTrue("Auth token should be redacted", sanitized3.contains("Bearer [REDACTED_TOKEN]"))

        assertFalse("Password must not leak", sanitized4.contains("SuperSecret123"))
        assertTrue("Password should be redacted", sanitized4.contains("password=[REDACTED_SENSITIVE]"))
    }

    @Test
    fun testExactRootCauseFailureDiagnosisWithoutGuessing() {
        // Developer debugging simulation where mission breaks at Step #2
        val mission = JarvisMissionDebugger.injectSimulatedMission(failAtStep2 = true)

        assertEquals("FAILED", mission.status)
        assertEquals(2, mission.failureStepIndex)
        assertNotNull("Failure diagnosis must be explicit", mission.failureDiagnosis)
        assertTrue(
            "Diagnosis should clearly state why it broke",
            mission.failureDiagnosis!!.contains("TargetResolver failed to resolve") ||
                mission.failureDiagnosis!!.contains("App stuck on splash screen")
        )

        val trace = mission.toFormattedTrace()
        assertTrue("Trace must state which step broke", trace.contains("Broken at Step #2"))
        assertTrue("Trace must specify failure root cause", trace.contains("FAILURE ROOT CAUSE"))
    }

    @Test
    fun testSafeJsonAndTextExport() {
        val mission = JarvisMissionDebugger.injectSimulatedMission(failAtStep2 = false)

        val json = mission.exportSafeJson()
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"missionId\":\"${mission.missionId}\""))
        assertTrue(json.contains("\"correlationId\":\"${mission.correlationId}\""))
        assertTrue(json.contains("\"status\":\"SUCCESS\""))
        assertTrue(json.contains("\"steps\":["))

        val text = mission.toFormattedTrace()
        assertTrue(text.contains("MISSION START"))
        assertTrue(text.contains("FINAL RESULT: SUCCESS"))
    }

    @Test
    fun testMissionFilterAndHistoryManagement() {
        JarvisMissionDebugger.injectSimulatedMission(failAtStep2 = false) // Success
        JarvisMissionDebugger.injectSimulatedMission(failAtStep2 = true)  // Failed

        val all = JarvisMissionDebugger.getAllMissions()
        assertEquals(2, all.size)

        val failedOnly = JarvisMissionDebugger.filterMissions(statusFilter = "FAILED")
        assertEquals(1, failedOnly.size)
        assertEquals("FAILED", failedOnly[0].status)

        val successOnly = JarvisMissionDebugger.filterMissions(statusFilter = "SUCCESS")
        assertEquals(1, successOnly.size)
        assertEquals("SUCCESS", successOnly[0].status)

        val searchQuery = JarvisMissionDebugger.filterMissions(searchQuery = "WhatsApp")
        assertEquals(2, searchQuery.size)
    }

    @Test
    fun testMissionValidationOrchestratorLogsDirectlyToDebugger() = runBlocking {
        val toolRegistry = ToolRegistry(ToolCapabilityManager())
        val planner = DynamicPlanner(
            toolRegistry = toolRegistry,
            jarvisBrain = null,
            appSettings = null
        )
        val orchestrator = MissionValidationOrchestrator(planner)

        val mission = MissionState(originalUserGoal = "Search WhatsApp in Play Store")
        val trail = MissionExecutionTrail(missionGoal = "Search WhatsApp in Play Store", level = 1)

        val mockClient = object : ModelClient {
            override suspend fun decideNextAction(
                systemInstruction: String,
                originalUserGoal: String,
                currentSubgoal: String?,
                compressedScreenIndex: String,
                actionHistory: List<String>,
                availableTools: List<com.assistive.headmouse.agent.jarvis.autonomous.tools.ToolDefinition>,
                screenshotBase64: String?
            ): Result<ModelResponse> {
                return Result.success(
                    ModelResponse(
                        thought = "Open Play Store",
                        toolCall = ToolCall(
                            name = CanonicalTools.LAUNCH_APP,
                            arguments = mapOf("package_or_name" to "com.android.vending")
                        )
                    )
                )
            }
        }

        val screen1 = WorldState(
            foregroundPackage = "com.android.launcher",
            nodes = listOf(
                SemanticNode(index = 1, text = "Play Store", isClickable = true)
            )
        )

        val (toolCall, nextScreen) = orchestrator.executeStep(
            missionState = mission,
            modelClient = mockClient,
            currentScreen = screen1,
            screenTransitionProvider = { call, curr ->
                Pair(
                    WorldState(
                        foregroundPackage = "com.android.vending",
                        nodes = listOf(SemanticNode(index = 2, text = "Search", isClickable = true))
                    ),
                    300L
                )
            },
            trail = trail
        )

        // Verify trail contains the step
        assertEquals(1, trail.steps.size)
        val stepRecord = trail.steps[0]
        assertEquals("Search WhatsApp in Play Store", stepRecord.goal)
        assertEquals(CanonicalTools.LAUNCH_APP, toolCall.name)

        // Verify JarvisMissionDebugger received the step
        val latestMission = JarvisMissionDebugger.getLatestMission()
        assertNotNull("MissionDebugger must record step from MissionValidationOrchestrator", latestMission)
        assertEquals("Search WhatsApp in Play Store", latestMission!!.goal)
        assertEquals(1, latestMission.steps.size)

        val stepTrace = latestMission.steps[0]
        assertEquals(1, stepTrace.stepIndex)
        assertEquals(CanonicalTools.LAUNCH_APP, stepTrace.toolCall.substringBefore("("))
        assertTrue(stepTrace.observe.contains("com.android.launcher"))
        assertTrue(stepTrace.nextObservation.contains("com.android.vending"))
    }
}
