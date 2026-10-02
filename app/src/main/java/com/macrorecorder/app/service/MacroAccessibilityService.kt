package com.macrorecorder.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.macrorecorder.app.model.ActionType
import com.macrorecorder.app.model.RecordedAction
import kotlinx.coroutines.*

/**
 * Core Accessibility Service that:
 * 1. Intercepts all accessibility events to RECORD user actions
 * 2. Replays recorded actions via gesture dispatch and node interaction
 *
 * Communication is done via static fields + LocalBroadcastManager broadcasts
 */
class MacroAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MacroAccessService"

        // Broadcast actions
        const val ACTION_RECORD_START = "com.macrorecorder.ACTION_RECORD_START"
        const val ACTION_RECORD_STOP = "com.macrorecorder.ACTION_RECORD_STOP"
        const val ACTION_PLAYBACK_START = "com.macrorecorder.ACTION_PLAYBACK_START"
        const val ACTION_PLAYBACK_STOP = "com.macrorecorder.ACTION_PLAYBACK_STOP"
        const val ACTION_STATUS_UPDATE = "com.macrorecorder.ACTION_STATUS_UPDATE"

        const val EXTRA_ACTIONS = "extra_actions_json"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_ACTION_COUNT = "extra_action_count"

        // Singleton reference for communication
        @Volatile
        var instance: MacroAccessibilityService? = null
            private set

        @Volatile
        var isRecording = false
            private set

        @Volatile
        var isPlaying = false
            private set

        // Accumulated recorded actions during a session
        val recordedActions = mutableListOf<RecordedAction>()
        private var recordingStartTime = 0L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var playbackJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Tracks last text per node to detect actual typing
    private val nodeTextCache = mutableMapOf<String, String>()

    // Track last tapped node info to avoid duplicate tap+click events
    private var lastTapTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "MacroAccessibilityService connected")
        broadcastStatus("Service Connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.i(TAG, "MacroAccessibilityService destroyed")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    // ========================
    // RECORDING
    // ========================

    fun startRecording() {
        recordedActions.clear()
        recordingStartTime = System.currentTimeMillis()
        nodeTextCache.clear()
        lastTapTimestamp = 0L
        isRecording = true
        Log.i(TAG, "Recording started")
        broadcastStatus("Recording started")
    }

    fun stopRecording(): List<RecordedAction> {
        isRecording = false
        Log.i(TAG, "Recording stopped. Actions: ${recordedActions.size}")
        broadcastStatus("Recording stopped. ${recordedActions.size} actions captured.")
        return recordedActions.toList()
    }

    private fun recordAction(action: RecordedAction) {
        if (!isRecording) return
        recordedActions.add(action)
        broadcastStatusCount(recordedActions.size)
        Log.v(TAG, "Recorded: ${action.type} at ${action.timestamp}ms")
    }

    private fun elapsedMs(): Long = System.currentTimeMillis() - recordingStartTime

    /**
     * Main event handler — this fires for every accessibility event system-wide
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isRecording) return

        val node = event.source
        val packageName = event.packageName?.toString() ?: ""

        // Skip our own package events
        if (packageName == "com.macrorecorder.app") return

        when (event.eventType) {

            // Window changed = app launch / navigation
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: ""
                if (packageName.isNotEmpty() && className.contains("Activity")) {
                    recordAction(
                        RecordedAction(
                            type = ActionType.APP_LAUNCH,
                            timestamp = elapsedMs(),
                            packageName = packageName,
                            text = className
                        )
                    )
                }
            }

            // Text changed = user typing
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.joinToString("").let {
                    // Compute only the added chars (delta)
                    val nodeKey = "${node?.viewIdResourceName}_${node?.hashCode()}"
                    val prev = nodeTextCache[nodeKey] ?: ""
                    nodeTextCache[nodeKey] = it
                    it // record full text each time for reliability
                }
                if (text.isNotEmpty()) {
                    recordAction(
                        RecordedAction(
                            type = ActionType.TYPE_TEXT,
                            timestamp = elapsedMs(),
                            text = text,
                            nodeId = node?.viewIdResourceName ?: "",
                            nodeClass = node?.className?.toString() ?: "",
                            nodeText = node?.text?.toString() ?: ""
                        )
                    )
                }
            }

            // View clicked
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Only record click if it wasn't from our injected gesture
                if (!isPlaying) {
                    val rect = android.graphics.Rect()
                    node?.getBoundsInScreen(rect)
                    val cx = if (rect.width() > 0 && rect.height() > 0) rect.centerX().toFloat() else 0f
                    val cy = if (rect.width() > 0 && rect.height() > 0) rect.centerY().toFloat() else 0f
                    recordAction(
                        RecordedAction(
                            type = ActionType.CLICK_NODE,
                            timestamp = elapsedMs(),
                            x = cx,
                            y = cy,
                            nodeId = node?.viewIdResourceName ?: "",
                            nodeClass = node?.className?.toString() ?: "",
                            nodeText = node?.text?.toString() ?: "",
                            packageName = packageName
                        )
                    )
                }
            }

            // View long clicked
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                if (!isPlaying) {
                    val rect = android.graphics.Rect()
                    node?.getBoundsInScreen(rect)
                    val cx = if (rect.width() > 0 && rect.height() > 0) rect.centerX().toFloat() else 0f
                    val cy = if (rect.width() > 0 && rect.height() > 0) rect.centerY().toFloat() else 0f
                    recordAction(
                        RecordedAction(
                            type = ActionType.LONG_PRESS,
                            timestamp = elapsedMs(),
                            x = cx,
                            y = cy,
                            nodeId = node?.viewIdResourceName ?: "",
                            nodeClass = node?.className?.toString() ?: "",
                            nodeText = node?.text?.toString() ?: "",
                            packageName = packageName,
                            duration = 800L
                        )
                    )
                }
            }

            // Scrolled
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val direction = when {
                    event.scrollDeltaY > 0 -> 1  // down
                    event.scrollDeltaY < 0 -> 0  // up
                    event.scrollDeltaX > 0 -> 3  // right
                    event.scrollDeltaX < 0 -> 2  // left
                    else -> 1
                }
                recordAction(
                    RecordedAction(
                        type = ActionType.SCROLL,
                        timestamp = elapsedMs(),
                        scrollDirection = direction,
                        nodeId = node?.viewIdResourceName ?: "",
                        nodeClass = node?.className?.toString() ?: ""
                    )
                )
            }

            else -> { /* Ignore other events */ }
        }

        node?.recycle()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    // ========================
    // PLAYBACK
    // ========================

    fun startPlayback(actions: List<RecordedAction>) {
        if (isPlaying) {
            Log.w(TAG, "Already playing")
            return
        }
        isPlaying = true
        broadcastStatus("Playback started")

        playbackJob = serviceScope.launch {
            try {
                executeActions(actions)
                broadcastStatus("Playback complete!")
            } catch (e: CancellationException) {
                broadcastStatus("Playback stopped by user")
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
                broadcastStatus("Playback error: ${e.message}")
            } finally {
                isPlaying = false
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        isPlaying = false
        broadcastStatus("Playback stopped")
    }

    private suspend fun executeActions(actions: List<RecordedAction>) {
        var lastTimestamp = 0L

        for ((index, action) in actions.withIndex()) {
            // Wait for the time gap between actions (respects original timing)
            val delay = action.timestamp - lastTimestamp
            if (delay > 0) {
                delay(delay.coerceAtMost(5000L)) // cap at 5s to avoid extreme waits
            }
            lastTimestamp = action.timestamp

            broadcastStatus("Executing action ${index + 1}/${actions.size}: ${action.type.name}")

            withContext(Dispatchers.Main) {
                executeAction(action)
            }

            // Small buffer after each action
            delay(100)
        }
    }

    private suspend fun executeAction(action: RecordedAction) {
        when (action.type) {

            ActionType.TAP -> {
                performTap(action.x, action.y, action.duration.coerceAtLeast(50L))
            }

            ActionType.LONG_PRESS -> {
                val pressed = performLongClickOnNode(action)
                if (!pressed && (action.x > 0f || action.y > 0f)) {
                    performTap(action.x, action.y, action.duration.coerceAtLeast(800L))
                }
            }

            ActionType.SWIPE -> {
                performSwipe(action.x, action.y, action.x2, action.y2, action.duration.coerceAtLeast(300L))
            }

            ActionType.SCROLL -> {
                performScroll(action.scrollDirection, action.nodeId)
            }

            ActionType.CLICK_NODE -> {
                val clicked = performClickOnNode(action)
                if (!clicked) {
                    if (action.x > 0f || action.y > 0f) {
                        Log.d(TAG, "Node click failed, falling back to coordinate tap at (${action.x}, ${action.y})")
                        performTap(action.x, action.y, 100L)
                    } else {
                        Log.w(TAG, "Node not found for click: ${action.nodeId} / ${action.nodeText}")
                    }
                }
            }

            ActionType.TYPE_TEXT -> {
                performTypeText(action.text, action.nodeId)
            }

            ActionType.KEY_PRESS -> {
                performGlobalAction(action.keyCode)
            }

            ActionType.APP_LAUNCH -> {
                launchApp(action.packageName)
            }

            ActionType.BACK -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            ActionType.HOME -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            ActionType.RECENTS -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }

            ActionType.WAIT -> {
                delay(action.duration)
            }

            ActionType.NOTIFICATION_ACTION -> {
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            }
        }
    }

    // ========================
    // GESTURE HELPERS
    // ========================

    private fun performTap(x: Float, y: Float, duration: Long = 100L) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300L) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performScroll(direction: Int, nodeId: String) {
        // Try to find the scrollable node first
        val root = rootInActiveWindow ?: return
        val action = when (direction) {
            0 -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD // up
            1 -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD  // down
            2 -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD // left
            3 -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD  // right
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }

        fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findScrollable(child)
                if (found != null) return found
                child.recycle()
            }
            return null
        }

        val scrollable = findScrollable(root)
        scrollable?.performAction(action)
        scrollable?.recycle()
        root.recycle()
    }

    private fun performClickOnNode(action: RecordedAction): Boolean {
        val root = rootInActiveWindow ?: return false
        
        fun searchNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            // Match by resource ID
            if (action.nodeId.isNotEmpty() && node.viewIdResourceName == action.nodeId) {
                return node
            }
            // Match by text content
            if (action.nodeText.isNotEmpty() && node.text?.toString()?.trim() == action.nodeText.trim()) {
                return node
            }
            // Match by content description
            if (action.nodeText.isNotEmpty() && node.contentDescription?.toString()?.trim() == action.nodeText.trim()) {
                return node
            }
            // Search children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = searchNode(child)
                if (found != null) return found
                child.recycle()
            }
            return null
        }

        val target = searchNode(root)
        return if (target != null) {
            val result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            target.recycle()
            root.recycle()
            result
        } else {
            root.recycle()
            false
        }
    }

    private fun performLongClickOnNode(action: RecordedAction): Boolean {
        val root = rootInActiveWindow ?: return false

        fun searchNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (action.nodeId.isNotEmpty() && node.viewIdResourceName == action.nodeId) return node
            if (action.nodeText.isNotEmpty() && node.text?.toString()?.trim() == action.nodeText.trim()) return node
            if (action.nodeText.isNotEmpty() && node.contentDescription?.toString()?.trim() == action.nodeText.trim()) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = searchNode(child)
                if (found != null) return found
                child.recycle()
            }
            return null
        }

        val target = searchNode(root)
        return if (target != null) {
            val result = target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            target.recycle()
            root.recycle()
            result
        } else {
            root.recycle()
            false
        }
    }

    private fun performTypeText(text: String, nodeId: String) {
        val root = rootInActiveWindow ?: return
        
        // Find focused node or node by ID
        fun findFocused(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isFocused && node.isEditable) return node
            if (nodeId.isNotEmpty() && node.viewIdResourceName == nodeId) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findFocused(child)
                if (found != null) return found
                child.recycle()
            }
            return null
        }

        val target = findFocused(root)
        if (target != null) {
            // Focus the field first
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            // Set the text
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            target.recycle()
        }
        root.recycle()
    }

    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                Log.w(TAG, "Could not find launch intent for $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app $packageName", e)
        }
    }

    // ========================
    // BROADCASTS
    // ========================

    private fun broadcastStatus(status: String) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_STATUS, status)
        }
        sendBroadcast(intent)
    }

    private fun broadcastStatusCount(count: Int) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_STATUS, "Recording... $count actions captured")
            putExtra(EXTRA_ACTION_COUNT, count)
        }
        sendBroadcast(intent)
    }
}
