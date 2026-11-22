package com.example.treedumper

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.treedumper.model.Rule
import com.example.treedumper.model.RuleValidator
import com.example.treedumper.model.RulesFile
import com.example.treedumper.model.RulesJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicReference

private val Context.rulesDataStore: DataStore<Preferences> by preferencesDataStore(name = "rules")

class RulesRepository(private val context: Context) {

    private val dataStore = context.applicationContext.rulesDataStore
    private val json = RulesJson.json
    private val lastBackedUp = AtomicReference<String?>(null)

    val rulesFlow: Flow<List<Rule>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_RULES_JSON]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                decode(raw).rules
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse rules JSON, returning empty list", e)
                backupOnce(raw)
                emptyList()
            }
        }
    }.distinctUntilChanged()

    suspend fun save(rules: List<Rule>) {
        rules.forEach { validateOrThrow(it) }
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(KEY_RULES_JSON, encode(RulesFile(rules = rules)))
            }.toPreferences()
        }
    }

    suspend fun upsert(rule: Rule) {
        validateOrThrow(rule)
        dataStore.updateData { prefs ->
            val current = decodeOrEmpty(prefs[KEY_RULES_JSON])
            val updated = if (current.any { it.id == rule.id }) {
                current.map { if (it.id == rule.id) rule else it }
            } else {
                current + rule
            }
            prefs.toMutablePreferences().apply {
                set(KEY_RULES_JSON, encode(RulesFile(rules = updated)))
            }.toPreferences()
        }
    }

    suspend fun delete(id: String) {
        dataStore.updateData { prefs ->
            val current = decodeOrEmpty(prefs[KEY_RULES_JSON])
            val updated = current.filterNot { it.id == id }
            prefs.toMutablePreferences().apply {
                set(KEY_RULES_JSON, encode(RulesFile(rules = updated)))
            }.toPreferences()
        }
    }

    private fun validateOrThrow(rule: Rule) {
        val error = RuleValidator.validate(rule)
        require(error == null) { "Invalid rule: ${error?.name}" }
    }

    private fun decode(raw: String): RulesFile {
        val cleaned = json.parseToJsonElement(raw).cleanUnknownActions()
        return json.decodeFromJsonElement(RulesFile.serializer(), cleaned)
    }

    private fun decodeOrEmpty(raw: String?): List<Rule> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            decode(raw).rules
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse rules JSON in write path, ignoring", e)
            emptyList()
        }
    }

    private fun encode(file: RulesFile): String = json.encodeToString(file)

    private suspend fun backupOnce(raw: String) {
        if (lastBackedUp.getAndSet(raw) == raw) return
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply { set(KEY_BACKUP, raw) }.toPreferences()
        }
    }

    private fun kotlinx.serialization.json.JsonElement.cleanUnknownActions(): kotlinx.serialization.json.JsonElement =
        when (this) {
            is JsonObject -> JsonObject(mapValues { (key, value) ->
                if (key == "actions" && value is JsonArray) {
                    JsonArray(value.filter { item ->
                        val type = (item as? JsonObject)?.get("type") as? JsonPrimitive
                        type?.content == "SCREENSHOT"
                    }.map { it.cleanUnknownActions() })
                } else {
                    value.cleanUnknownActions()
                }
            })
            is JsonArray -> JsonArray(map { it.cleanUnknownActions() })
            else -> this
        }

    private companion object {
        const val TAG = "RulesRepository"
        val KEY_RULES_JSON = stringPreferencesKey("rules_json")
        val KEY_BACKUP = stringPreferencesKey("rules_json_backup")
    }
}
