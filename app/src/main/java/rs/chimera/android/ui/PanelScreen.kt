package rs.chimera.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.chimera.android.R
import rs.chimera.android.viewmodel.HomeViewModel
import uniffi.chimera_ffi.Mode
import uniffi.chimera_ffi.Proxy

private const val DELAY_EXCELLENT_MS = 300
private const val DELAY_GOOD_MS = 600
private const val PROXY_COLUMNS = 2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    val proxyTypes = remember(viewModel.proxies) {
        viewModel.proxies.associate { it.name to it.proxyType }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.panel_screen)) },
                actions = {
                    IconButton(
                        onClick = { viewModel.fetchProxies() },
                        enabled = viewModel.isVpnRunning && !viewModel.isRefreshing,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(id = R.string.refresh),
                        )
                    }
                },
                windowInsets = WindowInsets(),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    PanelHeroCard(
                        currentMode = viewModel.currentMode,
                        isRunning = viewModel.isVpnRunning,
                        isModeUpdating = viewModel.isModeUpdating,
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
                } else if (groups.isEmpty() && !viewModel.isRefreshing) {
                    item {
                        EmptyStateCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            title = stringResource(id = R.string.no_proxy_groups),
                            message = stringResource(id = R.string.panel_refresh_hint),
                        )
                    }
                } else {
                    items(items = groups, key = { it.name }) { proxy ->
                        ProxyGroupCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            proxy = proxy,
                            delays = viewModel.delays,
                            proxyTypes = proxyTypes,
                            onTestDelay = { viewModel.testGroupDelay(proxy.all) },
                            onSelect = { selected -> viewModel.selectProxy(proxy.name, selected) },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }

            if (viewModel.isRefreshing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(id = R.string.refreshing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelHeroCard(
    currentMode: Mode,
    isRunning: Boolean,
    isModeUpdating: Boolean,
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

            StatusBadge(
                label = stringResource(id = modeLabelRes(currentMode)),
                active = isRunning,
            )

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
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "proxy-group-rotation",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = proxy.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = proxy.now ?: stringResource(id = R.string.none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            id = if (expanded) R.string.collapse else R.string.expand,
                        ),
                        modifier = Modifier
                            .rotate(rotation)
                            .padding(horizontal = 8.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }

                IconButton(
                    onClick = onTestDelay,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = stringResource(id = R.string.test_latency),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && proxy.all.isNotEmpty(),
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 250),
                ) + fadeOut(animationSpec = tween(durationMillis = 200)),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))

                    proxy.all.chunked(PROXY_COLUMNS).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { option ->
                                ProxyOptionChip(
                                    option = option,
                                    isSelected = option == proxy.now,
                                    delay = delays[option],
                                    type = proxyTypes[option],
                                    onSelect = { onSelect(option) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clickable { onSelect() }
            .padding(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            Color.Transparent
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(id = R.string.selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                type?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
                delay?.let {
                    Text(
                        text = delayDisplayText(it),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
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
