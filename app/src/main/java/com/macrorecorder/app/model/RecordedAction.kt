package com.macrorecorder.app.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single recorded action
 */
data class RecordedAction(
    @SerializedName("type") val type: ActionType,
    @SerializedName("timestamp") val timestamp: Long,           // ms since recording start
    @SerializedName("x") val x: Float = 0f,                    // Touch X coordinate
    @SerializedName("y") val y: Float = 0f,                    // Touch Y coordinate
    @SerializedName("x2") val x2: Float = 0f,                  // Swipe end X
    @SerializedName("y2") val y2: Float = 0f,                  // Swipe end Y
    @SerializedName("duration") val duration: Long = 0L,       // Touch/swipe duration in ms
    @SerializedName("text") val text: String = "",             // Typed text or content desc
    @SerializedName("packageName") val packageName: String = "", // App package for app launch
    @SerializedName("nodeId") val nodeId: String = "",         // View resource ID
    @SerializedName("nodeClass") val nodeClass: String = "",   // View class name
    @SerializedName("nodeText") val nodeText: String = "",     // View text content
    @SerializedName("scrollDirection") val scrollDirection: Int = 0, // 0=up, 1=down, 2=left, 3=right
    @SerializedName("keyCode") val keyCode: Int = 0            // Key code for key events
)

/**
 * Types of actions that can be recorded and replayed
 */
enum class ActionType {
    TAP,            // Single tap on screen
    LONG_PRESS,     // Long press on screen
    SWIPE,          // Swipe gesture
    SCROLL,         // Scroll in a direction
    TYPE_TEXT,      // Text input via keyboard
    KEY_PRESS,      // Hardware/soft key press
    APP_LAUNCH,     // Opening an app
    BACK,           // Back button press
    HOME,           // Home button press
    RECENTS,        // Recents button
    CLICK_NODE,     // Click on specific UI node (by ID/text)
    WAIT,           // Intentional delay/pause
    NOTIFICATION_ACTION // Interact with a notification
}

/**
 * A complete macro (named collection of recorded actions)
 */
data class Macro(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("duration") val duration: Long,         // Total duration in ms
    @SerializedName("actionCount") val actionCount: Int,
    @SerializedName("actions") val actions: List<RecordedAction>,
    @SerializedName("description") val description: String = ""
)
