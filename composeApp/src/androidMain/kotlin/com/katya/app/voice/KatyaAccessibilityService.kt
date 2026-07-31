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

    fun performGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun clickNodeByContentDescription(desc: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(desc) // Content description falls back to text search often, but we should iterate
        
        // Manual search for content description
        return clickNodeMatching(rootNode) { it.contentDescription?.toString()?.equals(desc, ignoreCase = true) == true }
    }

    fun clickNodeById(viewId: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return false
    }

    fun setTextNodeById(viewId: String, text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            if (node.isEditable || node.className?.contains("EditText") == true) {
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        }
        return false
    }

    private fun clickNodeMatching(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (predicate(node) && node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickNodeMatching(child, predicate)) {
                return true
            }
        }
        return false
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
