package com.oldbutgold

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Clean service foundation.
 *
 * Game-specific detection and state transitions are intentionally not faked here:
 * they will be implemented against the user's actual minimap and HUD references.
 */
class GameAutomationService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO: Add screen-state handling and user-enabled automation.
    }

    override fun onInterrupt() = Unit
}
