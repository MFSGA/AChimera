package rs.chimera.android

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import rs.chimera.android.ui.BottomBar
import rs.chimera.android.ui.BottomBarItem
import rs.chimera.android.ui.ConnectionsScreen
import rs.chimera.android.ui.HomeScreen
import rs.chimera.android.ui.PanelScreen
import rs.chimera.android.ui.ProfileScreen

@Composable
fun ChimeraAppRoot(modifier: Modifier = Modifier) {
    var selectedItem by rememberSaveable { mutableStateOf(BottomBarItem.Home) }
    var showConnectionsScreen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        bottomBar = if (showConnectionsScreen) {
            {}
        } else {
            {
                BottomBar(
                    selectedItem = selectedItem,
                    onItemSelected = {
                        selectedItem = it
                        showConnectionsScreen = false
                    },
                )
            }
        },
    ) { innerPadding ->
        when (selectedItem) {
            BottomBarItem.Home -> {
                if (showConnectionsScreen) {
                    ConnectionsScreen(
                        modifier = Modifier.padding(innerPadding),
                        onBack = { showConnectionsScreen = false },
                    )
                } else {
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        onConnectionsClick = { showConnectionsScreen = true },
                    )
                }
            }

            BottomBarItem.Panel -> {
                PanelScreen(
                    modifier = Modifier.padding(innerPadding),
                )
            }

            BottomBarItem.Profile -> {
                ProfileScreen(
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
