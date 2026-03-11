package rs.chimera.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.chimera.android.R
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType
import rs.chimera.android.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(),
) {
    val context = LocalContext.current
    // todo: delete the dev defaultRemoteUrl
    val defaultRemoteUrl = stringResource(id = R.string.profile_default_test_url)
    var localProfileName by remember { mutableStateOf("") }
    var remoteProfileName by remember { mutableStateOf("") }
    var remoteProfileUrl by remember { mutableStateOf(defaultRemoteUrl) }
    var showLocalDialog by remember { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }
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
                    placeholder = { Text(text = vm.selectedFile?.name ?: stringResource(id = R.string.profile_title)) },
                    singleLine = true,
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

    if (showRemoteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!vm.isDownloading) {
                    showRemoteDialog = false
                    remoteProfileName = ""
                    remoteProfileUrl = defaultRemoteUrl
                }
            },
            title = { Text(text = stringResource(id = R.string.profile_remote_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.isDownloading) {
                        Text(text = stringResource(id = R.string.profile_downloading))
                        vm.downloadProgress?.let { progress ->
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
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    OutlinedTextField(
                        value = remoteProfileName,
                        onValueChange = { remoteProfileName = it },
                        label = { Text(text = stringResource(id = R.string.profile_name_label)) },
                        placeholder = { Text(text = stringResource(id = R.string.profile_remote_name_hint)) },
                        singleLine = true,
                        enabled = !vm.isDownloading,
                    )
                    OutlinedTextField(
                        value = remoteProfileUrl,
                        onValueChange = { remoteProfileUrl = it },
                        label = { Text(text = stringResource(id = R.string.profile_import_url)) },
                        placeholder = { Text(text = stringResource(id = R.string.profile_url_hint)) },
                        singleLine = true,
                        enabled = !vm.isDownloading,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = remoteProfileUrl.isNotBlank() && !vm.isDownloading,
                    onClick = {
                        vm.addRemoteProfile(
                            context = context,
                            profileName = remoteProfileName.ifBlank { null },
                            url = remoteProfileUrl.trim(),
                        )
                    },
                ) {
                    Text(text = stringResource(id = R.string.profile_download_file))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !vm.isDownloading,
                    onClick = {
                        showRemoteDialog = false
                        remoteProfileName = ""
                        remoteProfileUrl = defaultRemoteUrl
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.profile_active_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = vm.activeProfile?.name ?: stringResource(id = R.string.profile_missing),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = vm.savedFilePath ?: stringResource(id = R.string.profile_no_saved_path),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { launcher.launch(arrayOf("*/*")) },
            ) {
                Text(text = stringResource(id = R.string.profile_local_button))
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { showRemoteDialog = true },
            ) {
                Text(text = stringResource(id = R.string.profile_remote_button))
            }
        }

        if (vm.isImporting || vm.isDownloading) {
            Text(
                text = stringResource(
                    id = if (vm.isDownloading) R.string.profile_downloading else R.string.profile_importing,
                ),
            )
        }

        vm.statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (vm.profiles.isNotEmpty()) {
            Text(
                text = stringResource(id = R.string.profile_list_title, vm.profiles.size),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        vm.profiles.forEach { profile ->
            ProfileItem(
                profile = profile,
                onActivate = { vm.activateProfile(profile) },
                onDelete = { vm.deleteProfile(profile) },
            )
        }
    }
}

@Composable
private fun ProfileItem(
    profile: Profile,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = profile.filePath,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    id = if (profile.type == ProfileType.REMOTE) R.string.profile_type_remote else R.string.profile_type_local,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!profile.isActive) {
                    OutlinedButton(onClick = onActivate) {
                        Text(text = stringResource(id = R.string.profile_activate))
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.profile_active_badge),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(onClick = onDelete) {
                    Text(text = stringResource(id = R.string.profile_delete))
                }
            }
        }
    }
}
