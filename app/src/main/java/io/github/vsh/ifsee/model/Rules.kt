package io.github.vsh.ifsee.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

object RulesJson {
    val json = Json { ignoreUnknownKeys = true }
}

@Serializable
data class RulesFile(
    val version: Int = 1,
    val rules: List<Rule> = emptyList()
)

@Serializable
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val packageName: String,
    val matchers: List<Matcher> = emptyList(),
    val matchMode: MatchMode = MatchMode.ALL,
    val cooldownMs: Long = 3000L,
    val delayMs: Long = 0L,
    val actions: List<RuleAction> = listOf(RuleAction.Screenshot)
)

@Serializable
data class Matcher(
    val field: MatcherField,
    val op: MatchOp,
    val value: String
)

@Serializable
enum class MatchMode {
    ALL,
    ANY
}

@Serializable
enum class MatcherField {
    TEXT,
    DESCRIPTION,
    VIEW_ID
}

@Serializable
enum class MatchOp {
    CONTAINS,
    EQUALS,
    REGEX
}

@Serializable
sealed interface RuleAction {
    @Serializable
    @SerialName("SCREENSHOT")
    data object Screenshot : RuleAction
}
