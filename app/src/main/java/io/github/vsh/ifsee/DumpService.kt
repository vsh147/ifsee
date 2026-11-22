package io.github.vsh.ifsee

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
import io.github.vsh.ifsee.model.MatchMode
import io.github.vsh.ifsee.model.MatchOp
import io.github.vsh.ifsee.model.Matcher
import io.github.vsh.ifsee.model.MatcherField
import io.github.vsh.ifsee.model.Rule
import io.github.vsh.ifsee.model.RuleValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DumpService : AccessibilityService() {

    private data class NodeData(val text: String, val description: String, val viewId: String)
    private data class CompiledMatcher(val matcher: Matcher, val regex: Regex?)
    private data class CachedRule(val rule: Rule, val matchers: List<CompiledMatcher>)

    private lateinit var repository: RulesRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val writer by lazy { ScreenshotWriter.get(this) }
    private val actionExecutors: List<ActionExecutor> by lazy {
        listOf(ScreenshotActionExecutor { requestScreenshot() })
    }
    private val lastFired = mutableMapOf<String, Long>()

    @Volatile
    var rules: List<Rule> = emptyList()
        private set

    @Volatile
    private var cachedRules: List<CachedRule> = emptyList()

    override fun onCreate() {
        super.onCreate()
        repository = RulesRepository(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            repository.rulesFlow.collect { loaded ->
                cachedRules = loaded.map { rule ->
                    CachedRule(rule, rule.matchers.map { matcher ->
                        CompiledMatcher(matcher, RuleValidator.compile(matcher))
                    })
                }
                rules = loaded
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val packageName = event.packageName?.toString() ?: return
            val applicable = cachedRules.filter { cached ->
                cached.rule.enabled && cached.rule.packageName == packageName
            }
            if (applicable.isEmpty()) return

            val nodes = collectNodes()
            if (nodes.isEmpty()) return

            val now = SystemClock.elapsedRealtime()
            for (cached in applicable) {
                if (!matches(cached, nodes)) continue

                val last = lastFired[cached.rule.id]
                if (last != null && now - last < cached.rule.cooldownMs) continue
                lastFired[cached.rule.id] = now

                val actions = cached.rule.actions
                handler.postDelayed({
                    actionExecutors.forEach { executor ->
                        actions.forEach { action -> executor.execute(action) }
                    }
                }, cached.rule.delayMs.coerceAtLeast(0L))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle accessibility event", e)
        }
    }

    private fun collectNodes(): List<NodeData> {
        val nodes = mutableListOf<NodeData>()
        val roots = try {
            windows.orEmpty().mapNotNull { window ->
                try {
                    window.root
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get window root", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get windows", e)
            emptyList()
        }
        val effective = if (roots.isEmpty()) listOfNotNull(rootInActiveWindow) else roots
        for (root in effective) {
            try {
                scan(root, 0, nodes)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scan window", e)
            }
        }
        return nodes
    }

    private fun scan(node: AccessibilityNodeInfo, depth: Int, nodes: MutableList<NodeData>) {
        if (depth > MAX_DEPTH) return

        try {
            nodes.add(
                NodeData(
                    text = node.text?.toString().orEmpty(),
                    description = node.contentDescription?.toString().orEmpty(),
                    viewId = node.viewIdResourceName.orEmpty()
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read node", e)
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
                scan(child, depth + 1, nodes)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scan child at $index", e)
            }
        }
    }

    private fun matches(cached: CachedRule, nodes: List<NodeData>): Boolean {
        if (cached.matchers.isEmpty()) return false

        val results = cached.matchers.map { compiled ->
            nodes.any { node -> matches(compiled, node) }
        }
        return if (cached.rule.matchMode == MatchMode.ALL) {
            results.all { it }
        } else {
            results.any { it }
        }
    }

    private fun matches(compiled: CompiledMatcher, node: NodeData): Boolean {
        val value = when (compiled.matcher.field) {
            MatcherField.TEXT -> node.text
            MatcherField.DESCRIPTION -> node.description
            MatcherField.VIEW_ID -> node.viewId
        }
        return when (compiled.matcher.op) {
            MatchOp.CONTAINS -> value.contains(compiled.matcher.value)
            MatchOp.EQUALS -> value == compiled.matcher.value
            MatchOp.REGEX -> compiled.regex?.containsMatchIn(value) ?: false
        }
    }

    private fun requestScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, screenshotCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request screenshot", e)
        }
    }

    private val screenshotCallback = object : TakeScreenshotCallback {
        override fun onSuccess(result: ScreenshotResult) {
            var copy: Bitmap? = null
            try {
                val hardwareBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                copy = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                hardwareBitmap?.recycle()
                if (copy == null) Log.w(TAG, "Failed to copy screenshot hardware buffer")
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
            if (copy != null) writer.enqueue(copy)
        }

        override fun onFailure(errorCode: Int) {
            if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                Log.w(TAG, "Screenshot rate limited by system")
            } else {
                Log.w(TAG, "Screenshot failed with error $errorCode")
            }
        }
    }

    override fun onInterrupt() {
        // No actions to interrupt.
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "RuleService"
        const val MAX_DEPTH = 40
    }
}
