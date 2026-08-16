package rs.chimera.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.chimera.android.R
import uniffi.chimera_ffi.DownloadProgress

@Composable
internal fun RemoteProfileDialog(
    profileName: String,
    profileUrl: String,
    autoUpdate: Boolean,
    userAgent: String,
    proxyUrl: String,
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    onProfileNameChange: (String) -> Unit,
    onProfileUrlChange: (String) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onProxyUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = { Text(text = stringResource(id = R.string.profile_remote_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isDownloading) {
                    Text(
                        text = stringResource(id = R.string.profile_downloading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    downloadProgress?.let { progress ->
                        if (progress.total > 0uL) {
                            val ratio = progress.downloaded.toFloat() / progress.total.toFloat()
                            LinearProgressIndicator(
                                progress = { ratio.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.profile_download_progress,
                                    progress.downloaded.toString(),
                                    progress.total.toString(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                OutlinedTextField(
                    value = profileName,
                    onValueChange = onProfileNameChange,
                    label = { Text(text = stringResource(id = R.string.profile_name_label)) },
                    placeholder = { Text(text = stringResource(id = R.string.profile_remote_name_hint)) },
                    singleLine = true,
                    enabled = !isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = profileUrl,
                    onValueChange = onProfileUrlChange,
                    label = { Text(text = stringResource(id = R.string.profile_import_url)) },
                    placeholder = { Text(text = stringResource(id = R.string.profile_url_hint)) },
                    singleLine = true,
                    enabled = !isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = onUserAgentChange,
                    label = { Text(text = stringResource(id = R.string.profile_user_agent_label)) },
                    placeholder = { Text(text = stringResource(id = R.string.profile_user_agent_hint)) },
                    singleLine = true,
                    enabled = !isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = proxyUrl,
                    onValueChange = onProxyUrlChange,
                    label = { Text(text = stringResource(id = R.string.profile_proxy_label)) },
                    placeholder = { Text(text = stringResource(id = R.string.profile_proxy_hint)) },
                    singleLine = true,
                    enabled = !isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = autoUpdate,
                        onCheckedChange = onAutoUpdateChange,
                        enabled = !isDownloading,
                    )
                    Text(
                        text = stringResource(id = R.string.profile_auto_update),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = profileUrl.isNotBlank() && !isDownloading,
                onClick = onConfirm,
            ) {
                Text(text = stringResource(id = R.string.profile_download_file))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isDownloading,
                onClick = onDismiss,
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
    )
}
