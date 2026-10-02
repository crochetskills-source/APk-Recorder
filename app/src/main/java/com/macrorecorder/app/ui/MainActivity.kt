package com.macrorecorder.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.macrorecorder.app.R
import com.macrorecorder.app.databinding.ActivityMainBinding
import com.macrorecorder.app.model.Macro
import com.macrorecorder.app.model.RecordedAction
import com.macrorecorder.app.service.FloatingControlService
import com.macrorecorder.app.service.MacroAccessibilityService
import com.macrorecorder.app.utils.MacroStorage
import com.macrorecorder.app.utils.TimeUtils
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var macroStorage: MacroStorage
    private lateinit var macroAdapter: MacroListAdapter
    private var macros = mutableListOf<Macro>()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                MacroAccessibilityService.ACTION_STATUS_UPDATE -> {
                    val status = intent.getStringExtra(MacroAccessibilityService.EXTRA_STATUS) ?: ""
                    val count = intent.getIntExtra(MacroAccessibilityService.EXTRA_ACTION_COUNT, -1)
                    runOnUiThread {
                        binding.tvStatus.text = status
                        if (count >= 0) {
                            binding.tvActionCount.text = "$count actions"
                            binding.tvActionCount.visibility = View.VISIBLE
                        }
                        updateRecordingUI()
                    }
                }
                MacroAccessibilityService.ACTION_RECORD_STOP -> {
                    runOnUiThread { showSaveMacroDialog() }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        macroStorage = MacroStorage(this)
        setupRecyclerView()
        setupButtons()
        loadMacros()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(MacroAccessibilityService.ACTION_STATUS_UPDATE)
            addAction(MacroAccessibilityService.ACTION_RECORD_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        updateRecordingUI()
        loadMacros()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statusReceiver) } catch (e: Exception) {}
    }

    private fun setupRecyclerView() {
        macroAdapter = MacroListAdapter(macros,
            onPlay = { macro -> playMacro(macro) },
            onDelete = { macro -> confirmDeleteMacro(macro) },
            onRename = { macro -> showRenameMacroDialog(macro) }
        )
        binding.rvMacros.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = macroAdapter
        }
    }

    private fun setupButtons() {
        // Record button
        binding.btnRecord.setOnClickListener {
            val service = MacroAccessibilityService.instance
            if (service == null) {
                showAccessibilityDialog()
                return@setOnClickListener
            }
            if (!MacroAccessibilityService.isRecording) {
                startRecording()
            } else {
                stopRecordingAndSave()
            }
        }

        // Stop playback button
        binding.btnStopPlayback.setOnClickListener {
            MacroAccessibilityService.instance?.stopPlayback()
            updateRecordingUI()
        }

        // Enable Accessibility button
        binding.btnEnableAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // Enable overlay button
        binding.btnEnableOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        // Start floating controls
        binding.btnFloatingControls.setOnClickListener {
            startFloatingControls()
        }
    }

    private fun startRecording() {
        binding.tvStatus.text = "Recording started... perform your actions!"
        binding.tvActionCount.text = "0 actions"
        binding.tvActionCount.visibility = View.VISIBLE
        MacroAccessibilityService.instance?.startRecording()
        updateRecordingUI()

        Snackbar.make(binding.root, "Recording! Go perform your actions now.", Snackbar.LENGTH_LONG)
            .setAction("OK") {}
            .show()
    }

    private fun stopRecordingAndSave() {
        val service = MacroAccessibilityService.instance ?: return
        service.stopRecording()
        showSaveMacroDialog()
    }

    private fun showSaveMacroDialog() {
        val actions = MacroAccessibilityService.recordedActions.toList()
        if (actions.isEmpty()) {
            Toast.makeText(this, "No actions were recorded", Toast.LENGTH_SHORT).show()
            updateRecordingUI()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_save_macro, null)
        val nameField = view.findViewById<TextInputEditText>(R.id.et_macro_name)
        val descField = view.findViewById<TextInputEditText>(R.id.et_macro_desc)

        MaterialAlertDialogBuilder(this)
            .setTitle("💾 Save Macro")
            .setMessage("${actions.size} actions recorded")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = nameField.text?.toString()?.trim() ?: ""
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveMacro(name, descField.text?.toString()?.trim() ?: "", actions)
            }
            .setNegativeButton("Discard") { _, _ ->
                binding.tvActionCount.visibility = View.GONE
                updateRecordingUI()
            }
            .setCancelable(false)
            .show()

        updateRecordingUI()
    }

    private fun saveMacro(name: String, description: String, actions: List<RecordedAction>) {
        val totalDuration = if (actions.isNotEmpty()) actions.last().timestamp else 0L
        val macro = Macro(
            id = macroStorage.generateId(name),
            name = name,
            createdAt = System.currentTimeMillis(),
            duration = totalDuration,
            actionCount = actions.size,
            actions = actions,
            description = description
        )

        if (macroStorage.saveMacro(macro)) {
            Toast.makeText(this, "✅ Macro '$name' saved!", Toast.LENGTH_SHORT).show()
            loadMacros()
        } else {
            Toast.makeText(this, "Failed to save macro", Toast.LENGTH_SHORT).show()
        }
        binding.tvActionCount.visibility = View.GONE
        updateRecordingUI()
    }

    private fun playMacro(macro: Macro) {
        val service = MacroAccessibilityService.instance
        if (service == null) {
            showAccessibilityDialog()
            return
        }
        if (MacroAccessibilityService.isPlaying) {
            Toast.makeText(this, "Already playing a macro!", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("▶ Run: ${macro.name}")
            .setMessage(
                "${macro.actionCount} actions\n" +
                "Duration: ${TimeUtils.formatDuration(macro.duration)}\n\n" +
                "The macro will execute automatically. You can stop it anytime from the floating controls."
            )
            .setPositiveButton("Run") { _, _ ->
                service.startPlayback(macro.actions)
                updateRecordingUI()
                Snackbar.make(binding.root, "Running macro: ${macro.name}", Snackbar.LENGTH_LONG)
                    .setAction("Stop") { service.stopPlayback() }
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteMacro(macro: Macro) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Macro")
            .setMessage("Delete '${macro.name}'? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                macroStorage.deleteMacro(macro.id)
                loadMacros()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameMacroDialog(macro: Macro) {
        val view = layoutInflater.inflate(R.layout.dialog_save_macro, null)
        val nameField = view.findViewById<TextInputEditText>(R.id.et_macro_name)
        val descField = view.findViewById<TextInputEditText>(R.id.et_macro_desc)
        nameField.setText(macro.name)
        descField.setText(macro.description)

        MaterialAlertDialogBuilder(this)
            .setTitle("✏ Edit Macro")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameField.text?.toString()?.trim() ?: macro.name
                val newDesc = descField.text?.toString()?.trim() ?: ""
                val updated = macro.copy(name = newName, description = newDesc)
                macroStorage.saveMacro(updated)
                loadMacros()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadMacros() {
        lifecycleScope.launch {
            val loaded = macroStorage.loadAllMacros()
            macros.clear()
            macros.addAll(loaded)
            macroAdapter.notifyDataSetChanged()
            binding.tvEmptyState.visibility = if (macros.isEmpty()) View.VISIBLE else View.GONE
            binding.rvMacros.visibility = if (macros.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun updateRecordingUI() {
        val isRecording = MacroAccessibilityService.isRecording
        val isPlaying = MacroAccessibilityService.isPlaying
        val serviceEnabled = MacroAccessibilityService.instance != null

        when {
            !serviceEnabled -> {
                binding.btnRecord.text = "⚠ Accessibility Not Enabled"
                binding.btnRecord.isEnabled = true
                binding.statusCard.setCardBackgroundColor(getColor(R.color.status_warning))
            }
            isRecording -> {
                binding.btnRecord.text = "⏹ Stop Recording"
                binding.statusCard.setCardBackgroundColor(getColor(R.color.status_recording))
            }
            isPlaying -> {
                binding.btnRecord.text = "⏸ Playing..."
                binding.btnRecord.isEnabled = false
                binding.btnStopPlayback.visibility = View.VISIBLE
                binding.statusCard.setCardBackgroundColor(getColor(R.color.status_playing))
            }
            else -> {
                binding.btnRecord.text = "⏺ Start Recording"
                binding.btnRecord.isEnabled = true
                binding.btnStopPlayback.visibility = View.GONE
                binding.statusCard.setCardBackgroundColor(getColor(R.color.status_idle))
            }
        }
    }

    private fun checkPermissions() {
        if (!isAccessibilityServiceEnabled()) {
            binding.layoutAccessibilityWarning.visibility = View.VISIBLE
            binding.btnRecord.isEnabled = false
        } else {
            binding.layoutAccessibilityWarning.visibility = View.GONE
        }

        if (!Settings.canDrawOverlays(this)) {
            binding.layoutOverlayWarning.visibility = View.VISIBLE
        } else {
            binding.layoutOverlayWarning.visibility = View.GONE
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (MacroAccessibilityService.instance != null) return true
        val expectedShort = android.content.ComponentName(this, MacroAccessibilityService::class.java).flattenToShortString()
        val expectedLong = android.content.ComponentName(this, MacroAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedShort) || enabledServices.contains(expectedLong)
    }

    private fun showAccessibilityDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage(
                "Macro Recorder needs the Accessibility Service to capture and replay your actions.\n\n" +
                "Steps:\n" +
                "1. Tap 'Open Settings' below\n" +
                "2. Find 'Macro Recorder' in the list\n" +
                "3. Toggle it ON\n" +
                "4. Tap 'Allow' in the confirmation dialog\n" +
                "5. Return to this app"
            )
            .setPositiveButton("Open Settings") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startFloatingControls() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        startService(Intent(this, FloatingControlService::class.java))
        Toast.makeText(this, "Floating controls started! You can now navigate to any app.", Toast.LENGTH_LONG).show()
    }
}
