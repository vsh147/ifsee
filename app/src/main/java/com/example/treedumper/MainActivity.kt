package com.example.treedumper

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import com.example.treedumper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private val writer by lazy { DumpWriter.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)

        binding.targetPackageEdit.setText(prefs.targetPackage)
        binding.targetPackageEdit.doAfterTextChanged { text ->
            prefs.targetPackage = text?.toString()?.trim().orEmpty()
        }

        binding.filterEdit.setText(prefs.logFilter)
        binding.filterEdit.doAfterTextChanged { text ->
            prefs.logFilter = text?.toString()?.trim().orEmpty()
        }

        binding.screenshotIntervalEdit.setText(prefs.screenshotIntervalSeconds.toString())
        binding.screenshotIntervalEdit.doAfterTextChanged { text ->
            val seconds = text?.toString()?.trim()?.toIntOrNull()
            if (seconds != null && seconds > 0) {
                prefs.screenshotIntervalSeconds = seconds
            }
        }

        binding.dumpSwitch.isChecked = prefs.dumpEnabled
        binding.dumpSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.dumpEnabled = isChecked
        }

        binding.openSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.shareButton.setOnClickListener { shareDump() }
        binding.clearButton.setOnClickListener { confirmClear() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateFileInfo()
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

    private fun updateFileInfo() {
        val file = writer.getFile()
        if (file.exists()) {
            val size = Formatter.formatFileSize(this, file.length())
            binding.fileInfoText.text = getString(R.string.file_info, file.absolutePath, size)
        } else {
            binding.fileInfoText.text = getString(R.string.file_missing, file.absolutePath)
        }
    }

    private fun shareDump() {
        val file = writer.getFile()
        if (!file.exists()) {
            Toast.makeText(this, R.string.share_no_file, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_title)
            .setMessage(R.string.clear_message)
            .setPositiveButton(R.string.clear_confirm) { _, _ ->
                writer.clear()
                updateFileInfo()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
