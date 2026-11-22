package com.example.treedumper

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.treedumper.databinding.ActivityMainBinding
import com.example.treedumper.model.Rule
import com.example.treedumper.model.RulesJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: RulesRepository
    private lateinit var adapter: RulesAdapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = RulesJson.json

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = RulesRepository(this)
        adapter = RulesAdapter(
            onEnabledChange = { rule, enabled ->
                scope.launch { repository.upsert(rule.copy(enabled = enabled)) }
            },
            onEdit = { openRule(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rulesRecycler.layoutManager = LinearLayoutManager(this)
        binding.rulesRecycler.adapter = adapter

        scope.launch {
            repository.rulesFlow.collect { adapter.submitList(it) }
        }

        binding.openSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.addRuleButton.setOnClickListener {
            openRule(Rule(name = getString(R.string.new_rule), packageName = ""))
        }
        binding.galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val component = ComponentName(this, DumpService::class.java).flattenToString()
        val isEnabled = enabled.split(':').any { it.equals(component, ignoreCase = true) }
        binding.statusText.setText(
            if (isEnabled) R.string.service_enabled else R.string.service_disabled
        )
    }

    private fun openRule(rule: Rule) {
        val intent = Intent(this, RuleActivity::class.java)
            .putExtra(RuleActivity.EXTRA_RULE_JSON, json.encodeToString(rule))
        startActivity(intent)
    }

    private fun confirmDelete(rule: Rule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_rule_title)
            .setMessage(R.string.delete_rule_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                scope.launch { repository.delete(rule.id) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
