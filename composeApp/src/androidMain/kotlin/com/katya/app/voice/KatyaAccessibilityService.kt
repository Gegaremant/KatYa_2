package com.katya.app.voice

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class KatyaAccessibilityService : AccessibilityService() {
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We can capture screen content here or listen to specific events
    }

    override fun onInterrupt() {
        // Handle interruption
    }
    
    /**
     * Reads the current screen's text content. Can be called from our agent tools.
     */
    fun readScreenContent(): String {
        val rootNode = rootInActiveWindow ?: return ""
        val stringBuilder = java.lang.StringBuilder()
        extractTextFromNode(rootNode, stringBuilder)
        return stringBuilder.toString()
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo, builder: java.lang.StringBuilder) {
        if (node.text != null && node.text.isNotBlank()) {
            builder.append(node.text).append("\n")
        } else if (node.contentDescription != null && node.contentDescription.isNotBlank()) {
            builder.append(node.contentDescription).append("\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractTextFromNode(child, builder)
            }
        }
    }
    
    /**
     * Attempts to click a view by its text or content description.
     */
    fun clickOnText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return searchAndClick(rootNode, targetText)
    }

    private fun searchAndClick(node: AccessibilityNodeInfo, targetText: String): Boolean {
        if (node.text?.toString()?.contains(targetText, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(targetText, ignoreCase = true) == true) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            } else {
                // If it's not clickable, try to click its parent
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    parent = parent.parent
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && searchAndClick(child, targetText)) {
                return true
            }
        }
        return false
    }

    companion object {
        var instance: KatyaAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}
