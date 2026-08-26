package rs.chimera.android.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.DiagnosticsShareCoordinator
import rs.chimera.android.util.runCatchingPreservingCancellation
import rs.chimera.android.util.runCatchingRecoverable

@Composable
internal fun DiagnosticsShareSetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backend = remember { BackendProvider.provide() }
    val chooserTitle = stringResource(R.string.diagnostics_share_chooser)
    val failureMessage = stringResource(R.string.diagnostics_share_failed)
    var preparing by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !preparing) {
                scope.launch {
                    preparing = true
                    try {
                        runCatchingPreservingCancellation {
                            DiagnosticsShareCoordinator.prepare(context, backend)
                        }.onSuccess { shareIntent ->
                            runCatchingRecoverable {
                                context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
                            }.onFailure {
                                Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure {
                            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        preparing = false
                    }
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.diagnostics_share_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.diagnostics_share_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
