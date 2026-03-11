package rs.chimera.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.chimera.android.R
import rs.chimera.android.viewmodel.HomeViewModel
import uniffi.chimera_ffi.Mode
import uniffi.chimera_ffi.Proxy
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val groups = remember(viewModel.proxies) {
        viewModel.proxies.filter { it.all.isNotEmpty() }
    }

    LaunchedEffect(viewModel.isVpnRunning) {
        if (viewModel.isVpnRunning && viewModel.proxies.isEmpty()) {
            viewModel.fetchMode()
            viewModel.fetchProxies()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PanelHeroCard(
                currentMode = viewModel.currentMode,
                isRunning = viewModel.isVpnRunning,
                isModeUpdating = viewModel.isModeUpdating,
                isRefreshing = viewModel.isRefreshing,
                onRefresh = { viewModel.fetchProxies() },
                onSwitchMode = { viewModel.switchMode(it) },
            )
        }

        viewModel.errorMessage?.let { message ->
            item {
                MessageCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(id = R.string.panel_error_title),
                    message = message,
                    onDismiss = { viewModel.clearError() },
                )
            }
        }

        if (!viewModel.isVpnRunning) {
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(id = R.string.panel_not_running_title),
                    message = stringResource(id = R.string.panel_not_running_message),
                )
            }
            return@LazyColumn
        }

        if (groups.isEmpty() && !viewModel.isRefreshing) {
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(id = R.string.no_proxy_groups),
                    message = stringResource(id = R.string.panel_refresh_hint),
                )
            }
            return@LazyColumn
        }

        items(items = groups, key = { it.name }) { proxy ->
            ProxyGroupCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                proxy = proxy,
                delays = viewModel.delays,
                proxyTypes = viewModel.proxies.associate { it.name to it.proxyType },
                onTestDelay = { viewModel.testGroupDelay(proxy.all) },
                onSelect = { selected -> viewModel.selectProxy(proxy.name, selected) },
            )
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelHeroCard(
    currentMode: Mode,
    isRunning: Boolean,
    isModeUpdating: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSwitchMode: (Mode) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(id = R.string.panel_screen),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(id = R.string.panel_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onRefresh, enabled = isRunning && !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = stringResource(id = R.string.refresh))
                    }
                }
                StatusBadge(
                    label = stringResource(id = modeLabelRes(currentMode)),
                    active = isRunning,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(Mode.RULE, Mode.GLOBAL, Mode.DIRECT).forEach { mode ->
                    OutlinedButton(
                        onClick = { onSwitchMode(mode) },
                        enabled = isRunning && !isModeUpdating && currentMode != mode,
                    ) {
                        Text(text = stringResource(id = modeLabelRes(mode)))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProxyGroupCard(
    proxy: Proxy,
    delays: Map<String, String>,
    proxyTypes: Map<String, String>,
    onTestDelay: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = proxy.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = proxy.now ?: stringResource(id = R.string.none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = onTestDelay) {
                    Text(text = stringResource(id = R.string.test_latency))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                proxy.all.forEach { option ->
                    ProxyOptionChip(
                        option = option,
                        isSelected = option == proxy.now,
                        delay = delays[option],
                        type = proxyTypes[option],
                        onSelect = { onSelect(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyOptionChip(
    option: String,
    isSelected: Boolean,
    delay: String?,
    type: String?,
    onSelect: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.clickable(onClick = onSelect),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = option,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                type?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                delay?.let {
                    Text(
                        text = delayDisplayText(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = delayColor(it),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.panel_dismiss))
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun modeLabelRes(mode: Mode): Int = when (mode) {
    Mode.RULE -> R.string.proxy_mode_rule
    Mode.GLOBAL -> R.string.proxy_mode_global
    Mode.DIRECT -> R.string.proxy_mode_direct
}

@Composable
private fun delayColor(delay: String) = when {
    delay.contains("ms") -> {
        val ms = delay.removeSuffix("ms").trim().toIntOrNull() ?: 0
        when {
            ms < 300 -> MaterialTheme.colorScheme.primary
            ms < 600 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        }
    }
    delay == "testing..." -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun delayDisplayText(delay: String): String = when (delay) {
    "testing..." -> stringResource(id = R.string.testing)
    "timeout" -> stringResource(id = R.string.timeout)
    "unavailable" -> stringResource(id = R.string.not_available)
    else -> delay
}

@Composable
private fun StatusBadge(
    label: String,
    active: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp),
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    content = {},
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
