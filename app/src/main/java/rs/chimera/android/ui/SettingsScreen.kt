package rs.chimera.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.chimera.android.R
import rs.chimera.android.ui.components.TextInfoDialog
import rs.chimera.android.ui.navigation.DefaultAppUiRouter
import rs.chimera.android.ui.preferences.AppearancePreference
import rs.chimera.android.ui.preferences.LanguagePreference
import rs.chimera.android.ui.preferences.UiVariant
import rs.chimera.android.viewmodel.ListenerPortInputPolicy
import rs.chimera.android.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogsClick: () -> Unit,
    onAppFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showUiDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showProvidersDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        TextInfoDialog(
            title = stringResource(R.string.about_title),
            content = stringResource(R.string.settings_known_issues),
            onDismiss = { showInfoDialog = false },
        )
    }

    if (showDnsDialog) {
        DnsDiagnosticsDialog(
            onDismiss = { showDnsDialog = false },
            onQuery = viewModel::queryDns,
        )
    }

    if (showRulesDialog) {
        RuleDiagnosticsDialog(
            onDismiss = { showRulesDialog = false },
            onLoad = viewModel::listRules,
        )
    }

    if (showProvidersDialog) {
        ProxyProviderDiagnosticsDialog(
            onDismiss = { showProvidersDialog = false },
            onLoad = viewModel::listProxyProviders,
            onUpdate = viewModel::updateProxyProvider,
            onHealthcheck = viewModel::healthcheckProxyProvider,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen)) },
                windowInsets = WindowInsets(),
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.action_about),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                SectionHeader(text = stringResource(R.string.settings_general))
            }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = viewModel.getLanguageDisplayName(),
                        onClick = { showLanguageDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.AutoMirrored.Outlined.Subject,
                        title = stringResource(R.string.logs_screen),
                        subtitle = stringResource(R.string.settings_logs_summary),
                        onClick = onLogsClick,
                    )
                    SettingsItem(
                        icon = Icons.Default.Dns,
                        title = stringResource(R.string.dns_diagnostics_title),
                        subtitle = stringResource(R.string.dns_diagnostics_summary),
                        onClick = { showDnsDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.Rule,
                        title = stringResource(R.string.rules_diagnostics_title),
                        subtitle = stringResource(R.string.rules_diagnostics_summary),
                        onClick = { showRulesDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Default.Public,
                        title = stringResource(R.string.proxy_providers_title),
                        subtitle = stringResource(R.string.proxy_providers_summary),
                        onClick = { showProvidersDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Default.Apps,
                        title = stringResource(R.string.settings_app_filter),
                        subtitle = viewModel.getAppFilterSummary(),
                        onClick = onAppFilterClick,
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.cmfa_settings_network))
            }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Lan,
                        title = stringResource(R.string.settings_listener_ports),
                        subtitle = stringResource(
                            R.string.cmfa_ports_summary,
                            viewModel.mixedPort.toInt(),
                            viewModel.httpPort?.toString() ?: "—",
                            viewModel.socksPort?.toString() ?: "—",
                        ),
                        onClick = { showPortDialog = true },
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Lan,
                        title = stringResource(R.string.settings_allow_lan),
                        subtitle = stringResource(R.string.settings_allow_lan_desc),
                        checked = viewModel.allowLan,
                        onCheckedChange = viewModel::updateAllowLan,
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Dns,
                        title = stringResource(R.string.settings_fake_ip),
                        subtitle = stringResource(R.string.settings_fake_ip_desc),
                        checked = viewModel.fakeIpEnabled,
                        onCheckedChange = viewModel::updateFakeIpEnabled,
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Public,
                        title = stringResource(R.string.settings_ipv6),
                        subtitle = stringResource(R.string.settings_ipv6_desc),
                        checked = viewModel.ipv6Enabled,
                        onCheckedChange = viewModel::updateIpv6Enabled,
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.settings_appearance))
            }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(R.string.settings_dark_mode),
                        subtitle = appearanceLabel(viewModel.appearancePreference),
                        onClick = { showAppearanceDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Default.Dashboard,
                        title = stringResource(R.string.settings_ui_style),
                        subtitle = uiVariantLabel(viewModel.uiVariant),
                        onClick = { showUiDialog = true },
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.settings_about))
            }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.about_title),
                        subtitle = stringResource(R.string.settings_about_summary),
                        onClick = { showInfoDialog = true },
                        showChevron = false,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showPortDialog) {
            ListenerPortsDialog(
                currentMixedPort = viewModel.mixedPort,
                currentHttpPort = viewModel.httpPort,
                currentSocksPort = viewModel.socksPort,
                onDismiss = { showPortDialog = false },
                onConfirm = { mixedPort, httpPort, socksPort ->
                    showPortDialog = false
                    viewModel.updateListenerPorts(mixedPort, httpPort, socksPort)
                },
            )
        }

        if (showUiDialog) {
            UiDialog(
                currentVariant = viewModel.uiVariant,
                onDismiss = { showUiDialog = false },
                onConfirm = { variant ->
                    showUiDialog = false
                    if (viewModel.uiVariant != variant) {
                        viewModel.updateUiVariant(variant)
                        when (variant) {
                            UiVariant.WATFAQ -> DefaultAppUiRouter.openWatfaq(context)
                            UiVariant.METACUBEX -> DefaultAppUiRouter.openMetaCubeX(context)
                        }
                    }
                },
            )
        }

        if (showAppearanceDialog) {
            AppearanceDialog(
                currentPreference = viewModel.appearancePreference,
                onDismiss = { showAppearanceDialog = false },
                onConfirm = { preference ->
                    showAppearanceDialog = false
                    viewModel.updateAppearancePreference(preference)
                },
            )
        }

        if (showLanguageDialog) {
            LanguageDialog(
                currentPreference = viewModel.languagePreference,
                onDismiss = { showLanguageDialog = false },
                onConfirm = { preference ->
                    showLanguageDialog = false
                    viewModel.updateLanguagePreference(preference)
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun ListenerPortsDialog(
    currentMixedPort: UShort,
    currentHttpPort: UShort?,
    currentSocksPort: UShort?,
    onDismiss: () -> Unit,
    onConfirm: (UShort, UShort?, UShort?) -> Unit,
) {
    var mixedPortText by remember(currentMixedPort) { mutableStateOf(currentMixedPort.toString()) }
    var httpPortText by remember(currentHttpPort) { mutableStateOf(currentHttpPort?.toString().orEmpty()) }
    var socksPortText by remember(currentSocksPort) { mutableStateOf(currentSocksPort?.toString().orEmpty()) }

    val mixedPort = ListenerPortInputPolicy.parseRequired(mixedPortText)
    val httpPort = ListenerPortInputPolicy.parseOptional(httpPortText)
    val socksPort = ListenerPortInputPolicy.parseOptional(socksPortText)
    val mixedPortValid = mixedPort != null
    val httpPortValid = ListenerPortInputPolicy.isOptionalValid(httpPortText)
    val socksPortValid = ListenerPortInputPolicy.isOptionalValid(socksPortText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_listener_ports)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = mixedPortText,
                    onValueChange = { mixedPortText = it },
                    label = { Text(stringResource(R.string.settings_mixed_port)) },
                    isError = !mixedPortValid,
                    supportingText = if (!mixedPortValid) {
                        { Text(stringResource(R.string.settings_port_invalid)) }
                    } else {
                        null
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = httpPortText,
                    onValueChange = { httpPortText = it },
                    label = { Text(stringResource(R.string.settings_http_port)) },
                    isError = !httpPortValid,
                    supportingText = if (!httpPortValid) {
                        { Text(stringResource(R.string.settings_port_invalid)) }
                    } else {
                        null
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = socksPortText,
                    onValueChange = { socksPortText = it },
                    label = { Text(stringResource(R.string.settings_socks_port)) },
                    isError = !socksPortValid,
                    supportingText = if (!socksPortValid) {
                        { Text(stringResource(R.string.settings_port_invalid)) }
                    } else {
                        null
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = mixedPortValid && httpPortValid && socksPortValid,
                onClick = {
                    onConfirm(
                        requireNotNull(mixedPort),
                        httpPort,
                        socksPort,
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun UiDialog(
    currentVariant: UiVariant,
    onDismiss: () -> Unit,
    onConfirm: (UiVariant) -> Unit,
) {
    var selectedVariant by remember { mutableStateOf(currentVariant) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ui_style)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption(
                    text = stringResource(R.string.ui_style_watfaq),
                    selected = selectedVariant == UiVariant.WATFAQ,
                    onClick = { selectedVariant = UiVariant.WATFAQ },
                )
                LanguageOption(
                    text = stringResource(R.string.ui_style_metacubex),
                    selected = selectedVariant == UiVariant.METACUBEX,
                    onClick = { selectedVariant = UiVariant.METACUBEX },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedVariant) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun AppearanceDialog(
    currentPreference: AppearancePreference,
    onDismiss: () -> Unit,
    onConfirm: (AppearancePreference) -> Unit,
) {
    var selectedPreference by remember { mutableStateOf(currentPreference) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dark_mode)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption(
                    text = stringResource(R.string.dark_mode_system),
                    selected = selectedPreference == AppearancePreference.SYSTEM,
                    onClick = { selectedPreference = AppearancePreference.SYSTEM },
                )
                LanguageOption(
                    text = stringResource(R.string.dark_mode_light),
                    selected = selectedPreference == AppearancePreference.LIGHT,
                    onClick = { selectedPreference = AppearancePreference.LIGHT },
                )
                LanguageOption(
                    text = stringResource(R.string.dark_mode_dark),
                    selected = selectedPreference == AppearancePreference.DARK,
                    onClick = { selectedPreference = AppearancePreference.DARK },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedPreference) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun appearanceLabel(preference: AppearancePreference): String =
    stringResource(
        when (preference) {
            AppearancePreference.SYSTEM -> R.string.dark_mode_system
            AppearancePreference.LIGHT -> R.string.dark_mode_light
            AppearancePreference.DARK -> R.string.dark_mode_dark
        },
    )

@Composable
private fun uiVariantLabel(variant: UiVariant): String =
    stringResource(
        when (variant) {
            UiVariant.WATFAQ -> R.string.ui_style_watfaq
            UiVariant.METACUBEX -> R.string.ui_style_metacubex
        },
    )

@Composable
private fun LanguageDialog(
    currentPreference: LanguagePreference,
    onDismiss: () -> Unit,
    onConfirm: (LanguagePreference) -> Unit,
) {
    var selectedPreference by remember { mutableStateOf(currentPreference) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption(
                    text = stringResource(R.string.language_system),
                    selected = selectedPreference == LanguagePreference.SYSTEM,
                    onClick = { selectedPreference = LanguagePreference.SYSTEM },
                )
                LanguageOption(
                    text = stringResource(R.string.language_simplified_chinese),
                    selected = selectedPreference == LanguagePreference.SIMPLIFIED_CHINESE,
                    onClick = { selectedPreference = LanguagePreference.SIMPLIFIED_CHINESE },
                )
                LanguageOption(
                    text = stringResource(R.string.language_english),
                    selected = selectedPreference == LanguagePreference.ENGLISH,
                    onClick = { selectedPreference = LanguagePreference.ENGLISH },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedPreference) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun LanguageOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
