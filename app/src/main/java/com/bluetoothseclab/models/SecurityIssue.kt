package com.bluetoothseclab.models

data class SecurityIssue(
    val title: String,
    val description: String,
    val severity: Severity,
    val cveReference: String? = null
) {
    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
}
