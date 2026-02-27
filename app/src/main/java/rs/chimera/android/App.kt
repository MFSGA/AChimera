package rs.chimera.android

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import rs.chimera.android.ui.BottomBar
import rs.chimera.android.ui.BottomBarItem
import rs.chimera.android.ui.ChimeraApp
import rs.chimera.android.ui.ProfileScreen

@Composable
fun ChimeraAppRoot(modifier: Modifier = Modifier) {
    val isServiceRunning by Global.isServiceRunning.collectAsState()
    val (selectedItem, setSelectedItem) = rememberSaveable { mutableStateOf(BottomBarItem.Home) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            BottomBar(
                selectedItem = selectedItem,
                onItemSelected = setSelectedItem,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    Global.isServiceRunning.value = !Global.isServiceRunning.value
                },
            ) {
                Text(if (isServiceRunning) "Stop" else "Start")
            }
        },
    ) { innerPadding ->
        ChimeraApp(
            modifier = Modifier.padding(innerPadding),
            sectionTitle = stringResource(id = selectedItem.label),
        )
        when (selectedItem) {
            BottomBarItem.Home -> {
                ChimeraApp(
                    modifier = Modifier.padding(innerPadding),
                    sectionTitle = stringResource(id = selectedItem.label),
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
