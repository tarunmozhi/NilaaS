package com.nilaass.assistant

import com.nilaass.assistant.policy.*
import org.junit.Assert.*
import org.junit.Test

class PolicyEngineTest {
    private val policy = PolicyEngine()
    @Test fun blocksNetworkWhenNotEnabled() {
        assertFalse(policy.evaluate(ActionRequest("research", RiskLevel.LOW, requiresNetwork = true)).allowed)
    }
    @Test fun requiresConfirmationForHighRisk() {
        val result = policy.evaluate(ActionRequest("remove-app", RiskLevel.HIGH))
        assertTrue(result.allowed); assertTrue(result.requiresConfirmation)
    }
    @Test fun blocksCriticalActions() {
        assertFalse(policy.evaluate(ActionRequest("root-shell", RiskLevel.CRITICAL)).allowed)
    }
}
