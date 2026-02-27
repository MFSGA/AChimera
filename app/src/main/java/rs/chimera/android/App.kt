package rs.chimera.android

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import rs.chimera.android.ui.ChimeraApp

@Composable
fun ChimeraAppRoot(modifier: Modifier = Modifier) {
    val isServiceRunning by Global.isServiceRunning.collectAsState()

    Scaffold(
        modifier = modifier,
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
        )
    }
}
