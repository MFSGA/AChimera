package rs.chimera.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vm.isDownloading) {
                        Text(
                            text = stringResource(id = R.string.profile_downloading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
                                    color = MaterialTheme.colorScheme.outline,
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = remoteProfileUrl,
                        onValueChange = { remoteProfileUrl = it },
                        label = { Text(text = stringResource(id = R.string.profile_import_url)) },
                        placeholder = { Text(text = stringResource(id = R.string.profile_url_hint)) },
                        singleLine = true,
                        enabled = !vm.isDownloading,
                        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = stringResource(id = R.string.profile_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
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

                if (vm.isImporting || vm.isDownloading) {
                    InlineStatusCard(
                        message = stringResource(
                            id = if (vm.isDownloading) {
                                R.string.profile_downloading
                            } else {
                                R.string.profile_importing
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
                onActivate = { vm.activateProfile(profile) },
                onDelete = { vm.deleteProfile(profile) },
            )
        }
    }
}

@Composable
private fun ActiveProfileCard(
    modifier: Modifier = Modifier,
    profile: Profile?,
    savedFilePath: String?,
    isVerifying: Boolean,
    onVerify: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.profile_active_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = profile?.name ?: stringResource(id = R.string.profile_missing),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ProfileKindBadge(
                    label = when (profile?.type) {
                        ProfileType.REMOTE -> stringResource(id = R.string.profile_type_remote)
                        ProfileType.LOCAL -> stringResource(id = R.string.profile_type_local)
                        null -> stringResource(id = R.string.profile_active_badge)
                    },
                    active = profile != null,
                )
            }

            Text(
                text = savedFilePath ?: stringResource(id = R.string.profile_no_saved_path),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            FilledTonalButton(
                enabled = profile != null && !isVerifying,
                onClick = onVerify,
            ) {
                Text(
                    text = stringResource(
                        id = if (isVerifying) R.string.profile_verifying else R.string.profile_verify,
                    ),
                )
            }
        }
    }
}

@Composable
private fun InlineStatusCard(
    message: String,
    isError: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

@Composable
private fun VerificationCard(
    modifier: Modifier = Modifier,
    title: String,
    content: String,
    isSuccess: Boolean,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSuccess) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(14.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState()),
                        color = if (isSuccess) Color.Unspecified else MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        }
    }
}

@Composable
private fun ProfileItem(
    modifier: Modifier = Modifier,
    profile: Profile,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = profile.filePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ProfileKindBadge(
                    label = stringResource(
                        id = if (profile.type == ProfileType.REMOTE) {
                            R.string.profile_type_remote
                        } else {
                            R.string.profile_type_local
                        },
                    ),
                    active = profile.isActive,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (profile.isActive) {
                    Text(
                        text = stringResource(id = R.string.profile_active_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    FilledTonalButton(onClick = onActivate) {
                        Text(text = stringResource(id = R.string.profile_activate))
                    }
                }

                OutlinedButton(onClick = onDelete) {
                    Text(text = stringResource(id = R.string.profile_delete))
                }
            }
        }
    }
}

@Composable
private fun ProfileKindBadge(
    label: String,
    active: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
