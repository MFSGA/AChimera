package rs.chimera.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.chimera.android.R
import rs.chimera.android.viewmodel.ListenerPortInputPolicy

@Composable
internal fun ListenerPortsDialog(
    currentMixedPort: UShort,
    currentHttpPort: UShort?,
    currentSocksPort: UShort?,
    onDismiss: () -> Unit,
    onConfirm: (UShort, UShort?, UShort?) -> Unit,
) {
    var mixedPortText by remember(currentMixedPort) { mutableStateOf(currentMixedPort.toString()) }
    var httpPortText by remember(currentHttpPort) { mutableStateOf(currentHttpPort?.toString().orEmpty()) }
    var socksPortText by remember(currentSocksPort) { mutableStateOf(currentSocksPort?.toString().orEmpty()) }

    val mixedPort = ListenerPortInputPolicy.parseRequired(mixedPortText)
    val httpPort = ListenerPortInputPolicy.parseOptional(httpPortText)
    val socksPort = ListenerPortInputPolicy.parseOptional(socksPortText)
    val mixedPortValid = mixedPort != null
    val httpPortValid = ListenerPortInputPolicy.isOptionalValid(httpPortText)
    val socksPortValid = ListenerPortInputPolicy.isOptionalValid(socksPortText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_listener_ports)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = mixedPortText,
                    onValueChange = { mixedPortText = it },
                    label = { Text(stringResource(R.string.settings_mixed_port)) },
                    isError = !mixedPortValid,
                    supportingText = if (!mixedPortValid) {
                        { Text(stringResource(R.string.settings_port_invalid)) }
                    } else {
                        null
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = httpPortText,
                    onValueChange = { httpPortText = it },
                    label = { Text(stringResource(R.string.settings_http_port)) },
                    isError = !httpPortValid,
                    supportingText = if (!httpPortValid) {
                        { Text(stringResource(R.string.settings_port_invalid)) }
                    } else {
                        null
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = socksPortText,
                    onValueChange = { socksPortText = it },
                    label = { Text(stringResource(R.string.settings_socks_port)) },
                    isError = !socksPortValid,
                    supportingText = if (!socksPortValid) {
                        { Text(stringResource(R.string.settings_port_invalid)) }
                    } else {
                        null
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = mixedPortValid && httpPortValid && socksPortValid,
                onClick = {
                    onConfirm(
                        requireNotNull(mixedPort),
                        httpPort,
                        socksPort,
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
