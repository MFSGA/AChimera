package rs.chimera.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.util.runCatchingPreservingCancellation
import rs.chimera.android.backend.model.ProxyProviderSnapshot

@Composable
internal fun ProxyProviderDiagnosticsDialog(
    onDismiss: () -> Unit,
    onLoad: suspend () -> List<ProxyProviderSnapshot>,
    onUpdate: suspend (String) -> Unit,
    onHealthcheck: suspend (String) -> Unit,
) {
    var reloadGeneration by rememberSaveable { mutableIntStateOf(0) }
    var providers by remember { mutableStateOf<List<ProxyProviderSnapshot>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var activeAction by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val unknownError = stringResource(R.string.profile_unknown_error)
    val actionFailed = stringResource(R.string.proxy_provider_action_failed)
    val updatedTemplate = stringResource(R.string.proxy_provider_updated)
    val checkedTemplate = stringResource(R.string.proxy_provider_checked)

    LaunchedEffect(reloadGeneration) {
        loading = true
        providers = null
        errorMessage = null
        runCatchingPreservingCancellation { onLoad() }
            .onSuccess { providers = it }
            .onFailure { error -> errorMessage = error.message ?: unknownError }
        loading = false
    }

    fun runAction(
        key: String,
        successMessage: String,
        action: suspend () -> Unit,
    ) {
        if (activeAction != null) return
        activeAction = key
        actionMessage = null
        scope.launch {
            runCatchingPreservingCancellation { action() }
                .onSuccess {
                    actionMessage = successMessage
                    reloadGeneration += 1
                }.onFailure { error ->
                    actionMessage = actionFailed.format(error.message ?: unknownError)
                }
            activeAction = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.proxy_providers_title)) },
        text = {
            when {
                loading -> CircularProgressIndicator()
                errorMessage != null -> Text(errorMessage.orEmpty())
                providers.isNullOrEmpty() -> Text(stringResource(R.string.proxy_providers_empty))
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        actionMessage?.let { Text(it) }
                        providers.orEmpty().forEachIndexed { index, provider ->
                            if (index > 0) HorizontalDivider()
                            Text(
                                text = stringResource(
                                    R.string.proxy_provider_summary,
                                    provider.name,
                                    provider.type,
                                    provider.vehicleType,
                                    provider.proxyCount,
                                ),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    enabled = activeAction == null,
                                    onClick = {
                                        runAction(
                                            key = "update:${provider.name}",
                                            successMessage = updatedTemplate.format(provider.name),
                                        ) { onUpdate(provider.name) }
                                    },
                                ) {
                                    Text(stringResource(R.string.proxy_provider_update))
                                }
                                TextButton(
                                    enabled = activeAction == null,
                                    onClick = {
                                        runAction(
                                            key = "health:${provider.name}",
                                            successMessage = checkedTemplate.format(provider.name),
                                        ) { onHealthcheck(provider.name) }
                                    },
                                ) {
                                    Text(stringResource(R.string.proxy_provider_healthcheck))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            if (!loading && activeAction == null) {
                TextButton(onClick = { reloadGeneration += 1 }) {
                    Text(stringResource(R.string.rules_diagnostics_refresh))
                }
            }
        },
    )
}
