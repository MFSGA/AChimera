package rs.chimera.android.ui

import rs.chimera.android.backend.model.RuleSnapshot

internal fun formatRuleDiagnostics(
    rules: List<RuleSnapshot>,
    totalLabel: String,
    remainingLabel: (Int) -> String,
    maxEntries: Int = DEFAULT_RULE_DISPLAY_LIMIT,
): String {
    val visibleRules = rules.take(maxEntries)
    return buildString {
        append(totalLabel)
        visibleRules.forEachIndexed { index, rule ->
            append("\n\n")
            append(index + 1)
            append(". ")
            append(rule.type)
            rule.payload.takeIf(String::isNotBlank)?.let { payload ->
                append(" · ")
                append(payload)
            }
            append(" → ")
            append(rule.proxy)
        }
        if (rules.size > visibleRules.size) {
            append("\n\n")
            append(remainingLabel(rules.size - visibleRules.size))
        }
    }
}

private const val DEFAULT_RULE_DISPLAY_LIMIT = 200
