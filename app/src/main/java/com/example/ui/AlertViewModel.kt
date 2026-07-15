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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertViewModel(
    private val repository: AlertRepository
) : ViewModel() {

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

    fun awardPhishingPoints() {
        _gameScore.value += 40
        _statusMessage.value = "Phishing telemetry scanned! (+40 pts)"
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private data class SimulatedData(
        val tactic: String,
        val technique: String,
        val port: String,
        val log: String,
        val script: String
    )

    fun triggerSimulatedScenario(name: String, severity: String, host: String) {
        viewModelScope.launch {
            val timestampStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", java.util.Locale.getDefault()).format(java.util.Date())
            
            val data = when (name) {
                "WannaScream Ransomware" -> SimulatedData(
                    tactic = "Impact",
                    technique = "T1486 (Data Encrypted for Impact)",
                    port = "445",
                    log = """{
  "timestamp": "$timestampStr",
  "event_type": "endpoint_compromise",
  "hostname": "$host",
  "alert_name": "Ransomware Encrypted Payload Execution",
  "process": "tasksche.exe",
  "action": "DETECTED_ACTIVE",
  "directories_encrypted": [
    "C:\\Users\\Finance\\Documents",
    "C:\\Users\\Shared\\Database"
  ],
  "ransom_note_dropped": "true",
  "extension_added": ".screamed"
}""".trimIndent(),
                    script = """
                    # Python SOAR Playbook: Quarantine Host & Terminate Ransomware Thread
                    import os
                    import sys
                    
                    def kill_and_isolate_node(proc, hostname):
                        print(f"[*] Terminating rogue process: {proc} on {hostname}")
                        print("[+] Staged Command: taskkill /F /IM " + proc)
                        print("[*] Initiating host level isolation via switch control...")
                        print(f"[+] Successfully isolated {hostname} from corporate subnet.")
                        
                    if __name__ == '__main__':
                        kill_and_isolate_node("tasksche.exe", "$host")
                    """.trimIndent()
                )
                
                "SQL Injection Database Leak" -> SimulatedData(
                    tactic = "Credential Access",
                    technique = "T1190 (Exploit Public-Facing Application)",
                    port = "3306",
                    log = """{
  "timestamp": "$timestampStr",
  "event_type": "sql_injection_probe",
  "attacker_ip": "$host",
  "target_url": "https://api.company.local/v2/users/search",
  "payload": "UNION SELECT username, password_hash FROM admin_users --",
  "http_response_code": 200,
  "bytes_returned": 10485,
  "vulnerability": "SQLi in SQL Statement Construction"
}""".trimIndent(),
                    script = """
                    # Python SOAR Playbook: Inject WAF Query Filter Rule
                    import urllib.request
                    import json
                    
                    def waf_block_pattern(attacker_ip):
                        print(f"[*] Applying Web Application Firewall block rule for IP: {attacker_ip}")
                        payload = {"block_ip": attacker_ip, "reason": "SQLi UNION probe detected", "waf_policy": "Strict"}
                        print(f"[+] API Request dispatched to cloud WAF with body: {json.dumps(payload)}")
                        print("[+] Attacker IP has been blacklisted at the perimeter proxy gateway.")
                        
                    if __name__ == '__main__':
                        waf_block_pattern("$host")
                    """.trimIndent()
                )
                
                "DDoS SYN Volumetric Flood" -> SimulatedData(
                    tactic = "Impact",
                    technique = "T1498 (Network Denial of Service)",
                    port = "80 / 443",
                    log = """{
  "timestamp": "$timestampStr",
  "event_type": "ddos_attack",
  "attacker_ip": "$host",
  "packets_per_sec": 8504000,
  "bandwidth_gbps": 12.4,
  "vulnerability": "SYN Volumetric TCP Flood",
  "state": "Degraded Service Egress"
}""".trimIndent(),
                    script = """
                    # Python SOAR Playbook: Deploy Anycast DDoS Mitigation
                    import urllib.request
                    
                    def enable_ddos_shield(attack_src):
                        print(f"[*] Initiating cloud scrubbing protocol for target IP block: {attack_src}")
                        print("[+] Successfully activated DDoS shield Anycast rerouting.")
                        print("[+] Malicious volume packets redirected to scrubbing centers.")
                        
                    if __name__ == '__main__':
                        enable_ddos_shield("$host")
                    """.trimIndent()
                )
                
                "Linux Local Privilege Escalation" -> SimulatedData(
                    tactic = "Privilege Escalation",
                    technique = "T1068 (Exploitation for Privilege Escalation)",
                    port = "N/A",
                    log = """{
  "timestamp": "$timestampStr",
  "event_type": "privilege_escalation_alert",
  "hostname": "$host",
  "user": "developer_temp",
  "uid": 1004,
  "target_binary": "/usr/bin/sudo",
  "exploit_signature": "CVE-2021-3156 (Baron Samedit)",
  "result": "ROOT_SHELL_OPENED"
}""".trimIndent(),
                    script = """
                    # Python SOAR Playbook: Revoke SSH Shell Access
                    import subprocess
                    
                    def disable_user_account(username):
                        print(f"[*] Revoking PAM credentials and shell access for: {username}")
                        print("[+] Staged Command: usermod -L " + username)
                        print("[+] Staged Command: pkill -u " + username)
                        print(f"[+] Account {username} successfully locked on target system.")
                        
                    if __name__ == '__main__':
                        disable_user_account("developer_temp")
                    """.trimIndent()
                )
                
                else -> SimulatedData(
                    tactic = "Command and Control",
                    technique = "T1071 (Application Layer Protocol)",
                    port = "53",
                    log = """{
  "timestamp": "$timestampStr",
  "event_type": "dns_tunnel_beacon",
  "src_ip": "$host",
  "target_dns_server": "8.8.8.8",
  "anomalous_query": "4165733235364b6579.malicious-domain.com",
  "query_type": "TXT",
  "data_leak_rate_bytes": 12845
}""".trimIndent(),
                    script = """
                    # Python SOAR Playbook: Block Rogue DNS Queries
                    import subprocess
                    
                    def block_dns_c2(attacker_dns_domain, client_ip):
                        print(f"[*] Blocking C2 lookup DNS domain: {attacker_dns_domain}")
                        print(f"[*] Flushing DNS resolver cache on active client node: {client_ip}")
                        print(f"[+] Staged Command: ip route add blackhole 185.112.146.0/24")
                        print("[+] C2 Beacon channel severed.")
                        
                    if __name__ == '__main__':
                        block_dns_c2("malicious-domain.com", "$host")
                    """.trimIndent()
                )
            }

            val alert = com.example.data.AlertEntity(
                title = "$name Detected",
                severity = severity,
                status = "New",
                classification = "Unassigned",
                timestamp = timestampStr,
                sourceIp = host,
                destinationPort = data.port,
                rawLog = data.log,
                remediationScript = data.script,
                mitreTactic = data.tactic,
                mitreTechnique = data.technique,
                blastRadiusPath = "$host ➔ Edge Router ➔ SOC Alarm Channel",
                aiExplanation = null,
                aiPlaybook = null
            )
            
            val newId = repository.insertAlert(alert)
            _selectedAlertId.value = newId.toInt()
            _statusMessage.value = "⚠️ SIMULATED CYBER THREAT TRIGGERED SUCCESSFULLY!"
        }
    }

    // Factory to instantiate AlertViewModel with repository constructor parameter
    class Factory(
        private val repository: AlertRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlertViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AlertViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
