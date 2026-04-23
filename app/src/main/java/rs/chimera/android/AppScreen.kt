package rs.chimera.android

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
import rs.chimera.android.ui.HomeScreen
import rs.chimera.android.ui.LogsScreen
import rs.chimera.android.ui.PanelScreen
import rs.chimera.android.ui.ProfileScreen
import rs.chimera.android.ui.SettingsScreen
import rs.chimera.android.ui.components.BottomBar
import rs.chimera.android.ui.components.BottomBarItem

@Composable
fun ChimeraAppRoot(modifier: Modifier = Modifier) {
    var selectedItem by rememberSaveable { mutableStateOf(BottomBarItem.Home) }
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
                    BottomBar(
                        selectedItem = selectedItem,
                        onItemSelected = {
                            selectedItem = it
                            showConnectionsScreen = false
                            showLogsScreen = false
                        },
                    )
                }
            },
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)

            when (selectedItem) {
                BottomBarItem.Home -> {
                    if (showConnectionsScreen) {
                        ConnectionsScreen(
                            modifier = contentModifier,
                            onBack = { showConnectionsScreen = false },
                        )
                    } else {
                        HomeScreen(
                            modifier = contentModifier,
                            onConnectionsClick = { showConnectionsScreen = true },
                        )
                    }
                }

                BottomBarItem.Panel -> {
                    PanelScreen(
                        modifier = contentModifier,
                    )
                }

                BottomBarItem.Profile -> {
                    ProfileScreen(
                        modifier = contentModifier,
                    )
                }

                BottomBarItem.Settings -> {
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
