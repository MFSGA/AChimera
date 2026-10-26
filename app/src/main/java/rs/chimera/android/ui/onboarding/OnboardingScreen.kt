package rs.chimera.android.ui.onboarding

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.ui.preferences.UiVariant
import rs.chimera.android.viewmodel.HomeViewModel

@Composable
internal fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel()
    val serviceState by BackendProvider.provide().serviceState.collectAsState()
    var urlInput by remember { mutableStateOf("") }
    var showUrlInput by remember { mutableStateOf(false) }
    var scanNotice by remember { mutableStateOf<String?>(null) }
    var showQuickTips by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scanPermissionError = stringResource(R.string.onboarding_qr_permission_error)
    val scanFailure = stringResource(R.string.onboarding_qr_scan_error)
    val scanEmpty = stringResource(R.string.onboarding_qr_empty)
    val vpnPermissionDenied = stringResource(R.string.service_vpn_permission_denied)

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importLocalProfile(uri)
    }
    val qrLauncher = rememberLauncherForActivityResult(
        contract = ScanQRCode(),
    ) { result ->
        scanNotice = null
        when (result) {
            is QRResult.QRSuccess -> {
                val scanned = result.content.rawValue
                    ?.takeIf(String::isNotBlank)
                    ?: result.content.rawBytes?.let { String(it) }?.takeIf(String::isNotBlank)
                if (scanned == null) {
                    scanNotice = scanEmpty
                } else {
                    urlInput = scanned
                    showUrlInput = true
                }
            }
            QRResult.QRUserCanceled -> Unit
            QRResult.QRMissingPermission -> scanNotice = scanPermissionError
            is QRResult.QRError -> scanNotice = scanFailure
        }
    }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            homeViewModel.startVpnAfterPermission()
        } else {
            homeViewModel.cancelVpnStart()
            viewModel.reportConnectionFailure()
            homeViewModel.reportError(vpnPermissionDenied)
        }
    }

    LaunchedEffect(viewModel.step, serviceState) {
        if (viewModel.step == OnboardingStep.CONNECT && serviceState == ServiceState.RUNNING) {
            viewModel.markConnected()
        } else if (OnboardingPolicy.shouldReturnToConnect(viewModel.step, serviceState)) {
            viewModel.returnToConnectAfterDisconnect()
        }
    }
    LaunchedEffect(homeViewModel.errorMessage) {
        if (viewModel.step == OnboardingStep.CONNECT && homeViewModel.errorMessage != null) {
            viewModel.reportConnectionFailure()
        }
    }
    LaunchedEffect(viewModel.step) {
        if (viewModel.step != OnboardingStep.PROFILE) {
            urlInput = ""
            showUrlInput = false
            scanNotice = null
        }
    }

    BackHandler {
        if (!viewModel.isWorking) {
            when (viewModel.step) {
                OnboardingStep.WELCOME -> (context as? Activity)?.finish()
                OnboardingStep.COMPLETE -> onFinish()
                else -> viewModel.goBack()
            }
        }
    }

    val stepNumber = when (viewModel.step) {
        OnboardingStep.WELCOME -> 0
        OnboardingStep.UI_SELECTION -> 1
        OnboardingStep.PROFILE -> 2
        OnboardingStep.CONNECT -> 3
        OnboardingStep.COMPLETE -> 4
    }
    val stepLabel = when (viewModel.step) {
        OnboardingStep.WELCOME -> R.string.onboarding_welcome
        OnboardingStep.UI_SELECTION -> R.string.onboarding_step_ui
        OnboardingStep.PROFILE -> R.string.onboarding_step_profile
        OnboardingStep.CONNECT -> R.string.onboarding_step_connect
        OnboardingStep.COMPLETE -> R.string.onboarding_step_complete
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (viewModel.step == OnboardingStep.UI_SELECTION) {
                Surface(
                    modifier = Modifier.navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 4.dp,
                ) {
                    Button(
                        onClick = viewModel::confirmUi,
                        enabled = !viewModel.isCheckingProfiles,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        if (viewModel.isCheckingProfiles) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        Text(stringResource(R.string.onboarding_ui_continue))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.step != OnboardingStep.WELCOME) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::goBack, enabled = !viewModel.isWorking) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(stepLabel),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (viewModel.step != OnboardingStep.COMPLETE) {
                            Text(
                                text = stringResource(R.string.onboarding_progress, stepNumber, 3),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (viewModel.step != OnboardingStep.COMPLETE) {
                    LinearProgressIndicator(
                        progress = { stepNumber / 3f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when (viewModel.step) {
                OnboardingStep.WELCOME -> WelcomePage(
                    onStart = viewModel::goToUiSelection,
                    onSkip = {
                        viewModel.skipGuide()
                        onFinish()
                    },
                )
                OnboardingStep.UI_SELECTION -> UiSelectionPage(
                    selectedUi = viewModel.selectedUi,
                    profileLookupFailed = viewModel.profileLookupFailed,
                    onSelect = viewModel::chooseUi,
                    onRetryLookup = viewModel::retryProfileLookup,
                )
                OnboardingStep.PROFILE -> ProfilePage(
                    viewModel = viewModel,
                    urlInput = urlInput,
                    onUrlChange = { urlInput = it },
                    showUrlInput = showUrlInput,
                    scanNotice = scanNotice,
                    onFile = { fileLauncher.launch(arrayOf("*/*")) },
                    onUrl = {
                        showUrlInput = true
                        scanNotice = null
                    },
                    onQr = {
                        scanNotice = null
                        qrLauncher.launch(null)
                    },
                    onImportUrl = { viewModel.importRemoteProfile(urlInput) },
                )
                OnboardingStep.CONNECT -> ConnectPage(
                    profileName = viewModel.importedProfileName,
                    isRunning = serviceState == ServiceState.RUNNING,
                    isBusy = homeViewModel.isVpnTransitioning || homeViewModel.isVpnActionInProgress,
                    hasFailure = viewModel.connectionFailure,
                    onConnect = {
                        viewModel.clearConnectionFailure()
                        homeViewModel.clearError()
                        homeViewModel.startVpn(vpnPermissionLauncher::launch)
                    },
                )
                OnboardingStep.COMPLETE -> CompletePage(
                    isRunning = serviceState == ServiceState.RUNNING,
                    showQuickTips = showQuickTips,
                    onToggleTips = { showQuickTips = !showQuickTips },
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    Spacer(modifier = Modifier.height(18.dp))
    Surface(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("C", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    Text(
        text = stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(14.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_start))
    }
    OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_skip))
    }
}

@Composable
private fun UiSelectionPage(
    selectedUi: UiVariant,
    profileLookupFailed: Boolean,
    onSelect: (UiVariant) -> Unit,
    onRetryLookup: () -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_ui_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.onboarding_ui_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    UiPreviewCard(
        variant = UiVariant.WATFAQ,
        selected = selectedUi == UiVariant.WATFAQ,
        onClick = { onSelect(UiVariant.WATFAQ) },
    )
    UiPreviewCard(
        variant = UiVariant.METACUBEX,
        selected = selectedUi == UiVariant.METACUBEX,
        onClick = { onSelect(UiVariant.METACUBEX) },
    )
    if (profileLookupFailed) {
        Text(
            text = stringResource(R.string.onboarding_profile_lookup_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onRetryLookup) {
            Text(stringResource(R.string.onboarding_retry))
        }
    }
}

@Composable
private fun UiPreviewCard(
    variant: UiVariant,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title = if (variant == UiVariant.WATFAQ) {
        R.string.onboarding_ui_chimera
    } else {
        R.string.onboarding_ui_cmfa
    }
    val subtitle = if (variant == UiVariant.WATFAQ) {
        R.string.onboarding_ui_chimera_description
    } else {
        R.string.onboarding_ui_cmfa_description
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            InterfacePreview(variant)
        }
    }
}

@Composable
private fun InterfacePreview(variant: UiVariant) {
    val preview = if (variant == UiVariant.WATFAQ) {
        R.drawable.onboarding_chimera_preview
    } else {
        R.drawable.onboarding_cmfa_preview
    }
    Image(
        painter = painterResource(preview),
        contentDescription = stringResource(R.string.onboarding_ui_preview_description),
        modifier = Modifier
            .fillMaxWidth()
            .height(105.dp)
            .clip(RoundedCornerShape(14.dp)),
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(0f, -0.94f),
    )
}

@Composable
private fun ProfilePage(
    viewModel: OnboardingViewModel,
    urlInput: String,
    onUrlChange: (String) -> Unit,
    showUrlInput: Boolean,
    scanNotice: String?,
    onFile: () -> Unit,
    onUrl: () -> Unit,
    onQr: () -> Unit,
    onImportUrl: () -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_profile_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.onboarding_profile_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ImportOption(
        icon = Icons.Filled.FolderOpen,
        title = stringResource(R.string.onboarding_import_file),
        subtitle = stringResource(R.string.onboarding_import_file_description),
        enabled = !viewModel.isWorking,
        onClick = onFile,
    )
    ImportOption(
        icon = Icons.Filled.Link,
        title = stringResource(R.string.onboarding_import_url),
        subtitle = stringResource(R.string.onboarding_import_url_description),
        enabled = !viewModel.isWorking,
        onClick = onUrl,
    )
    ImportOption(
        icon = Icons.Filled.QrCodeScanner,
        title = stringResource(R.string.onboarding_import_qr),
        subtitle = stringResource(R.string.onboarding_import_qr_description),
        enabled = !viewModel.isWorking,
        onClick = onQr,
    )
    if (showUrlInput) {
        OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_subscription_url)) },
            placeholder = { Text(stringResource(R.string.onboarding_subscription_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !viewModel.isWorking,
        )
        Button(
            onClick = onImportUrl,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isWorking && urlInput.isNotBlank(),
        ) {
            Text(stringResource(R.string.onboarding_import_and_check))
        }
    }
    scanNotice?.let { notice ->
        Text(notice, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (viewModel.isWorking) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val progress = viewModel.downloadProgress
            if (progress != null && progress.total > 0uL) {
                LinearProgressIndicator(
                    progress = {
                        (progress.downloaded.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = if (progress == null || progress.total == 0uL) {
                    stringResource(R.string.onboarding_importing)
                } else {
                    stringResource(
                        R.string.onboarding_download_progress,
                        progress.downloaded.toString(),
                        progress.total.toString(),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    viewModel.importFailure?.let { failure ->
        ImportFailureCard(
            failure = failure,
            onRetry = viewModel::retryImport,
            onEdit = viewModel::clearImportFailure,
        )
    }
    Text(
        text = stringResource(R.string.onboarding_profile_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ImportOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ImportFailureCard(
    failure: ProfileImportFailure,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) {
    val (title, body) = when (failure.type) {
        ProfileImportFailureType.INVALID_URL -> R.string.onboarding_error_invalid_url to R.string.onboarding_error_invalid_url_body
        ProfileImportFailureType.HTTP_AUTH -> R.string.onboarding_error_http_auth to R.string.onboarding_error_http_auth_body
        ProfileImportFailureType.HTTP_NOT_FOUND -> R.string.onboarding_error_http_not_found to R.string.onboarding_error_http_not_found_body
        ProfileImportFailureType.HTTP_SERVER -> R.string.onboarding_error_http_server to R.string.onboarding_error_http_server_body
        ProfileImportFailureType.HTTP_OTHER -> R.string.onboarding_error_http_other to R.string.onboarding_error_http_other_body
        ProfileImportFailureType.TIMEOUT -> R.string.onboarding_error_timeout to R.string.onboarding_error_timeout_body
        ProfileImportFailureType.NETWORK -> R.string.onboarding_error_network to R.string.onboarding_error_network_body
        ProfileImportFailureType.TLS -> R.string.onboarding_error_tls to R.string.onboarding_error_tls_body
        ProfileImportFailureType.REDIRECT -> R.string.onboarding_error_redirect to R.string.onboarding_error_redirect_body
        ProfileImportFailureType.TOO_LARGE -> R.string.onboarding_error_too_large to R.string.onboarding_error_too_large_body
        ProfileImportFailureType.EMPTY_RESPONSE -> R.string.onboarding_error_empty to R.string.onboarding_error_empty_body
        ProfileImportFailureType.HTML_RESPONSE -> R.string.onboarding_error_html to R.string.onboarding_error_html_body
        ProfileImportFailureType.INVALID_CONFIGURATION -> R.string.onboarding_error_config to R.string.onboarding_error_config_body
        ProfileImportFailureType.LOCAL_FILE -> R.string.onboarding_error_file to R.string.onboarding_error_file_body
        ProfileImportFailureType.UNKNOWN -> R.string.onboarding_error_unknown to R.string.onboarding_error_unknown_body
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val resolvedTitle = if (failure.httpStatus != null) {
                stringResource(title, failure.httpStatus)
            } else {
                stringResource(title)
            }
            Text(resolvedTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall)
            if (failure.type == ProfileImportFailureType.INVALID_URL) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.onboarding_edit_link))
                }
            } else {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.onboarding_retry))
                }
            }
        }
    }
}

@Composable
private fun ConnectPage(
    profileName: String?,
    isRunning: Boolean,
    isBusy: Boolean,
    hasFailure: Boolean,
    onConnect: () -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_connect_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.onboarding_connect_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    profileName?.let { name ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.onboarding_active_profile), style = MaterialTheme.typography.labelMedium)
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (hasFailure) {
        Text(
            text = stringResource(R.string.onboarding_connect_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (isRunning) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.onboarding_connect_running), fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(
            onClick = onConnect,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text(stringResource(R.string.onboarding_connect_button))
        }
        Text(
            text = stringResource(R.string.onboarding_connect_permission_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompletePage(
    isRunning: Boolean,
    showQuickTips: Boolean,
    onToggleTips: () -> Unit,
    onFinish: () -> Unit,
) {
    if (!isRunning) {
        Spacer(Modifier.height(18.dp))
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.onboarding_complete_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Spacer(Modifier.height(18.dp))
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.padding(18.dp).size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        text = stringResource(R.string.onboarding_complete_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.onboarding_complete_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onToggleTips) {
        Text(stringResource(if (showQuickTips) R.string.onboarding_tips_hide else R.string.onboarding_tips_show))
    }
    if (showQuickTips) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.onboarding_quick_tips), fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                Text(stringResource(R.string.onboarding_tip_one), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.onboarding_tip_two), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.onboarding_tip_three), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_go_to_app))
    }
}
