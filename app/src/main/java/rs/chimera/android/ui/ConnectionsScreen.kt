package rs.chimera.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.awaitCancellation
import rs.chimera.android.R
import rs.chimera.android.backend.model.ConnectionSnapshot
import rs.chimera.android.formatSize
import rs.chimera.android.viewmodel.ConnectionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectionsViewModel = viewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var showCloseAllConfirmation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startPolling()
            try {
                awaitCancellation()
            } finally {
                viewModel.stopPolling()
            }
        }
    }

    if (showCloseAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showCloseAllConfirmation = false },
            title = { Text(stringResource(R.string.connections_close_all_title)) },
            text = { Text(stringResource(R.string.connections_close_all_message)) },
            confirmButton = {
                TextButton(
                    enabled = !viewModel.closeAllInProgress,
                    onClick = {
                        showCloseAllConfirmation = false
                        viewModel.closeAllConnections()
                    },
                ) {
                    Text(stringResource(R.string.connections_close_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAllConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.connections_title)) },
                actions = {
                    if (viewModel.connections.isNotEmpty()) {
                        IconButton(
                            enabled = !viewModel.closeAllInProgress &&
                                viewModel.closingConnectionIds.isEmpty(),
                            onClick = { showCloseAllConfirmation = true },
                        ) {
                            if (viewModel.closeAllInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = stringResource(R.string.connections_close_all),
                                )
                            }
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        ConnectionsContent(
            connections = viewModel.connections,
            downloadTotal = viewModel.downloadTotal,
            uploadTotal = viewModel.uploadTotal,
            errorMessage = viewModel.errorMessage,
            closingConnectionIds = viewModel.closingConnectionIds,
            onCloseConnection = viewModel::closeConnection,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

@Composable
private fun ConnectionsContent(
    connections: List<ConnectionSnapshot>,
    downloadTotal: Long,
    uploadTotal: Long,
    errorMessage: String?,
    closingConnectionIds: Set<String>,
    onCloseConnection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.connections_summary),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SummaryMetric(
                            title = stringResource(R.string.connections_count),
                            value = connections.size.toString(),
                        )
                        SummaryMetric(
                            title = stringResource(R.string.stat_download),
                            value = formatSize(downloadTotal),
                            alignEnd = true,
                        )
                        SummaryMetric(
                            title = stringResource(R.string.stat_upload),
                            value = formatSize(uploadTotal),
                            alignEnd = true,
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            item(key = "error") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        if (connections.isEmpty() && errorMessage == null) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.connections_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        itemsIndexed(
            items = connections,
            key = { _, connection -> connection.id },
        ) { _, connection ->
            ConnectionCard(
                connection = connection,
                closing = connection.id in closingConnectionIds,
                onClose = { onCloseConnection(connection.id) },
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    title: String,
    value: String,
    alignEnd: Boolean = false,
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ConnectionCard(
    connection: ConnectionSnapshot,
    closing: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinationHost = connection.host.ifEmpty { connection.destinationIp }
    val destinationIp = connection.destinationIp.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.not_available)
    val sourceIp = connection.sourceIp.takeIf { it.isNotBlank() } ?: "?"
    val sourcePort = connection.sourcePort.takeIf { it.isNotBlank() } ?: "?"
    val destinationPort = connection.destinationPort.takeIf { it.isNotBlank() } ?: "?"
    val network = connection.network.takeIf { it.isNotBlank() } ?: "?"

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = destinationHost.ifEmpty { destinationIp },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = network.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    IconButton(
                        enabled = !closing,
                        onClick = onClose,
                    ) {
                        if (closing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.connections_close),
                            )
                        }
                    }
                }
            }

            Text(
                text = "$destinationIp:$destinationPort",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = stringResource(
                    R.string.connections_source,
                    "$sourceIp:$sourcePort",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            if (connection.chains.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.connections_chain,
                        connection.chains.reversed().joinToString(" -> "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            connection.rule?.takeIf { it.isNotBlank() }?.let { rule ->
                Text(
                    text = stringResource(R.string.connections_rule, rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.connections_download,
                        formatSize(connection.download),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(
                        R.string.connections_upload,
                        formatSize(connection.upload),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
