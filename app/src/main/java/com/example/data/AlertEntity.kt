package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val severity: String, // CRITICAL, HIGH, MEDIUM, LOW
    val status: String, // New, Investigating, Resolved
    val classification: String, // Unassigned, True Positive, False Positive
    val timestamp: String,
    val sourceIp: String,
    val destinationPort: String,
    val rawLog: String,
    val aiExplanation: String? = null,
    val aiPlaybook: String? = null,
    val userNotes: String? = null,
    val incidentReport: String? = null,
    val mitreTactic: String? = null,
    val mitreTechnique: String? = null,
    val remediationScript: String? = null,
    val blastRadiusPath: String? = null
)
