package rs.chimera.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import rs.chimera.android.ui.metacubex.MetaCubeXAppRoot
import rs.chimera.android.ui.watfaq.WatfaqAppRoot

private enum class UiFlavor {
    Watfaq,
    MetaCubeX,
}

@Composable
fun ChimeraAppRoot(modifier: Modifier = Modifier) {
    var uiFlavor by rememberSaveable { mutableStateOf(UiFlavor.MetaCubeX) }

    when (uiFlavor) {
        UiFlavor.Watfaq -> WatfaqAppRoot(
            modifier = modifier,
            onSwitchUi = { uiFlavor = UiFlavor.MetaCubeX },
        )

        UiFlavor.MetaCubeX -> MetaCubeXAppRoot(
            modifier = modifier,
            onSwitchUi = { uiFlavor = UiFlavor.Watfaq },
        )
    }
}
