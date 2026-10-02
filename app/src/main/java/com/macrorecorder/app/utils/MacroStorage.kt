package com.macrorecorder.app.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.macrorecorder.app.model.Macro
import java.io.File
import java.util.UUID

/**
 * Manages saving, loading, listing and deleting macros from internal storage
 */
class MacroStorage(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val macrosDir: File
        get() = File(context.filesDir, "macros").also { it.mkdirs() }

    companion object {
        private const val TAG = "MacroStorage"
        private const val MACRO_EXT = ".macro.json"
    }

    /**
     * Save a macro to disk with the given name
     */
    fun saveMacro(macro: Macro): Boolean {
        return try {
            val safeId = macro.id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val file = File(macrosDir, "$safeId$MACRO_EXT")
            val json = gson.toJson(macro)
            file.writeText(json, Charsets.UTF_8)
            Log.d(TAG, "Saved macro '${macro.name}' to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save macro", e)
            false
        }
    }

    /**
     * Load all saved macros
     */
    fun loadAllMacros(): List<Macro> {
        return macrosDir.listFiles { f -> f.name.endsWith(MACRO_EXT) }
            ?.mapNotNull { file ->
                try {
                    val json = file.readText(Charsets.UTF_8)
                    gson.fromJson(json, Macro::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load macro from ${file.name}", e)
                    null
                }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /**
     * Load a single macro by ID
     */
    fun loadMacro(id: String): Macro? {
        val safeId = id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(macrosDir, "$safeId$MACRO_EXT")
        return if (file.exists()) {
            try {
                val json = file.readText(Charsets.UTF_8)
                gson.fromJson(json, Macro::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load macro $id", e)
                null
            }
        } else null
    }

    /**
     * Delete a macro by ID
     */
    fun deleteMacro(id: String): Boolean {
        val safeId = id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(macrosDir, "$safeId$MACRO_EXT")
        return file.delete()
    }

    /**
     * Generate a unique macro ID from the name
     */
    fun generateId(name: String): String {
        val sanitized = name.trim().replace("\\s+".toRegex(), "_").lowercase()
        val unique = UUID.randomUUID().toString().take(8)
        return "${sanitized}_$unique"
    }

    /**
     * Get the storage directory path for display
     */
    fun getStoragePath(): String = macrosDir.absolutePath
}
