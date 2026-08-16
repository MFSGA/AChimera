package rs.chimera.android.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.util.runCatchingPreservingCancellation
import rs.chimera.android.viewmodel.AppFilterMode
import rs.chimera.android.viewmodel.SettingsViewModel

data class AppInfo(
	val packageName: String,
	val appName: String,
	val icon: Drawable,
	val isSystemApp: Boolean,
)

@Destination<RootGraph>
@Composable
fun AppSelectorScreen(
	navigator: DestinationsNavigator,
	modifier: Modifier = Modifier,
	viewModel: SettingsViewModel = viewModel(),
) {
	AppSelectorScreen(
		onBack = navigator::navigateUp,
		modifier = modifier,
		viewModel = viewModel,
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: SettingsViewModel = viewModel(),
) {
	val context = LocalContext.current
	val coroutineScope = rememberCoroutineScope()
	var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
	var isLoading by remember { mutableStateOf(true) }
	var searchQuery by remember { mutableStateOf("") }
	var showSystemApps by remember { mutableStateOf(false) }
	var showModeDialog by remember { mutableStateOf(false) }
	var isSaving by remember { mutableStateOf(false) }
	var saveError by remember { mutableStateOf<String?>(null) }
	var tempFilterMode by remember { mutableStateOf(viewModel.appFilterMode) }
	var selectedApps by remember {
		mutableStateOf(
			when (viewModel.appFilterMode) {
				AppFilterMode.ALLOWED -> viewModel.allowedApps
				AppFilterMode.DISALLOWED -> viewModel.disallowedApps
				else -> emptySet()
			},
		)
	}

	// Load apps when screen opens
	LaunchedEffect(Unit) {
		coroutineScope.launch {
			withContext(Dispatchers.IO) {
				val pm = context.packageManager
				val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
				apps =
					installedApps
						.filter { it.packageName != context.packageName } // Exclude self
						.mapNotNull { appInfo ->
							try {
								val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
								AppInfo(
									packageName = appInfo.packageName,
									appName = pm.getApplicationLabel(appInfo).toString(),
									icon = pm.getApplicationIcon(appInfo),
									isSystemApp = isSystem,
								)
							} catch (_: Exception) {
								null
							}
						}.sortedBy { it.appName.lowercase() }
				isLoading = false
			}
		}
	}

	saveError?.let { error ->
		val details = error.ifBlank { stringResource(R.string.profile_unknown_error) }
		AlertDialog(
			onDismissRequest = { saveError = null },
			title = { Text(stringResource(R.string.settings_title)) },
			text = { Text(stringResource(R.string.cmfa_settings_save_failed, details)) },
			confirmButton = {
				TextButton(onClick = { saveError = null }) {
					Text(stringResource(android.R.string.ok))
				}
			},
		)
	}

	// Filter apps based on search and system apps toggle
	val filteredApps =
		apps.filter { app ->
			val matchesSearch =
				searchQuery.isEmpty() ||
					app.appName.contains(searchQuery, ignoreCase = true) ||
					app.packageName.contains(searchQuery, ignoreCase = true)
			val matchesSystemFilter = showSystemApps || !app.isSystemApp
			matchesSearch && matchesSystemFilter
		}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.app_selector_title)) },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = stringResource(R.string.back),
						)
					}
				},
				windowInsets = WindowInsets(),
			)
		},
		floatingActionButton = {
			FloatingActionButton(
				onClick = {
					if (!isSaving) {
						isSaving = true
						coroutineScope.launch {
							runCatchingPreservingCancellation {
								viewModel.saveAppFilter(tempFilterMode, selectedApps)
							}.onSuccess {
								onBack()
							}.onFailure { error ->
								saveError = error.message.orEmpty()
							}
							isSaving = false
						}
					}
				},
			) {
				Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
			}
		},
	) { padding ->
		Column(
			modifier =
				Modifier
					.padding(padding)
					.fillMaxSize(),
		) {
			// Filter Mode Selection
			Card(
				modifier =
					Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 8.dp),
				elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
			) {
				Column(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
				) {
					Row(
						modifier =
							Modifier
								.fillMaxWidth()
								.clickable { showModeDialog = true },
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = stringResource(R.string.app_selector_mode),
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.primary,
							)
							Text(
								text =
									when (tempFilterMode) {
										AppFilterMode.ALL -> stringResource(R.string.app_selector_mode_all)
										AppFilterMode.ALLOWED -> stringResource(R.string.app_selector_mode_allowed)
										AppFilterMode.DISALLOWED -> stringResource(R.string.app_selector_mode_disallowed)
									},
								style = MaterialTheme.typography.bodyMedium,
							)
							if (tempFilterMode != AppFilterMode.ALL) {
								Text(
									text = stringResource(R.string.app_selector_selected, selectedApps.size),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.primary,
								)
							}
						}
						Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			}

			// Search and Filter
			if (tempFilterMode != AppFilterMode.ALL) {
				Column(
					modifier =
						Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 4.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					TextField(
						value = searchQuery,
						onValueChange = { searchQuery = it },
						modifier = Modifier.fillMaxWidth(),
						placeholder = { Text(stringResource(R.string.app_selector_search)) },
						leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
						singleLine = true,
					)

					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier.clickable { showSystemApps = !showSystemApps },
					) {
						Switch(
							checked = showSystemApps,
							onCheckedChange = { showSystemApps = it },
						)
						Spacer(modifier = Modifier.width(4.dp))
						Text(
							text = stringResource(R.string.app_selector_system_apps),
							style = MaterialTheme.typography.bodyMedium,
						)
					}
				}
			}

			// Apps List
			if (isLoading) {
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center,
				) {
					Column(horizontalAlignment = Alignment.CenterHorizontally) {
						CircularProgressIndicator()
						Spacer(modifier = Modifier.height(16.dp))
						Text(stringResource(R.string.app_selector_loading))
					}
				}
			} else if (tempFilterMode != AppFilterMode.ALL) {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					items(filteredApps, key = { it.packageName }) { app ->
						AppListItem(
							app = app,
							isSelected = selectedApps.contains(app.packageName),
							onToggle = {
								selectedApps =
									if (selectedApps.contains(app.packageName)) {
										selectedApps - app.packageName
									} else {
										selectedApps + app.packageName
									}
							},
						)
					}
				}
			}
		}

		// Mode Selection Dialog
		if (showModeDialog) {
			AlertDialog(
				onDismissRequest = { showModeDialog = false },
				title = { Text(stringResource(R.string.app_selector_mode)) },
				text = {
					Column(modifier = Modifier.selectableGroup()) {
						AppFilterMode.entries.forEach { mode ->
							Row(
								modifier =
									Modifier
										.fillMaxWidth()
										.selectable(
											selected = tempFilterMode == mode,
											onClick = {
												val oldMode = tempFilterMode
												tempFilterMode = mode
												// Clear selections when switching modes
												if (mode == AppFilterMode.ALL) {
													selectedApps = emptySet()
												} else if (oldMode != mode) {
													selectedApps = emptySet()
												}
												showModeDialog = false
											},
											role = Role.RadioButton,
										).padding(vertical = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								RadioButton(
									selected = tempFilterMode == mode,
									onClick = null,
								)
								Spacer(modifier = Modifier.width(8.dp))
								Text(
									text =
										when (mode) {
											AppFilterMode.ALL -> stringResource(R.string.app_selector_mode_all)
											AppFilterMode.ALLOWED -> stringResource(R.string.app_selector_mode_allowed)
											AppFilterMode.DISALLOWED -> stringResource(R.string.app_selector_mode_disallowed)
										},
								)
							}
						}
					}
				},
				confirmButton = {
					TextButton(onClick = { showModeDialog = false }) {
						Text(stringResource(R.string.cancel))
					}
				},
			)
		}
	}
}

@Composable
private fun AppListItem(
	app: AppInfo,
	isSelected: Boolean,
	onToggle: () -> Unit,
) {
	Card(
		modifier =
			Modifier
				.fillMaxWidth()
				.clickable(onClick = onToggle),
		elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
	) {
		Row(
			modifier = Modifier.padding(12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Image(
				bitmap = app.icon.toBitmap(64, 64).asImageBitmap(),
				contentDescription = null,
				modifier = Modifier.size(48.dp),
			)
			Spacer(modifier = Modifier.width(16.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = app.appName,
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium,
				)
				Text(
					text = app.packageName,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Checkbox(
				checked = isSelected,
				onCheckedChange = { onToggle() },
			)
		}
	}
}
