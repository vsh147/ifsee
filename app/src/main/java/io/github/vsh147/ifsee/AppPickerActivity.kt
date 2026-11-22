package io.github.vsh147.ifsee

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.vsh147.ifsee.databinding.ActivityAppPickerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var adapter: AppPickerAdapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppPickerAdapter { selectApp(it) }
        binding.appsRecycler.layoutManager = LinearLayoutManager(this)
        binding.appsRecycler.adapter = adapter

        binding.searchEdit.addTextChangedListener { text ->
            applyFilter(text?.toString().orEmpty())
        }

        scope.launch {
            allApps = AppCatalog.loadInstalledApps(this@AppPickerActivity)
            applyFilter(binding.searchEdit.text?.toString().orEmpty())
        }
    }

    private fun applyFilter(query: String) {
        val filter = query.trim()
        val apps = if (filter.isEmpty()) {
            allApps
        } else {
            allApps.filter { app ->
                app.label.contains(filter, ignoreCase = true) ||
                    app.packageName.contains(filter, ignoreCase = true)
            }
        }
        adapter.submitList(apps)
    }

    private fun selectApp(app: AppInfo) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PACKAGE, app.packageName).putExtra(EXTRA_LABEL, app.label))
        finish()
    }

    override fun onDestroy() {
        adapter.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "app_package"
        const val EXTRA_LABEL = "app_label"
    }
}
