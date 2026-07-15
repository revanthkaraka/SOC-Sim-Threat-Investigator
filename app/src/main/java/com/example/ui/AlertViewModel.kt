package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiService
import com.example.data.AlertEntity
import com.example.data.AlertRepository
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertViewModel(
    application: Application,
    private val repository: AlertRepository
) : AndroidViewModel(application) {

    // List of alerts observed from Room database
    val alerts: StateFlow<List<AlertEntity>> = repository.allAlerts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Gamification Score and Time-to-Triage metrics
    private val _gameScore = MutableStateFlow(250) // Starts at base 250 points
    val gameScore: StateFlow<Int> = _gameScore.asStateFlow()

    private val _averageTriageTime = MutableStateFlow(0) // in seconds
    val averageTriageTime: StateFlow<Int> = _averageTriageTime.asStateFlow()

    private val triageStartTimes = mutableMapOf<Int, Long>() // Map of alertId to opening timestamp
    private var totalTriagedCount = 0
    private var totalTriageDuration = 0L

    // Currently selected alert ID
    private val _selectedAlertId = MutableStateFlow<Int?>(null)
    val selectedAlertId: StateFlow<Int?> = _selectedAlertId.asStateFlow()

    // Loading state for AI analysis
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // Status message for operations
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            // Check if database is empty and pre-populate if needed
            repository.prepopulateIfEmpty()
            
            // Set first alert as selected by default once alerts are loaded
            alerts.collect { list ->
                if (list.isNotEmpty() && _selectedAlertId.value == null) {
                    _selectedAlertId.value = list.first().id
                }
            }
        }
    }

    fun selectAlert(id: Int) {
        _selectedAlertId.value = id
        // Reset/Record triage start time for this alert to calculate Time-to-Triage
        if (!triageStartTimes.containsKey(id)) {
            triageStartTimes[id] = System.currentTimeMillis()
        }
    }

    fun updateAlertStatus(alert: AlertEntity, newStatus: String) {
        viewModelScope.launch {
            val isClosing = newStatus.equals("Resolved", ignoreCase = true) && !alert.status.equals("Resolved", ignoreCase = true)
            var scoreDiff = 0
            var reason = ""

            if (isClosing) {
                // Gamification Penalty: Resolving critical/high without running AI Diagnostics first
                if (alert.aiExplanation.isNullOrBlank() && (alert.severity.uppercase() == "CRITICAL" || alert.severity.uppercase() == "HIGH")) {
                    scoreDiff = -100
                    reason = "CLOSED WITHOUT AI DIAGNOSTICS PENALTY (-100 pts)"
                } else {
                    scoreDiff = 80
                    reason = "Incident documented & resolved successfully (+80 pts)"
                }

                // Track triage duration
                val startTime = triageStartTimes[alert.id] ?: System.currentTimeMillis()
                val durationSec = (System.currentTimeMillis() - startTime) / 1000
                totalTriagedCount++
                totalTriageDuration += durationSec
                _averageTriageTime.value = (totalTriageDuration / totalTriagedCount).toInt()
            }

            val updated = alert.copy(status = newStatus)
            repository.updateAlert(updated)
            
            _gameScore.value = (_gameScore.value + scoreDiff).coerceAtLeast(0)
            _statusMessage.value = if (reason.isNotEmpty()) {
                "Status: $newStatus • $reason"
            } else {
                "Status updated to $newStatus"
            }
        }
    }

    fun updateAlertClassification(alert: AlertEntity, newClassification: String) {
        viewModelScope.launch {
            var scoreDiff = 50
            var feedback = "Valid triage classification (+50 pts)"

            // If classifying a complex incident as True Positive correctly (all our defaults are true threats)
            if (newClassification == "True Positive") {
                scoreDiff = 100
                feedback = "Correctly flagged active cyber threat! (+100 pts)"
            } else if (newClassification == "False Positive") {
                scoreDiff = -40
                feedback = "Flagged real anomaly as false positive (-40 pts)"
            }

            val updated = alert.copy(classification = newClassification)
            repository.updateAlert(updated)
            
            _gameScore.value = (_gameScore.value + scoreDiff).coerceAtLeast(0)
            _statusMessage.value = feedback
        }
    }

    fun updateAlertNotes(alert: AlertEntity, notes: String) {
        viewModelScope.launch {
            val isFirstNotes = alert.userNotes.isNullOrBlank() && notes.isNotBlank()
            val updated = alert.copy(userNotes = notes)
            repository.updateAlert(updated)
            
            if (isFirstNotes) {
                _gameScore.value += 30
                _statusMessage.value = "Custom investigator findings logged! (+30 pts)"
            }
        }
    }

    fun runAiDiagnostics(alert: AlertEntity) {
        if (_isAnalyzing.value) return
        _isAnalyzing.value = true
        _statusMessage.value = "Consulting Gemini Threat Copilot..."
        
        viewModelScope.launch {
            try {
                val analysis = GeminiService.analyzeSecurityLog(alert.title, alert.rawLog)
                
                // Construct a nice structured markdown string for the playbook
                val playbookStr = """
                    ### 🛡️ Containment Plan
                    ${analysis.containment}
                    
                    ### 🧹 Eradication Steps
                    ${analysis.eradication}
                    
                    ### 🔄 Recovery & Hardening
                    ${analysis.recovery}
                """.trimIndent()

                val updatedAlert = alert.copy(
                    aiExplanation = analysis.explanation,
                    aiPlaybook = playbookStr,
                    mitreTactic = analysis.mitreTactic,
                    mitreTechnique = analysis.mitreTechnique,
                    remediationScript = analysis.remediationScript,
                    blastRadiusPath = analysis.blastRadiusPath
                )
                
                repository.updateAlert(updatedAlert)
                _gameScore.value += 60 // Reward for running AI analytics
                _statusMessage.value = "Threat analysis completed successfully! (+60 pts)"
            } catch (e: Exception) {
                _statusMessage.value = "Analysis failed: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun generateIncidentReport(alert: AlertEntity): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val generatedTime = dateFormat.format(Date())

        val explanation = alert.aiExplanation ?: "*No AI threat explanation generated. Click 'Run AI Diagnostics' first.*"
        val playbook = alert.aiPlaybook ?: "*No IR playbook generated. Click 'Run AI Diagnostics' first.*"
        val notes = if (alert.userNotes.isNullOrBlank()) {
            "*No custom investigator notes provided.*"
        } else {
            alert.userNotes
        }
        val mitreTactic = alert.mitreTactic ?: "Unmapped Tactic"
        val mitreTechnique = alert.mitreTechnique ?: "Unmapped Technique"
        val blastRadius = alert.blastRadiusPath ?: "Uncalculated Path"
        val remediationScript = alert.remediationScript ?: "# Script not generated yet"

        return """
            # SECURITY INCIDENT TICKET #${alert.id}
            ======================================
            **Report Generated:** $generatedTime
            **Incident Label:** ${alert.title}
            **Severity Level:** ${alert.severity}
            **Current Status:** ${alert.status}
            **Triage Classification:** ${alert.classification}

            --------------------------------------
            ## 1. EVIDENCE & METRICS
            - **Attacker/Source IP:** ${alert.sourceIp}
            - **Target Port/Service:** ${alert.destinationPort}
            - **Telemetry Timestamp:** ${alert.timestamp}

            ### Network Blast Radius Path
            $blastRadius

            ### Raw SIEM / Firewall Log Data
            ```json
            ${alert.rawLog}
            ```

            --------------------------------------
            ## 2. MITRE ATT&CK MAPPING & SOAR
            - **MITRE Tactic:** $mitreTactic
            - **MITRE Technique:** $mitreTechnique

            ### Automated Remediation Mitigation Script
            ```python
            $remediationScript
            ```

            --------------------------------------
            ## 3. GEMINI COPILOT THREAT INTEL
            ### Attack Vector Explanation
            $explanation

            ### Incident Response Playbook
            $playbook

            --------------------------------------
            ## 4. INVESTIGATOR FINDINGS & NOTES
            $notes

            ======================================
            Generated by SOC-Sim Pro Triage Assistant.
        """.trimIndent()
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Incident Report") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun shareIncidentReport(context: Context, reportText: String, alertTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SOC Incident Report - $alertTitle")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        context.startActivity(Intent.createChooser(intent, "Share Incident Report"))
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Factory to instantiate AlertViewModel with repository constructor parameter
    class Factory(
        private val application: Application,
        private val repository: AlertRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlertViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AlertViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
