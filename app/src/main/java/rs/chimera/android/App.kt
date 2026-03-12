package rs.chimera.android

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import rs.chimera.android.ui.BottomBar
import rs.chimera.android.ui.BottomBarItem
import rs.chimera.android.ui.HomeScreen
import rs.chimera.android.ui.PanelScreen
import rs.chimera.android.ui.ProfileScreen

@Composable
fun ChimeraAppRoot(modifier: Modifier = Modifier) {
    val (selectedItem, setSelectedItem) = rememberSaveable { mutableStateOf(BottomBarItem.Home) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            BottomBar(
                selectedItem = selectedItem,
                onItemSelected = setSelectedItem,
            )
        },
    ) { innerPadding ->
        when (selectedItem) {
            BottomBarItem.Home -> {
                HomeScreen(
                    modifier = Modifier.padding(innerPadding),
                )
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
