package com.nilaass.assistant.policy

enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

data class ActionRequest(
    val actionId: String,
    val risk: RiskLevel,
    val requiresNetwork: Boolean = false,
    val containsPrivateData: Boolean = false
)

data class PolicyDecision(val allowed: Boolean, val requiresConfirmation: Boolean, val reason: String)

class PolicyEngine {
    fun evaluate(request: ActionRequest, onlineUseEnabled: Boolean = false): PolicyDecision {
        if (request.requiresNetwork && !onlineUseEnabled)
            return PolicyDecision(false, false, "Online access has not been enabled by the user.")
        if (request.containsPrivateData && request.requiresNetwork)
            return PolicyDecision(false, false, "Private data must be reviewed and explicitly approved before network use.")
        return when (request.risk) {
            RiskLevel.LOW -> PolicyDecision(true, false, "Low-risk action.")
            RiskLevel.MEDIUM, RiskLevel.HIGH -> PolicyDecision(true, true, "User confirmation is required.")
            RiskLevel.CRITICAL -> PolicyDecision(false, false, "Critical actions are blocked in this prototype.")
        }
    }
}
