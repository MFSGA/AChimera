package rs.chimera.android

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import rs.chimera.android.service.TunService
import rs.chimera.android.service.tunService
import rs.chimera.android.ui.BottomBar
import rs.chimera.android.ui.BottomBarItem
import rs.chimera.android.ui.ChimeraApp
import rs.chimera.android.ui.PanelScreen
import rs.chimera.android.ui.ProfileScreen

@Composable
fun ChimeraAppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isServiceRunning by Global.isServiceRunning.collectAsState()
    val (selectedItem, setSelectedItem) = rememberSaveable { mutableStateOf(BottomBarItem.Home) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            context.startService(TunService::class.intent)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.service_vpn_permission_required),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

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
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            context.startService(TunService::class.intent)
                        }
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
