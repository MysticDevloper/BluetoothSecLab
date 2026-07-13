package com.bluetoothseclab.models

data class AttackResult(
    val moduleName: String,
    val status: Status,
    val findings: List<Finding>,
    val summary: String,
    val riskScore: Float,
    val durationMs: Long
) {
    enum class Status { SUCCESS, PARTIAL, FAILED, CANCELLED }

    data class Finding(
        val title: String,
        val description: String,
        val severity: Severity,
        val cveReference: String? = null,
        val remediation: String? = null
    )

    enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }
}
