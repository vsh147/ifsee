package io.github.vsh.ifsee

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.vsh.ifsee.databinding.ItemRuleBinding
import io.github.vsh.ifsee.model.Rule

class RulesAdapter(
    private val onEnabledChange: (Rule, Boolean) -> Unit,
    private val onEdit: (Rule) -> Unit,
    private val onDelete: (Rule) -> Unit
) : ListAdapter<Rule, RulesAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: Rule) {
            binding.nameText.text = rule.name
            binding.packageText.text = rule.packageName

            binding.enabledSwitch.setOnCheckedChangeListener(null)
            binding.enabledSwitch.isChecked = rule.enabled
            binding.enabledSwitch.setOnCheckedChangeListener { _, checked ->
                onEnabledChange(rule, checked)
            }

            binding.root.setOnClickListener { onEdit(rule) }
            binding.menuButton.setOnClickListener { view ->
                PopupMenu(view.context, view).apply {
                    inflate(R.menu.rule_item)
                    setOnMenuItemClickListener { item ->
                        if (item.itemId == R.id.action_delete) {
                            onDelete(rule)
                            true
                        } else {
                            false
                        }
                    }
                    show()
                }
            }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<Rule>() {
            override fun areItemsTheSame(oldItem: Rule, newItem: Rule): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Rule, newItem: Rule): Boolean = oldItem == newItem
        }
    }
}
