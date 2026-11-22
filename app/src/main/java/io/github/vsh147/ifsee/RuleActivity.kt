package io.github.vsh147.ifsee

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import io.github.vsh147.ifsee.databinding.ActivityRuleBinding
import io.github.vsh147.ifsee.databinding.ItemMatcherBinding
import io.github.vsh147.ifsee.model.MatchMode
import io.github.vsh147.ifsee.model.MatchOp
import io.github.vsh147.ifsee.model.Matcher
import io.github.vsh147.ifsee.model.MatcherField
import io.github.vsh147.ifsee.model.Rule
import io.github.vsh147.ifsee.model.RuleAction
import io.github.vsh147.ifsee.model.RuleValidator
import io.github.vsh147.ifsee.model.RulesJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString

class RuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRuleBinding
    private lateinit var repository: RulesRepository
    private lateinit var rule: Rule
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = RulesJson.json
    private val matcherBindings = mutableListOf<ItemMatcherBinding>()
    private var selectedApp: AppInfo? = null

    private val appPickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val packageName = result.data?.getStringExtra(AppPickerActivity.EXTRA_PACKAGE)
                ?: return@registerForActivityResult
            val label = result.data?.getStringExtra(AppPickerActivity.EXTRA_LABEL)
                ?.takeIf { it.isNotBlank() }
                ?: packageName
            selectedApp = AppInfo(label, packageName)
            binding.packageEdit.setText(packageName)
            renderSelectedApp()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val raw = intent.getStringExtra(EXTRA_RULE_JSON) ?: run { finish(); return }
        rule = try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            finish()
            return
        }
        repository = RulesRepository(this)

        binding.nameEdit.setText(rule.name)
        setupAppSelection()
        binding.modeSpinner.setSelection(if (rule.matchMode == MatchMode.ALL) 0 else 1)
        rule.matchers.forEach { addMatcher(it) }
        binding.cooldownEdit.setText(rule.cooldownMs.toString())
        binding.delayEdit.setText(rule.delayMs.toString())
        binding.screenshotCheckBox.isChecked = rule.actions.any { it is RuleAction.Screenshot }

        binding.addMatcherButton.setOnClickListener { addMatcher(null) }
        binding.saveButton.setOnClickListener { save() }
    }

    private fun setupAppSelection() {
        binding.packageEdit.setText(rule.packageName)
        binding.selectedAppRow.setOnClickListener {
            appPickerLauncher.launch(Intent(this, AppPickerActivity::class.java))
        }
        binding.packageEdit.addTextChangedListener { text ->
            val packageName = text?.toString()?.trim().orEmpty()
            if (selectedApp != null && packageName != selectedApp?.packageName) {
                selectedApp = null
                renderSelectedApp()
            }
        }
        loadSelectedApp(rule.packageName)
    }

    private fun loadSelectedApp(packageName: String) {
        selectedApp = null
        renderSelectedApp()
        if (packageName.isBlank()) return
        scope.launch {
            val app = AppCatalog.findApp(this@RuleActivity, packageName)
            if (binding.packageEdit.text?.toString()?.trim() == packageName) {
                selectedApp = app
                renderSelectedApp()
            }
        }
    }

    private fun renderSelectedApp() {
        val app = selectedApp
        if (app == null) {
            binding.selectedAppIcon.setImageDrawable(null)
            binding.selectedAppName.setText(R.string.select_app)
            binding.selectedAppPackage.text = ""
            return
        }

        binding.selectedAppName.text = app.label
        binding.selectedAppPackage.text = app.packageName
        binding.selectedAppIcon.setImageDrawable(AppCatalog.cachedIcon(app.packageName))
        scope.launch {
            val icon = AppCatalog.loadIcon(this@RuleActivity, app.packageName)
            if (selectedApp?.packageName == app.packageName) {
                binding.selectedAppIcon.setImageDrawable(icon)
            }
        }
    }

    private fun addMatcher(matcher: Matcher?) {
        val itemBinding = ItemMatcherBinding.inflate(layoutInflater, binding.matchersContainer, false)
        itemBinding.fieldSpinner.setSelection(matcher?.field?.ordinal ?: 0)
        itemBinding.opSpinner.setSelection(matcher?.op?.ordinal ?: 0)
        itemBinding.valueEdit.setText(matcher?.value.orEmpty())
        itemBinding.removeButton.setOnClickListener {
            binding.matchersContainer.removeView(itemBinding.root)
            matcherBindings.remove(itemBinding)
        }
        matcherBindings.add(itemBinding)
        binding.matchersContainer.addView(itemBinding.root)
    }

    private fun collectMatchers(): List<Matcher> = matcherBindings.map { item ->
        Matcher(
            field = MatcherField.entries[item.fieldSpinner.selectedItemPosition],
            op = MatchOp.entries[item.opSpinner.selectedItemPosition],
            value = item.valueEdit.text?.toString().orEmpty()
        )
    }

    private fun save() {
        val enteredPackage = binding.packageEdit.text?.toString()?.trim().orEmpty()
        val updated = rule.copy(
            name = binding.nameEdit.text?.toString()?.trim().orEmpty(),
            packageName = enteredPackage,
            matchers = collectMatchers(),
            matchMode = if (binding.modeSpinner.selectedItemPosition == 0) MatchMode.ALL else MatchMode.ANY,
            cooldownMs = binding.cooldownEdit.text?.toString()?.trim()?.toLongOrNull() ?: 3000L,
            delayMs = binding.delayEdit.text?.toString()?.trim()?.toLongOrNull() ?: 0L,
            actions = if (binding.screenshotCheckBox.isChecked) {
                listOf(RuleAction.Screenshot)
            } else {
                emptyList()
            }
        )

        val error = RuleValidator.validate(updated)
        if (error != null) {
            Toast.makeText(this, getString(error.messageRes), Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            repository.upsert(updated)
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RULE_JSON = "rule_json"
    }
}
