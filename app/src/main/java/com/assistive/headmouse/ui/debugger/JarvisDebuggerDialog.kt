package com.assistive.headmouse.ui.debugger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.assistive.headmouse.R
import com.assistive.headmouse.agent.jarvis.autonomous.JarvisMissionDebugger
import com.assistive.headmouse.agent.jarvis.autonomous.MissionTraceRecord
import com.google.android.material.button.MaterialButton

/**
 * PHASE 19 — J.A.R.V.I.S. AGENT DEBUGGER DIALOG
 *
 * Developer & diagnostic view that renders complete, traceable autonomous mission logs.
 * Enables developers to determine exactly where a mission broke without guessing.
 */
class JarvisDebuggerDialog(private val context: Context) {

    private var activeFilter: String = "ALL" // ALL, FAILED, SUCCESS

    fun show() {
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_jarvis_debugger, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btn_close_debugger)
        val btnFilterAll = dialogView.findViewById<MaterialButton>(R.id.btn_filter_all)
        val btnFilterFailed = dialogView.findViewById<MaterialButton>(R.id.btn_filter_failed)
        val btnFilterSuccess = dialogView.findViewById<MaterialButton>(R.id.btn_filter_success)
        val btnSimulate = dialogView.findViewById<MaterialButton>(R.id.btn_simulate_mission)
        val btnClear = dialogView.findViewById<MaterialButton>(R.id.btn_clear_debugger)
        val tvStatsCount = dialogView.findViewById<TextView>(R.id.tv_stats_count)
        val tvStatsRate = dialogView.findViewById<TextView>(R.id.tv_stats_rate)
        val container = dialogView.findViewById<LinearLayout>(R.id.ll_missions_container)
        val tvEmptyState = dialogView.findViewById<TextView>(R.id.tv_empty_state)

        val colorActive = ContextCompat.getColor(context, R.color.secondary)
        val colorInactive = ContextCompat.getColor(context, R.color.surface_dark)
        val textDark = ContextCompat.getColor(context, R.color.card_dark)
        val textLight = ContextCompat.getColor(context, R.color.text_primary)

        fun refreshMissions() {
            container.removeAllViews()
            val allMissions = JarvisMissionDebugger.getAllMissions()
            val filteredMissions = when (activeFilter) {
                "FAILED" -> allMissions.filter { it.status == "FAILED" }
                "SUCCESS" -> allMissions.filter { it.status == "SUCCESS" }
                else -> allMissions
            }

            val total = allMissions.size
            val passed = allMissions.count { it.status == "SUCCESS" }
            val failed = allMissions.count { it.status == "FAILED" }
            tvStatsCount.text = "Missions: $total ($passed passed, $failed failed)"
            if (failed > 0) {
                tvStatsRate.text = "⚠️ $failed Breakpoints Detected"
                tvStatsRate.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
            } else {
                tvStatsRate.text = "✓ 100% Validated"
                tvStatsRate.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
            }

            if (filteredMissions.isEmpty()) {
                container.addView(tvEmptyState)
                tvEmptyState.visibility = View.VISIBLE
                tvEmptyState.text = when (activeFilter) {
                    "FAILED" -> "No failed missions recorded. Agent self-healing is healthy!"
                    "SUCCESS" -> "No successful missions recorded yet."
                    else -> "No autonomous missions recorded yet.\nRun an autonomous mission or click '+ SIMULATION'\nto inspect the 18-field diagnostic trace."
                }
            } else {
                tvEmptyState.visibility = View.GONE
                filteredMissions.forEach { mission ->
                    val cardView = inflater.inflate(R.layout.item_mission_trace_card, container, false)
                    bindMissionCard(cardView, mission)
                    container.addView(cardView)
                }
            }
        }

