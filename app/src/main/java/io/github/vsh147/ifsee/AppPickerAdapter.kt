package io.github.vsh147.ifsee

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.vsh147.ifsee.databinding.ItemAppBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppPickerAdapter(
    private val onSelect: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppPickerAdapter.ViewHolder>(DiffCallback) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.cancelIconLoad()
        super.onViewRecycled(holder)
    }

    fun close() {
        scope.cancel()
    }

    inner class ViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {

        private val placeholder = ContextCompat.getDrawable(binding.root.context, android.R.drawable.sym_def_app_icon)
        private var iconJob: Job? = null

        fun bind(app: AppInfo) {
            binding.appName.text = app.label
            binding.appPackage.text = app.packageName
            binding.appIcon.setImageDrawable(AppCatalog.cachedIcon(app.packageName) ?: placeholder)

            iconJob?.cancel()
            val boundPosition = bindingAdapterPosition
            iconJob = scope.launch {
                val icon = AppCatalog.loadIcon(binding.root.context, app.packageName)
                if (boundPosition != RecyclerView.NO_POSITION && boundPosition == bindingAdapterPosition) {
                    binding.appIcon.setImageDrawable(icon ?: placeholder)
                }
            }

            binding.root.setOnClickListener { onSelect(app) }
        }

        fun cancelIconLoad() {
            iconJob?.cancel()
            iconJob = null
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean = oldItem == newItem
        }
    }
}
