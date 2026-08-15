package rs.chimera.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.RuleSnapshot

class RuleDiagnosticsFormatterTest {
    @Test
    fun formatsRulesInEvaluationOrder() {
        val result = formatRuleDiagnostics(
            rules = listOf(
                RuleSnapshot("DOMAIN-SUFFIX", "Proxy", "example.com"),
                RuleSnapshot("MATCH", "DIRECT", ""),
            ),
            totalLabel = "2 rules",
            remainingLabel = { count -> "… $count more" },
        )

        assertEquals(
            "2 rules\n\n1. DOMAIN-SUFFIX · example.com → Proxy\n\n2. MATCH → DIRECT",
            result,
        )
    }

    @Test
    fun limitsLargeRuleSets() {
        val rules = (1..5).map { index ->
            RuleSnapshot("DOMAIN", "Proxy", "host-$index.test")
        }

        val result = formatRuleDiagnostics(
            rules = rules,
            totalLabel = "5 rules",
            remainingLabel = { count -> "… $count more" },
            maxEntries = 2,
        )

        assertTrue(result.contains("5 rules"))
        assertTrue(result.contains("host-1.test"))
        assertTrue(result.contains("host-2.test"))
        assertFalse(result.contains("host-3.test"))
        assertTrue(result.endsWith("… 3 more"))
    }
}