        fun updateFilterUI(filter: String) {
            activeFilter = filter
            btnFilterAll.setBackgroundColor(if (filter == "ALL") colorActive else colorInactive)
            btnFilterAll.setTextColor(if (filter == "ALL") textDark else textLight)

            btnFilterFailed.setBackgroundColor(if (filter == "FAILED") colorActive else colorInactive)
            btnFilterFailed.setTextColor(if (filter == "FAILED") textDark else textLight)

            btnFilterSuccess.setBackgroundColor(if (filter == "SUCCESS") colorActive else colorInactive)
            btnFilterSuccess.setTextColor(if (filter == "SUCCESS") textDark else textLight)

            refreshMissions()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnFilterAll.setOnClickListener { updateFilterUI("ALL") }
        btnFilterFailed.setOnClickListener { updateFilterUI("FAILED") }
        btnFilterSuccess.setOnClickListener { updateFilterUI("SUCCESS") }

        btnSimulate.setOnClickListener {
            // Alternate between successful and failure traces for realistic diagnostic testing
            val toggle = (JarvisMissionDebugger.getAllMissions().size % 2 == 1)
            JarvisMissionDebugger.injectSimulatedMission(failAtStep2 = toggle)
            Toast.makeText(context, if (toggle) "Simulated FAILED mission injected" else "Simulated SUCCESS mission injected", Toast.LENGTH_SHORT).show()
            refreshMissions()
        }

        btnClear.setOnClickListener {
            JarvisMissionDebugger.clearHistory()
            Toast.makeText(context, "Mission trace history cleared", Toast.LENGTH_SHORT).show()
            refreshMissions()
        }

        updateFilterUI("ALL")
        dialog.show()
    }

    private fun bindMissionCard(cardView: View, mission: MissionTraceRecord) {
        val tvMissionId = cardView.findViewById<TextView>(R.id.tv_card_mission_id)
        val tvCorrId = cardView.findViewById<TextView>(R.id.tv_card_correlation_id)
        val tvStatusBadge = cardView.findViewById<TextView>(R.id.tv_card_status_badge)
        val tvGoal = cardView.findViewById<TextView>(R.id.tv_card_goal)
        val tvModelInfo = cardView.findViewById<TextView>(R.id.tv_card_model_info)
        val tvMetrics = cardView.findViewById<TextView>(R.id.tv_card_metrics)
        val llFailureAlert = cardView.findViewById<LinearLayout>(R.id.ll_failure_alert)
        val tvFailureTitle = cardView.findViewById<TextView>(R.id.tv_failure_banner_title)
        val tvFailureDesc = cardView.findViewById<TextView>(R.id.tv_failure_banner_desc)
        val btnToggleSteps = cardView.findViewById<MaterialButton>(R.id.btn_toggle_steps)
        val btnCopy = cardView.findViewById<MaterialButton>(R.id.btn_copy_trace)
        val btnShare = cardView.findViewById<MaterialButton>(R.id.btn_share_trace)
        val llDetail = cardView.findViewById<LinearLayout>(R.id.ll_trace_detail_container)
        val tvTraceText = cardView.findViewById<TextView>(R.id.tv_formatted_trace_text)

        tvMissionId.text = mission.missionId
        tvCorrId.text = mission.correlationId
        tvGoal.text = "Goal: ${mission.goal}"
        tvModelInfo.text = "Provider: ${mission.modelProvider} • ${mission.modelName}"
        tvMetrics.text = "${mission.steps.size} steps • ${mission.durationMs}ms • ${mission.totalTokens} tokens"

        when (mission.status) {
            "SUCCESS" -> {
                tvStatusBadge.text = "SUCCESS"
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
                llFailureAlert.visibility = View.GONE
            }
            "FAILED" -> {
                tvStatusBadge.text = "FAILED"
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
                llFailureAlert.visibility = View.VISIBLE
                tvFailureTitle.text = "🔴 BROKEN AT STEP #${mission.failureStepIndex ?: "?"}"
                tvFailureDesc.text = "Root Cause: ${mission.failureDiagnosis ?: "Verification mismatch or loop detected"}"
            }
            else -> {
                tvStatusBadge.text = "IN PROGRESS"
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_yellow))
                llFailureAlert.visibility = View.GONE
            }
        }

        tvTraceText.text = mission.toFormattedTrace()

        btnToggleSteps.setOnClickListener {
            if (llDetail.visibility == View.VISIBLE) {
                llDetail.visibility = View.GONE
                btnToggleSteps.text = "EXPAND TRACE (18 FIELDS)"
            } else {
                llDetail.visibility = View.VISIBLE
                btnToggleSteps.text = "COLLAPSE TRACE"
            }
        }

        btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Mission Trace", mission.toFormattedTrace())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Mission trace copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "J.A.R.V.I.S. Mission Trace ${mission.missionId}")
                putExtra(Intent.EXTRA_TEXT, mission.toFormattedTrace())
            }
            context.startActivity(Intent.createChooser(intent, "Share Mission Trace"))
        }
    }
}
