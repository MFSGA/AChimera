package rs.chimera.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.chimera.android.R
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType
import rs.chimera.android.ui.components.ProfileCard
import rs.chimera.android.ui.components.RemoteProfileDialog
import rs.chimera.android.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(),
) {
    val context = LocalContext.current
    val defaultRemoteUrl = stringResource(id = R.string.profile_default_test_url)
    var localProfileName by remember { mutableStateOf("") }
    var remoteProfileName by remember { mutableStateOf("") }
    var remoteProfileUrl by remember { mutableStateOf(defaultRemoteUrl) }
    var remoteAutoUpdate by remember { mutableStateOf(false) }
    var remoteUserAgent by remember { mutableStateOf("") }
    var remoteProxyUrl by remember { mutableStateOf("") }
    var showLocalDialog by remember { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var wasDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadSavedFilePath()
    }

    LaunchedEffect(vm.isDownloading) {
        if (vm.isDownloading) {
            wasDownloading = true
        } else if (wasDownloading && showRemoteDialog) {
            showRemoteDialog = false
            remoteProfileName = ""
            remoteProfileUrl = defaultRemoteUrl
            remoteAutoUpdate = false
            remoteUserAgent = ""
            remoteProxyUrl = ""
            wasDownloading = false
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            vm.selectFile(context, it)
            showLocalDialog = true
        }
    }

    if (showLocalDialog && vm.selectedFile != null) {
        AlertDialog(
            onDismissRequest = {
                showLocalDialog = false
                localProfileName = ""
                vm.clearSelection()
            },
            title = { Text(text = stringResource(id = R.string.profile_local_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = localProfileName,
                    onValueChange = { localProfileName = it },
                    label = { Text(text = stringResource(id = R.string.profile_name_label)) },
                    placeholder = {
                        Text(
                            text = vm.selectedFile?.name ?: stringResource(id = R.string.profile_title),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = vm.selectedFile?.uri ?: return@TextButton
                        vm.saveFileToAppDirectory(
                            context = context,
                            uri = uri,
                            profileName = localProfileName.ifBlank { null },
                        )
                        showLocalDialog = false
                        localProfileName = ""
                    },
                ) {
                    Text(text = stringResource(id = R.string.profile_save_file))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLocalDialog = false
                        localProfileName = ""
                        vm.clearSelection()
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(text = stringResource(id = R.string.about_title)) },
            text = { Text(text = stringResource(id = R.string.profile_known_issues)) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
        )
    }

    if (showRemoteDialog) {
        RemoteProfileDialog(
            profileName = remoteProfileName,
            profileUrl = remoteProfileUrl,
            autoUpdate = remoteAutoUpdate,
            userAgent = remoteUserAgent,
            proxyUrl = remoteProxyUrl,
            isDownloading = vm.isDownloading,
            downloadProgress = vm.downloadProgress,
            onProfileNameChange = { remoteProfileName = it },
            onProfileUrlChange = { remoteProfileUrl = it },
            onAutoUpdateChange = { remoteAutoUpdate = it },
            onUserAgentChange = { remoteUserAgent = it },
            onProxyUrlChange = { remoteProxyUrl = it },
            onConfirm = {
                vm.addRemoteProfile(
                    context = context,
                    profileName = remoteProfileName.ifBlank { null },
                    url = remoteProfileUrl.trim(),
                    autoUpdate = remoteAutoUpdate,
                    userAgent = remoteUserAgent.ifBlank { null },
                    proxyUrl = remoteProxyUrl.ifBlank { null },
                )
            },
            onDismiss = {
                showRemoteDialog = false
                remoteProfileName = ""
                remoteProfileUrl = defaultRemoteUrl
                remoteAutoUpdate = false
                remoteUserAgent = ""
                remoteProxyUrl = ""
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.profile_screen),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.profile_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(id = R.string.action_about),
                        )
                    }
                }
            }
        }

        item {
            ActiveProfileCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                profile = vm.activeProfile,
                savedFilePath = vm.savedFilePath,
                isVerifying = vm.isVerifying,
                onVerify = { vm.verifyActiveProfile(context) },
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = !vm.isProfileOperationInProgress,
                    onClick = { launcher.launch(arrayOf("*/*")) },
                ) {
                    Text(text = stringResource(id = R.string.profile_local_button))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !vm.isProfileOperationInProgress,
                    onClick = { showRemoteDialog = true },
                ) {
                    Text(text = stringResource(id = R.string.profile_remote_button))
                }
            }
        }

        if (vm.profiles.any { it.type == ProfileType.REMOTE }) {
            item {
                FilledTonalButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = !vm.isProfileOperationInProgress,
                    onClick = { vm.updateAllRemoteProfiles(context) },
                ) {
                    Text(
                        text = stringResource(
                            id = if (vm.isRefreshingRemoteProfiles) {
                                R.string.profile_refreshing
                            } else {
                                R.string.profile_update_all_remote
                            },
                        ),
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                vm.statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    InlineStatusCard(
                        message = message,
                        isError = message.contains("failed", ignoreCase = true),
                    )
                }

                if (vm.isImporting || vm.isDownloading || vm.isRefreshingRemoteProfiles) {
                    InlineStatusCard(
                        message = stringResource(
                            id = when {
                                vm.isRefreshingRemoteProfiles -> R.string.profile_refreshing
                                vm.isDownloading -> R.string.profile_downloading
                                else -> R.string.profile_importing
                            },
                        ),
                        isError = false,
                    )
                }
            }
        }

        vm.verificationResult?.takeIf { it.isNotBlank() }?.let { result ->
            item {
                VerificationCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(
                        id = if (vm.verificationSucceeded == true) {
                            R.string.profile_verification_title_success
                        } else {
                            R.string.profile_verification_title_failure
                        },
                    ),
                    content = result,
                    isSuccess = vm.verificationSucceeded == true,
                    onDismiss = { vm.clearVerificationResult() },
                )
            }
        }

        if (vm.profiles.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.profile_list_title, vm.profiles.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        items(vm.profiles, key = { it.id }) { profile ->
            ProfileItem(
                modifier = Modifier.padding(horizontal = 16.dp),
                profile = profile,
                mutationEnabled = !vm.isProfileOperationInProgress,
                onActivate = { vm.activateProfile(context, profile) },
                onDelete = { vm.deleteProfile(context, profile) },
                onRename = { vm.renameProfile(context, profile, it) },
                onUpdate = if (profile.type == ProfileType.REMOTE) {
                    { vm.updateRemoteProfile(context, profile) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ProfileItem(
    modifier: Modifier = Modifier,
    profile: Profile,
    mutationEnabled: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onUpdate: (() -> Unit)?,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember(profile.id, profile.name) { mutableStateOf(profile.name) }
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                renameValue = profile.name
            },
            title = { Text(text = stringResource(id = R.string.profile_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text(text = stringResource(id = R.string.profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = mutationEnabled,
                    onClick = {
                        onRename(renameValue)
                        showRenameDialog = false
                    },
                ) {
                    Text(text = stringResource(id = R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        renameValue = profile.name
                    },
                ) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            },
        )
    }

    ProfileCard(
        modifier = modifier,
        profile = profile,
        mutationEnabled = mutationEnabled,
        onActivate = onActivate,
        onDelete = onDelete,
        onRenameRequest = { showRenameDialog = true },
        onUpdate = onUpdate,
    )
}
