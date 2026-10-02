package com.macrorecorder.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.macrorecorder.app.databinding.ItemMacroBinding
import com.macrorecorder.app.model.Macro
import com.macrorecorder.app.utils.TimeUtils

class MacroListAdapter(
    private val macros: List<Macro>,
    private val onPlay: (Macro) -> Unit,
    private val onDelete: (Macro) -> Unit,
    private val onRename: (Macro) -> Unit
) : RecyclerView.Adapter<MacroListAdapter.MacroViewHolder>() {

    inner class MacroViewHolder(private val binding: ItemMacroBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(macro: Macro) {
            binding.tvMacroName.text = macro.name
            binding.tvMacroMeta.text = buildString {
                append("${macro.actionCount} actions")
                if (macro.duration > 0) append(" • ${TimeUtils.formatDuration(macro.duration)}")
                append("\n${TimeUtils.formatTimestamp(macro.createdAt)}")
            }
            binding.tvMacroDesc.text = macro.description.ifEmpty { "No description" }

            binding.btnPlay.setOnClickListener { onPlay(macro) }
            binding.btnDelete.setOnClickListener { onDelete(macro) }
            binding.btnEdit.setOnClickListener { onRename(macro) }

            // Color the play button based on action count
            val hue = (macro.actionCount * 23 % 360).toFloat()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MacroViewHolder {
        val binding = ItemMacroBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MacroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MacroViewHolder, position: Int) {
        holder.bind(macros[position])
    }

    override fun getItemCount() = macros.size
}
