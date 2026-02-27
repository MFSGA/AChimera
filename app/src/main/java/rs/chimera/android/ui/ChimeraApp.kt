package rs.chimera.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import rs.chimera.android.R
import rs.chimera.android.ffi.ChimeraFfi

@Composable
fun ChimeraApp(
    modifier: Modifier = Modifier,
    sectionTitle: String = "",
    isServiceRunning: Boolean = false,
    profilePath: String = "",
) {
    val ffiMessage = remember { ChimeraFfi.helloOrFallback() }
    val profileLabel = remember(profilePath) {
        if (profilePath.isBlank()) "" else File(profilePath).name
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sectionTitle.isNotEmpty()) {
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(id = R.string.home_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            StatsCard(
                title = stringResource(id = R.string.native_bridge_title),
                value = ffiMessage,
                subtitle = stringResource(id = R.string.native_bridge_subtitle),
            )
        }
        item {
            StatsCard(
                title = stringResource(id = R.string.service_status_title),
                value = stringResource(
                    id = if (isServiceRunning) {
                        R.string.service_running
                    } else {
                        R.string.service_stopped
                    },
                ),
                subtitle = stringResource(id = R.string.service_status_subtitle),
            )
        }
        item {
            StatsCard(
                title = stringResource(id = R.string.profile_title),
                value = if (profileLabel.isBlank()) {
                    stringResource(id = R.string.profile_missing)
                } else {
                    profileLabel
                },
                subtitle = if (profilePath.isBlank()) profilePath else profilePath,
            )
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    value: String,
    subtitle: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
