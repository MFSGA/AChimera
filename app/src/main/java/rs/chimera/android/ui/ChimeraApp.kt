package rs.chimera.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.chimera.android.R
import rs.chimera.android.ffi.ChimeraFfi

@Composable
fun ChimeraApp(modifier: Modifier = Modifier) {
    val ffiMessage = remember { ChimeraFfi.helloOrFallback() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = stringResource(id = R.string.home_message))
            Box(modifier = Modifier.size(8.dp))
            Text(text = ffiMessage)
            Text(text = "所展现22222333333出")
        }
    }
}
