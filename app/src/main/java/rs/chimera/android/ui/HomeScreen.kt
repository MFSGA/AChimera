package rs.chimera.android.ui

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import rs.chimera.android.R
import rs.chimera.android.formatSize
import rs.chimera.android.ui.components.StatsCard
import rs.chimera.android.ui.components.TextInfoDialog
import rs.chimera.android.viewmodel.HomeViewModel
import uniffi.chimera_ffi.MemoryResponse

@Destination<RootGraph>(start = true)
@Composable
fun HomeScreen(
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    HomeScreen(
        modifier = modifier,
        viewModel = viewModel,
        onConnectionsClick = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onConnectionsClick: () -> Unit = {},
    onSwitchUi: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val vpnPermissionDeniedMessage = stringResource(R.string.service_vpn_permission_denied)
    var showRestartDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.startVpnAfterPermission()
            } else {
                viewModel.reportError(vpnPermissionDeniedMessage)
            }
        },
    )

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.restart_confirm_title)) },
            text = { Text(stringResource(R.string.restart_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        val intent =
                            context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (intent != null) {
                            context.startActivity(intent)
                        }
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showInfoDialog) {
        TextInfoDialog(
            title = stringResource(R.string.about_title),
            content = stringResource(R.string.about_known_issues),
            onDismiss = { showInfoDialog = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    if (onSwitchUi != null) {
                        IconButton(onClick = onSwitchUi) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = stringResource(R.string.switch_to_metacubex_ui),
                            )
                        }
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.action_about),
                        )
                    }
                    IconButton(onClick = { showRestartDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_restart),
                        )
                    }
                },
                windowInsets = WindowInsets(),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        val memory = viewModel.memoryUsage
        val connections = viewModel.connectionCount
        val download = viewModel.totalDownload
        val upload = viewModel.totalUpload
        val isVpnRunning = viewModel.isVpnRunning
        val errorMessage = viewModel.errorMessage

        OverviewTab(
            memory = memory,
            connections = connections,
            download = download,
            upload = upload,
            isVpnRunning = isVpnRunning,
            errorMessage = errorMessage,
            onDismissError = viewModel::clearError,
            onConnectionsClick = onConnectionsClick,
            onVpnToggle = {
                if (isVpnRunning) {
                    viewModel.stopVpn()
                } else {
                    viewModel.startVpn(vpnPermissionLauncher)
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

@Composable
private fun HomeHeroCard(
    isVpnRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    val gradient = if (isVpnRunning) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
                MaterialTheme.colorScheme.secondary,
            ),
        )
    }
    val foreground = Color.White

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, foreground.copy(alpha = 0.22f)),
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .background(gradient, shape)
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 10.dp)
                    .size(96.dp)
                    .background(foreground.copy(alpha = 0.10f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(42.dp)
                    .background(foreground.copy(alpha = 0.12f), CircleShape),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                StatusPill(
                    label = stringResource(R.string.stat_vpn),
                    active = isVpnRunning,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = foreground,
                    )
                    Text(
                        text = if (isVpnRunning) {
                            stringResource(R.string.stat_vpn_hint_stop)
                        } else {
                            stringResource(R.string.stat_vpn_hint_start)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = foreground.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isVpnRunning) {
                            stringResource(R.string.stat_vpn_running)
                        } else {
                            stringResource(R.string.stat_vpn_stopped)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = foreground,
                    )
                    FilledTonalButton(
                        onClick = onClick,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = if (isVpnRunning) {
                                stringResource(R.string.service_stop_action)
                            } else {
                                stringResource(R.string.service_start_action)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (active) Color(0xFFB9FFDA) else Color(0xFFFFD4C7),
                        shape = CircleShape,
                    ),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun OverviewTab(
    memory: MemoryResponse?,
    connections: Int,
    download: Long,
    upload: Long,
    isVpnRunning: Boolean,
    errorMessage: String?,
    onVpnToggle: () -> Unit,
    onDismissError: () -> Unit,
    onConnectionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") {
            HomeHeroCard(
                isVpnRunning = isVpnRunning,
                onClick = onVpnToggle,
            )
        }

        if (errorMessage != null) {
            item(key = "error") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_error_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onDismissError) {
                            Text(
                                text = stringResource(R.string.panel_dismiss),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }

        item(key = "quick-stats") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatsCard(
                    title = stringResource(R.string.stat_memory),
                    value = memory?.let { formatSize(it.inuse) } ?: stringResource(R.string.not_available),
                    subtitle = memory?.let {
                        stringResource(R.string.stat_memory_limit, formatSize(it.oslimit))
                    } ?: stringResource(R.string.refreshing),
                    modifier = Modifier.weight(1f),
                )
                StatsCard(
                    title = stringResource(R.string.stat_connections),
                    value = connections.toString(),
                    subtitle = if (connections > 0) {
                        stringResource(R.string.stat_connections_ongoing)
                    } else {
                        stringResource(R.string.stat_connections_none)
                    },
                    modifier = Modifier.weight(1f),
                    onClick = onConnectionsClick,
                )
            }
        }

        item(key = "bandwidth") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatsCard(
                    title = stringResource(R.string.stat_download),
                    value = formatSize(download),
                    modifier = Modifier.weight(1f),
                )
                StatsCard(
                    title = stringResource(R.string.stat_upload),
                    value = formatSize(upload),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
