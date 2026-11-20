package com.example.treedumper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DumpService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val writer by lazy { DumpWriter.get(this) }
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val screenshotHandler = Handler(Looper.getMainLooper())

    private var lastDumpAt = 0L
    private var lastScreenshotAt = 0L
    private var screenshotPending = false
    private var lastHash: Int? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            if (!prefs.dumpEnabled) return

            val targetPackage = prefs.targetPackage.trim()
            if (targetPackage.isEmpty()) return
            if (event.packageName?.toString() != targetPackage) return

            val now = SystemClock.elapsedRealtime()
            if (now - lastDumpAt < THROTTLE_MS) return
            lastDumpAt = now

            val roots = collectRoots(targetPackage)
            if (roots.isEmpty()) return

            val filter = prefs.logFilter
            val tree = StringBuilder()
            for (root in roots) {
                try {
                    tree.append(TreeDumper.dumpTree(root, filter))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to dump a window", e)
                }
            }
            if (tree.isEmpty()) return

            requestScreenshotIfAllowed()

            val text = tree.toString()
            val hash = text.hashCode()
            if (hash == lastHash) return
            lastHash = hash

            val timestamp = timeFormat.format(Date())
            writer.enqueue(timestamp, eventTypeName(event.eventType), targetPackage, roots.size, text)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle accessibility event", e)
        }
    }

    private fun collectRoots(targetPackage: String): List<AccessibilityNodeInfo> {
        val windows = try {
            this.windows.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get windows", e)
            emptyList()
        }

        val roots = windows.mapNotNull { window ->
            try {
                window.root
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get window root", e)
                null
            }
        }.filter { root ->
            try {
                root.packageName?.toString() == targetPackage
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get root package", e)
                false
            }
        }

        if (roots.isNotEmpty()) return roots

        return listOfNotNull(rootInActiveWindow).filter { root ->
            try {
                root.packageName?.toString() == targetPackage
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get active root package", e)
                false
            }
        }
    }

    private fun requestScreenshotIfAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (screenshotPending) return

        val now = SystemClock.elapsedRealtime()
        val intervalMs = prefs.screenshotIntervalSeconds * 1000L
        if (now - lastScreenshotAt < intervalMs) return
        lastScreenshotAt = now
        screenshotPending = true

        screenshotHandler.postDelayed({
            screenshotPending = false
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, screenshotCallback)
        }, prefs.screenshotDelaySeconds * 1000L)
    }

    private val screenshotCallback = object : TakeScreenshotCallback {
        override fun onSuccess(result: ScreenshotResult) {
            var copy: Bitmap? = null
            try {
                val hardwareBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                copy = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                hardwareBitmap?.recycle()
                if (copy == null) {
                    Log.w(TAG, "Failed to copy screenshot hardware buffer")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy screenshot hardware buffer", e)
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        result.hardwareBuffer.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to close screenshot hardware buffer", e)
                    }
                }
            }
            if (copy != null) {
                writer.enqueueScreenshot(copy)
            }
        }

        override fun onFailure(errorCode: Int) {
            Log.w(TAG, "Screenshot failed with error $errorCode")
        }
    }

    private fun eventTypeName(eventType: Int): String = when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
        else -> "UNKNOWN_$eventType"
    }

    override fun onInterrupt() {
        // Nothing to interrupt: the service only reads the tree.
    }

    override fun onDestroy() {
        screenshotHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DumpService"
        const val THROTTLE_MS = 500L
    }
}
