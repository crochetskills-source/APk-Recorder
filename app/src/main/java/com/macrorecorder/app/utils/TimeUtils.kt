package com.macrorecorder.app.utils

import java.util.concurrent.TimeUnit

object TimeUtils {
    /**
     * Format milliseconds to human-readable duration string
     */
    fun formatDuration(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        val millis = ms % 1000
        return when {
            minutes > 0 -> String.format("%d:%02d.%03d", minutes, seconds, millis)
            else -> String.format("%d.%03ds", seconds, millis)
        }
    }

    /**
     * Format a unix timestamp to readable date/time
     */
    fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy - HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
