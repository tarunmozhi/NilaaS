package com.nilaass.assistant.actions

import com.nilaass.assistant.policy.PolicyDecision

interface AssistantAction {
    val id: String
    suspend fun validate(): PolicyDecision
    suspend fun execute(): ActionResult
}

data class ActionResult(val success: Boolean, val message: String)
