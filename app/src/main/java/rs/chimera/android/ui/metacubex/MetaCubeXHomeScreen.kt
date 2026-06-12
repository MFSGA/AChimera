package rs.chimera.android.ui.metacubex

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import rs.chimera.android.R
import rs.chimera.android.formatSize
import rs.chimera.android.ui.components.TextInfoDialog
import rs.chimera.android.viewmodel.HomeViewModel
import uniffi.chimera_ffi.Mode

@Composable
fun MetaCubeXHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onProxyClick: () -> Unit,
    onProfilesClick: () -> Unit,
    onLogsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchUi: () -> Unit,
) {
    val context = LocalContext.current
    var showRestartDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.startVpn()
            }
        },
    )

    val isVpnRunning by remember { derivedStateOf { viewModel.isVpnRunning } }
    val download by remember { derivedStateOf { viewModel.totalDownload } }
    val upload by remember { derivedStateOf { viewModel.totalUpload } }
    val currentMode by remember { derivedStateOf { viewModel.currentMode } }
    val profilePath by viewModel.profilePath.observeAsState()
    val profileName = remember(profilePath) {
        profilePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).name }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.restart_confirm_title)) },
            text = { Text(stringResource(R.string.restart_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        android.os.Process.killProcess(android.os.Process.myPid())
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

    if (showAboutDialog) {
        TextInfoDialog(
            title = stringResource(R.string.about_title),
            content = stringResource(R.string.about_known_issues),
            onDismiss = { showAboutDialog = false },
        )
    }

    if (showHelpDialog) {
        TextInfoDialog(
            title = stringResource(R.string.cmfa_help_title),
            content = stringResource(R.string.cmfa_help_body),
            onDismiss = { showHelpDialog = false },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "banner") {
            MetaHeader()
        }

        item(key = "status") {
            MetaLargeActionCard(
                title = if (isVpnRunning) {
                    stringResource(R.string.stat_vpn_running)
                } else {
                    stringResource(R.string.stat_vpn_stopped)
                },
                subtitle = if (isVpnRunning) {
                    stringResource(R.string.cmfa_traffic_forwarded, formatSize(download + upload))
                } else {
                    stringResource(R.string.stat_vpn_hint_start)
                },
                icon = if (isVpnRunning) Icons.Filled.CheckCircle else Icons.Filled.NotInterested,
                emphasized = true,
                running = isVpnRunning,
                onClick = {
                    if (isVpnRunning) {
                        viewModel.stopVpn()
                        showRestartDialog = true
                    } else {
                        viewModel.startVpn(vpnPermissionLauncher)
                    }
                },
            )
        }

        if (isVpnRunning) {
            item(key = "proxy") {
                MetaLargeActionCard(
                    title = stringResource(R.string.panel_screen),
                    subtitle = modeLabel(currentMode),
                    icon = Icons.Filled.Apps,
                    onClick = onProxyClick,
                )
            }
        }

        item(key = "profiles") {
            MetaLargeActionCard(
                title = stringResource(R.string.profile_screen),
                subtitle = profileName?.let {
                    stringResource(R.string.cmfa_profile_activated, it)
                } ?: stringResource(R.string.profile_missing),
                icon = Icons.Filled.ViewList,
                onClick = onProfilesClick,
            )
        }

        item(key = "logs") {
            MetaActionLabel(
                title = stringResource(R.string.logs_screen),
                icon = Icons.Filled.Assignment,
                onClick = onLogsClick,
            )
        }

        item(key = "settings") {
            MetaActionLabel(
                title = stringResource(R.string.settings_screen),
                icon = Icons.Filled.Settings,
                onClick = onSettingsClick,
            )
        }

        item(key = "switch_ui") {
            MetaActionLabel(
                title = stringResource(R.string.switch_to_watfaq_ui),
                icon = Icons.Filled.SwapHoriz,
                onClick = onSwitchUi,
            )
        }

        item(key = "help") {
            MetaActionLabel(
                title = stringResource(R.string.cmfa_help_title),
                icon = Icons.Filled.Help,
                onClick = { showHelpDialog = true },
            )
        }

        item(key = "about") {
            MetaActionLabel(
                title = stringResource(R.string.about_title),
                icon = Icons.Filled.Info,
                onClick = { showAboutDialog = true },
            )
        }
    }
}

@Composable
private fun MetaHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetaLargeActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    running: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val containerColor = when {
        emphasized && running -> MaterialTheme.colorScheme.primary
        emphasized -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (emphasized) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val gradient = if (emphasized) {
        Brush.horizontalGradient(
            colors = listOf(
                containerColor,
                containerColor.copy(alpha = 0.82f),
            ),
        )
    } else {
        null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = shape,
        border = if (emphasized) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (emphasized) 3.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (gradient != null) {
                        Modifier.background(gradient)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MetaActionLabel(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun modeLabel(mode: Mode): String {
    return when (mode) {
        Mode.DIRECT -> stringResource(R.string.proxy_mode_direct)
        Mode.GLOBAL -> stringResource(R.string.proxy_mode_global)
        Mode.RULE -> stringResource(R.string.proxy_mode_rule)
    }
}
