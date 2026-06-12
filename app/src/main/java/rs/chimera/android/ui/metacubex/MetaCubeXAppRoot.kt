package rs.chimera.android.ui.metacubex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import rs.chimera.android.ui.ConnectionsScreen
import rs.chimera.android.ui.LogsScreen
import rs.chimera.android.ui.PanelScreen
import rs.chimera.android.ui.ProfileScreen
import rs.chimera.android.ui.SettingsScreen

@Composable
fun MetaCubeXAppRoot(
    onSwitchUi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MetaCubeXTab.Overview) }
    var showConnectionsScreen by rememberSaveable { mutableStateOf(false) }
    var showLogsScreen by rememberSaveable { mutableStateOf(false) }

    val showBottomBar = !showConnectionsScreen && !showLogsScreen

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    MetaCubeXBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = {
                            selectedTab = it
                            showConnectionsScreen = false
                            showLogsScreen = false
                        },
                    )
                }
            },
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)

            when (selectedTab) {
                MetaCubeXTab.Overview -> {
                    if (showConnectionsScreen) {
                        ConnectionsScreen(
                            modifier = contentModifier,
                            onBack = { showConnectionsScreen = false },
                        )
                    } else {
                        MetaCubeXHomeScreen(
                            modifier = contentModifier,
                            onProxyClick = { selectedTab = MetaCubeXTab.Proxies },
                            onProfilesClick = { selectedTab = MetaCubeXTab.Profiles },
                            onLogsClick = {
                                selectedTab = MetaCubeXTab.Settings
                                showLogsScreen = true
                            },
                            onSettingsClick = { selectedTab = MetaCubeXTab.Settings },
                            onSwitchUi = onSwitchUi,
                        )
                    }
                }

                MetaCubeXTab.Proxies -> PanelScreen(modifier = contentModifier)

                MetaCubeXTab.Profiles -> ProfileScreen(modifier = contentModifier)

                MetaCubeXTab.Settings -> {
                    if (showLogsScreen) {
                        LogsScreen(
                            modifier = contentModifier,
                            onBack = { showLogsScreen = false },
                        )
                    } else {
                        SettingsScreen(
                            modifier = contentModifier,
                            onLogsClick = { showLogsScreen = true },
                        )
                    }
                }
            }
        }
    }
}
