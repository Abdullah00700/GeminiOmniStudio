package com.example.geminidevagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class AssistantAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // State is queried on demand when Gemini calls screen tools.
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun readVisibleElements(maxItems: Int = 40): JSONArray {
        val result = JSONArray()
        val root = rootInActiveWindow ?: return result
        collectNodes(root, result, maxItems)
        return result
    }

    private fun collectNodes(node: AccessibilityNodeInfo, out: JSONArray, maxItems: Int) {
        if (out.length() >= maxItems) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val isClickable = node.isClickable
        val isEditable = node.isEditable

        if (text.isNotEmpty() || desc.isNotEmpty() || isEditable) {
            out.put(
                JSONObject().apply {
                    if (text.isNotEmpty()) put("text", text)
                    if (desc.isNotEmpty()) put("desc", desc)
                    if (isClickable) put("clickable", true)
                    if (isEditable) put("editable", true)
                }
            )
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectNodes(child, out, maxItems)
            }
        }
    }

    fun tapByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val normalized = query.trim().lowercase()
        val target = findNode(root) { node ->
            val t = node.text?.toString()?.lowercase().orEmpty()
            val d = node.contentDescription?.toString()?.lowercase().orEmpty()
            t.contains(normalized) || d.contains(normalized)
        } ?: return false

        var clickable: AccessibilityNodeInfo? = target
        while (clickable != null && !clickable.isClickable) {
            clickable = clickable.parent
        }
        return (clickable ?: target).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun typeIntoField(hintQuery: String, textToType: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val normalized = hintQuery.trim().lowercase()
        val target = findNode(root) { node ->
            if (!node.isEditable) return@findNode false
            if (normalized.isEmpty()) return@findNode true
            val t = node.text?.toString()?.lowercase().orEmpty()
            val h = node.hintText?.toString()?.lowercase().orEmpty()
            val d = node.contentDescription?.toString()?.lowercase().orEmpty()
            t.contains(normalized) || h.contains(normalized) || d.contains(normalized)
        } ?: findNode(root) { it.isEditable } ?: return false

        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findNode(root) { it.isScrollable } ?: return false
        val action = if (direction.lowercase() == "up" || direction.lowercase() == "backward") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return scrollable.performAction(action)
    }

    fun performNavAction(action: String): Boolean {
        val code = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        return performGlobalAction(code)
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findNode(child, predicate)
            if (match != null) return match
        }
        return null
    }

    companion object {
        @Volatile
        var instance: AssistantAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }
}
