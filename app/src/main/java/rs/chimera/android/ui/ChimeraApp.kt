package rs.chimera.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import rs.chimera.android.Global
import rs.chimera.android.R
import rs.chimera.android.ffi.ChimeraFfi

@Composable
fun ChimeraApp(
    modifier: Modifier = Modifier,
    sectionTitle: String = "",
    isServiceRunning: Boolean = false,
    profilePath: String = "",
) {
    var refreshVersion by rememberSaveable { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboardManager.current
    val ffiMessage = remember(refreshVersion) { ChimeraFfi.helloOrFallback() }
    val refreshedAt = remember(refreshVersion) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }
    val runtimeLog = remember(refreshVersion, isServiceRunning) {
        Global.readRuntimeLogTail()
    }
    val profileLabel = remember(profilePath) {
        if (profilePath.isBlank()) {
            ""
        } else {
            File(profilePath).name
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DashboardHero(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                refreshedAt = refreshedAt,
                sectionTitle = sectionTitle,
                onRefresh = { refreshVersion++ },
            )
        }

        item {
            SummaryRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                isServiceRunning = isServiceRunning,
                profileLabel = profileLabel,
            )
        }

        item {
            DashboardCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(id = R.string.native_bridge_title),
                value = ffiMessage,
                subtitle = stringResource(
                    id = R.string.native_bridge_subtitle_with_time,
                    refreshedAt,
                ),
                accent = MaterialTheme.colorScheme.tertiary,
            )
        }

        item {
            DashboardCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(id = R.string.profile_title),
                value = if (profileLabel.isBlank()) {
                    stringResource(id = R.string.profile_missing)
                } else {
                    profileLabel
                },
                subtitle = profilePath.ifBlank {
                    stringResource(id = R.string.profile_no_saved_path)
                },
                accent = MaterialTheme.colorScheme.secondary,
            )
        }

        item {
            LogPreviewCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(id = R.string.home_logs_title),
                subtitle = Global.runtimeLogFile().absolutePath,
                content = runtimeLog.ifBlank {
                    stringResource(id = R.string.home_logs_empty)
                },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(runtimeLog))
                },
                onClear = {
                    Global.clearRuntimeLog()
                    refreshVersion++
                },
            )
        }
    }
}

@Composable
private fun DashboardHero(
    modifier: Modifier = Modifier,
    refreshedAt: String,
    sectionTitle: String,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        text = stringResource(id = R.string.home_dashboard_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(id = R.string.home_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                FilledTonalButton(onClick = onRefresh) {
                    Text(text = stringResource(id = R.string.home_refresh))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(
                    label = stringResource(id = R.string.home_screen),
                    active = true,
                )
                Text(
                    text = stringResource(
                        id = R.string.home_last_refreshed,
                        refreshedAt,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            if (sectionTitle.isNotBlank()) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    modifier: Modifier = Modifier,
    isServiceRunning: Boolean,
    profileLabel: String,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniSummaryCard(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.service_status_title),
            value = stringResource(
                id = if (isServiceRunning) {
                    R.string.service_running
                } else {
                    R.string.service_stopped
                },
            ),
            active = isServiceRunning,
        )
        MiniSummaryCard(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.profile_screen),
            value = if (profileLabel.isBlank()) {
                stringResource(id = R.string.profile_missing)
            } else {
                profileLabel
            },
            active = profileLabel.isNotBlank(),
        )
    }
}

@Composable
private fun MiniSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    active: Boolean,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBadge(
                label = title,
                active = active,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun LogPreviewCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    content: String,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onCopy) {
                    Text(text = stringResource(id = R.string.home_logs_copy))
                }
                FilledTonalButton(onClick = onClear) {
                    Text(text = stringResource(id = R.string.home_logs_clear))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    )
                    .padding(14.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Unspecified,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    active: Boolean,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
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
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                    ),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
        }
    }
}
