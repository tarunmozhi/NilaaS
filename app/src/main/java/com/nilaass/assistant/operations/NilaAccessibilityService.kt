package com.nilaass.assistant.operations

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled bridge for explicit, user-requested screen navigation.
 * This first version deliberately does not read screen text, click arbitrary
 * controls, record screens, or act in the background.
 */
class NilaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally no screen-content collection in this first milestone.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var activeService: NilaAccessibilityService? = null

        fun isEnabled(): Boolean = activeService != null

        fun goBack(): Boolean = activeService?.performGlobalAction(GLOBAL_ACTION_BACK) == true

        fun goHome(): Boolean = activeService?.performGlobalAction(GLOBAL_ACTION_HOME) == true
    }
}
