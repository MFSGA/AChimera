package rs.chimera.android.ui.metacubex.activity

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.backend.model.RuleSnapshot
import rs.chimera.android.ui.formatRuleDiagnostics
import rs.chimera.android.util.runCatchingPreservingCancellation
import rs.chimera.android.util.toUserVisibleMessage

internal fun AppCompatActivity.showMetaRuleDiagnosticsDialog(
    loadRules: suspend () -> List<RuleSnapshot>,
) {
    lifecycleScope.launch {
        runCatchingPreservingCancellation(loadRules)
            .onSuccess { rules ->
                val message = formatRuleDiagnostics(
                    rules = rules,
                    totalLabel = resources.getQuantityString(
                        R.plurals.rules_diagnostics_count,
                        rules.size,
                        rules.size,
                    ),
                    remainingLabel = { count ->
                        resources.getQuantityString(
                            R.plurals.rules_diagnostics_more,
                            count,
                            count,
                        )
                    },
                )
                AlertDialog.Builder(this@showMetaRuleDiagnosticsDialog)
                    .setTitle(R.string.rules_diagnostics_title)
                    .setMessage(message)
                    .setNegativeButton(R.string.rules_diagnostics_refresh) { _, _ ->
                        showMetaRuleDiagnosticsDialog(loadRules)
                    }.setPositiveButton(android.R.string.ok, null)
                    .show()
            }.onFailure { error ->
                AlertDialog.Builder(this@showMetaRuleDiagnosticsDialog)
                    .setTitle(R.string.rules_diagnostics_title)
                    .setMessage(error.toUserVisibleMessage(this@showMetaRuleDiagnosticsDialog, R.string.profile_unknown_error))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
    }
}
