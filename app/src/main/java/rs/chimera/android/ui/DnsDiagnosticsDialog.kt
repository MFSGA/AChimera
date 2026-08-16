package rs.chimera.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.util.runCatchingPreservingCancellation

@Composable
internal fun DnsDiagnosticsDialog(
    onDismiss: () -> Unit,
    onQuery: suspend (String, String) -> String,
) {
    var name by rememberSaveable { mutableStateOf("example.com") }
    var recordType by rememberSaveable { mutableStateOf("A") }
    var result by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var querying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val unknownError = stringResource(R.string.profile_unknown_error)

    AlertDialog(
        onDismissRequest = { if (!querying) onDismiss() },
        title = { Text(stringResource(R.string.dns_diagnostics_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.dns_diagnostics_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !querying,
                    singleLine = true,
                    label = { Text(stringResource(R.string.dns_query_name)) },
                )
                OutlinedTextField(
                    value = recordType,
                    onValueChange = { recordType = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !querying,
                    singleLine = true,
                    label = { Text(stringResource(R.string.dns_record_type)) },
                    supportingText = { Text(stringResource(R.string.dns_record_type_hint)) },
                )
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                result?.let { queryResult ->
                    SelectionContainer {
                        Text(
                            text = queryResult,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !querying && name.isNotBlank() && recordType.isNotBlank(),
                onClick = {
                    coroutineScope.launch {
                        querying = true
                        result = null
                        errorMessage = null
                        runCatchingPreservingCancellation {
                            onQuery(name.trim(), recordType.trim().uppercase())
                        }.onSuccess { response ->
                            result = response
                        }.onFailure { error ->
                            errorMessage = error.message ?: unknownError
                        }
                        querying = false
                    }
                },
            ) {
                if (querying) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.dns_query_action))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !querying,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
