package io.github.vsh147.ifsee.model

import io.github.vsh147.ifsee.R

enum class RuleValidationError(val messageRes: Int) {
    EMPTY_NAME(R.string.error_empty_name),
    EMPTY_PACKAGE(R.string.error_empty_package),
    NO_MATCHERS(R.string.error_no_matchers),
    EMPTY_VALUE(R.string.error_empty_value),
    BAD_REGEX(R.string.error_bad_regex),
    NEGATIVE_DELAY(R.string.error_negative_delay),
    NO_ACTIONS(R.string.error_no_actions)
}

object RuleValidator {

    fun validate(rule: Rule): RuleValidationError? {
        if (rule.name.isBlank()) return RuleValidationError.EMPTY_NAME
        if (rule.packageName.isBlank()) return RuleValidationError.EMPTY_PACKAGE
        if (rule.matchers.isEmpty()) return RuleValidationError.NO_MATCHERS

        for (matcher in rule.matchers) {
            if (matcher.value.isEmpty()) return RuleValidationError.EMPTY_VALUE
            if (matcher.op == MatchOp.REGEX && compile(matcher) == null) {
                return RuleValidationError.BAD_REGEX
            }
        }

        if (rule.cooldownMs < 0 || rule.delayMs < 0) return RuleValidationError.NEGATIVE_DELAY
        if (rule.actions.isEmpty()) return RuleValidationError.NO_ACTIONS
        return null
    }

    fun compile(matcher: Matcher): Regex? =
        if (matcher.op == MatchOp.REGEX) {
            try {
                Regex(matcher.value)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
}
