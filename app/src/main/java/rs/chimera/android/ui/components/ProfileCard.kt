package rs.chimera.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rs.chimera.android.R
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType
import rs.chimera.android.ui.ProfileAutoUpdateStatus
import rs.chimera.android.ui.ProfileKindBadge
import rs.chimera.android.ui.format
import rs.chimera.android.ui.profileDetailsText
import rs.chimera.android.ui.resolveProfileAutoUpdatePresentation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ProfileCard(
    modifier: Modifier = Modifier,
    profile: Profile,
    mutationEnabled: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onRenameRequest: () -> Unit,
    onUpdate: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val context = LocalContext.current
    val autoUpdatePresentation = resolveProfileAutoUpdatePresentation(
        autoUpdate = profile.autoUpdate,
        lastAttempt = profile.lastAutoUpdateAttempt,
        failureCount = profile.autoUpdateFailures,
        nextAttemptAt = profile.nextAutoUpdateAt,
        error = profile.lastAutoUpdateError,
    )

    if (showDetailsDialog) {
        TextInfoDialog(
            title = profile.name,
            content = profile.profileDetailsText(context),
            onDismiss = { showDetailsDialog = false },
        )
    }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (profile.type == ProfileType.REMOTE) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = dateFormatter.format(Date(profile.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = profile.filePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (profile.type == ProfileType.REMOTE && profile.lastUpdated != null) {
                        Text(
                            text = stringResource(
                                id = R.string.profile_last_updated,
                                dateFormatter.format(Date(profile.lastUpdated)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    autoUpdatePresentation?.let { presentation ->
                        Text(
                            text = presentation.format(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (presentation.status == ProfileAutoUpdateStatus.RETRY) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (!profile.isActive) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(id = R.string.profile_activate)) },
                                enabled = mutationEnabled,
                                onClick = {
                                    onActivate()
                                    menuExpanded = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.profile_details)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showDetailsDialog = true
                                menuExpanded = false
                            },
                        )
                        if (profile.type == ProfileType.REMOTE && onEditRemoteSettings != null) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(id = R.string.profile_edit_settings)) },
                                enabled = mutationEnabled,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onEditRemoteSettings()
                                    menuExpanded = false
                                },
                            )
                        }
                        if (profile.type == ProfileType.REMOTE && onUpdate != null) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(id = R.string.profile_update)) },
                                enabled = mutationEnabled,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onUpdate()
                                    menuExpanded = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.profile_rename)) },
                            enabled = mutationEnabled,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onRenameRequest()
                                menuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.profile_delete)) },
                            enabled = mutationEnabled,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDelete()
                                menuExpanded = false
                            },
                        )
                    }
                }
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
        }
    }
}
