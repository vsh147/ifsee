package com.example.treedumper

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object TreeDumper {

    private const val TAG = "TreeDumper"
    private const val MAX_DEPTH = 40
    private const val INDENT = "  "

    fun dumpTree(root: AccessibilityNodeInfo): String {
        val out = StringBuilder()
        appendNode(root, 0, out)
        return out.toString()
    }

    private fun appendNode(node: AccessibilityNodeInfo, depth: Int, out: StringBuilder) {
        if (depth > MAX_DEPTH) return

        try {
            val className = node.className?.toString().asNodeValue() ?: NULL_VALUE
            val text = node.text?.toString().asNodeValue() ?: NULL_VALUE
            val description = node.contentDescription?.toString().asNodeValue() ?: NULL_VALUE
            val viewId = node.viewIdResourceName.asNodeValue() ?: NULL_VALUE
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            out.append(INDENT.repeat(depth))
                .append('<').append(className).append('>')
                .append(" text=\"").append(text).append('"')
                .append(" desc=\"").append(description).append('"')
                .append(" id=").append(viewId)
                .append(" bounds=[")
                .append(bounds.left).append(',')
                .append(bounds.top).append(',')
                .append(bounds.right).append(',')
                .append(bounds.bottom).append(']')
                .append(" click=").append(node.isClickable)
                .append(" enabled=").append(node.isEnabled)
                .append('\n')
        } catch (e: Exception) {
            Log.w(TAG, "Failed to format node", e)
            return
        }

        val childCount = try {
            node.childCount
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get child count", e)
            0
        }

        for (index in 0 until childCount) {
            val child = try {
                node.getChild(index)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get child at $index", e)
                null
            } ?: continue

            try {
                appendNode(child, depth + 1, out)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to traverse child at $index", e)
            }
        }
    }

    private fun String?.asNodeValue(): String? =
        takeIf { it?.isNotEmpty() == true }
            ?.replace("\r\n", "\\n")
            ?.replace("\n", "\\n")
            ?.replace("\r", "\\n")

    private const val NULL_VALUE = "null"
}
