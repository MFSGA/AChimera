package rs.chimera.android

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import rs.chimera.android.service.TunService
import rs.chimera.android.service.tunService
import rs.chimera.android.ui.BottomBar
import rs.chimera.android.ui.BottomBarItem
import rs.chimera.android.ui.ChimeraApp
import rs.chimera.android.ui.ProfileScreen

@Composable
fun ChimeraAppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
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
                    if (isServiceRunning) {
                        tunService?.stopVpn() ?: context.stopService(TunService::class.intent)
                    } else if (Global.profilePath.isBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.service_profile_required),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        ContextCompat.startForegroundService(context, TunService::class.intent)
                    }
                },
            ) {
                Text(
                    text = stringResource(
                        id = if (isServiceRunning) {
                            R.string.service_stop_action
                        } else {
                            R.string.service_start_action
                        },
                    ),
                )
            }
        },
    ) { innerPadding ->
        when (selectedItem) {
            BottomBarItem.Home -> {
                ChimeraApp(
                    modifier = Modifier.padding(innerPadding),
                    sectionTitle = stringResource(id = selectedItem.label),
                    isServiceRunning = isServiceRunning,
                    profilePath = Global.profilePath,
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
