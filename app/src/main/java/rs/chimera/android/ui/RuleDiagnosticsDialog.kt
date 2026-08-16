package rs.chimera.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.chimera.android.R
import rs.chimera.android.util.runCatchingPreservingCancellation
import rs.chimera.android.backend.model.RuleSnapshot

@Composable
internal fun RuleDiagnosticsDialog(
    onDismiss: () -> Unit,
    onLoad: suspend () -> List<RuleSnapshot>,
) {
    var reloadGeneration by rememberSaveable { mutableIntStateOf(0) }
    var rules by remember { mutableStateOf<List<RuleSnapshot>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val unknownError = stringResource(R.string.profile_unknown_error)

    LaunchedEffect(reloadGeneration) {
        loading = true
        rules = null
        errorMessage = null
        runCatchingPreservingCancellation { onLoad() }
            .onSuccess { result -> rules = result }
            .onFailure { error -> errorMessage = error.message ?: unknownError }
        loading = false
    }

    val totalLabel = stringResource(R.string.rules_diagnostics_count, rules?.size ?: 0)
    val remainingTemplate = stringResource(R.string.rules_diagnostics_more)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_diagnostics_title)) },
        text = {
            when {
                loading -> CircularProgressIndicator()
                errorMessage != null -> Text(errorMessage.orEmpty())
                else -> {
                    val result = rules.orEmpty()
                    SelectionContainer {
                        Text(
                            text = formatRuleDiagnostics(
                                rules = result,
                                totalLabel = totalLabel,
                                remainingLabel = { count ->
                                    remainingTemplate.format(count)
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            if (!loading) {
                TextButton(onClick = { reloadGeneration += 1 }) {
                    Text(stringResource(R.string.rules_diagnostics_refresh))
                }
            }
        },
    )
}
