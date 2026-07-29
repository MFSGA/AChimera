package rs.chimera.android.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rs.chimera.android.Global
import rs.chimera.android.R

private const val MAX_LOG_LINES = 400
private const val LOG_REFRESH_INTERVAL_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val verticalScrollState = rememberScrollState()
    var logContent by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var refreshPaused by remember { mutableStateOf(false) }

    LaunchedEffect(refreshPaused) {
        while (isActive) {
            if (!refreshPaused) {
                runCatching { Global.readRuntimeLogTail(MAX_LOG_LINES) }
                    .onSuccess { latest ->
                        errorMessage = null
                        if (latest != logContent) logContent = latest
                    }.onFailure { error ->
                        errorMessage = error.message ?: error.javaClass.simpleName
                    }
            }
            delay(LOG_REFRESH_INTERVAL_MS)
        }
    }

    LaunchedEffect(logContent, autoScrollEnabled) {
        if (autoScrollEnabled) {
            verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_screen)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("Chimera runtime log", logContent)),
                                )
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.home_logs_copy))
                    }
                    TextButton(
                        enabled = logContent.isNotBlank(),
                        onClick = {
                            runCatching { Global.clearRuntimeLog() }
                                .onSuccess {
                                    logContent = ""
                                    errorMessage = null
                                }.onFailure { error ->
                                    errorMessage = error.message ?: error.javaClass.simpleName
                                }
                        },
                    ) {
                        Text(text = stringResource(R.string.home_logs_clear))
                    }
                },
                windowInsets = WindowInsets(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.logs_read_error, errorMessage!!),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.logs_autoscroll_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.logs_autoscroll_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(
                        checked = autoScrollEnabled,
                        onCheckedChange = { autoScrollEnabled = it },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.logs_pause_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.logs_pause_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(
                        checked = refreshPaused,
                        onCheckedChange = { refreshPaused = it },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = Global.runtimeLogFile().absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )

                    SelectionContainer {
                        Text(
                            text = if (logContent.isBlank()) {
                                stringResource(R.string.home_logs_empty)
                            } else {
                                logContent
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Unspecified,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = MaterialTheme.shapes.large,
                                )
                                .padding(12.dp)
                                .verticalScroll(verticalScrollState),
                        )
                    }
                }
            }
        }
    }
}
