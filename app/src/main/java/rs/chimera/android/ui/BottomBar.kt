package rs.chimera.android.ui

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import rs.chimera.android.R

enum class BottomBarItem(val label: Int) {
    Home(R.string.home_screen),
    Profile(R.string.profile_screen),
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
                icon = { Text(text = stringResource(id = item.label).take(1)) },
                label = { Text(text = stringResource(id = item.label)) },
            )
        }
    }
}
