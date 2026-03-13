package rs.chimera.android.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import rs.chimera.android.R

enum class BottomBarItem(
    val icon: ImageVector,
    @param:StringRes val label: Int,
) {
    Home(Icons.Outlined.Home, R.string.home_screen),
    Panel(Icons.Outlined.Dashboard, R.string.panel_screen),
    Profile(Icons.AutoMirrored.Outlined.TextSnippet, R.string.profile_screen),
    Settings(Icons.Outlined.Settings, R.string.settings_screen),
}

@Composable
fun BottomBar(
    selectedItem: BottomBarItem,
    onItemSelected: (BottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        BottomBarItem.entries.forEach { item ->
            NavigationBarItem(
                selected = selectedItem == item,
                onClick = { onItemSelected(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.label),
                    )
                },
                label = { Text(stringResource(item.label)) },
            )
        }
    }
}
