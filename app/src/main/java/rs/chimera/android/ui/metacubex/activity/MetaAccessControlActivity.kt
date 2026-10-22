package rs.chimera.android.ui.metacubex.activity

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.ui.metacubex.design.AccessControlDesign
import rs.chimera.android.ui.metacubex.design.AccessControlSelectionPolicy
import rs.chimera.android.viewmodel.AppFilterMode

class MetaAccessControlActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: AccessControlDesign
    private var mode = AppFilterMode.ALL
    private var allowedApps = emptySet<String>()
    private var disallowedApps = emptySet<String>()
    private var savedSelection = AccessControlSelectionPolicy.Selection(
        AppFilterMode.ALL,
        emptySet(),
        emptySet(),
    )
    private var installedApps = emptyList<AccessControlDesign.InstalledApp>()
    private var query = ""
    private var showSystemApps = false
    private var sort = AccessControlDesign.Sort.NAME
    private var reverse = false
    private var loading = false
    private var saving = false
    private var loadError: String? = null
    private val uiPreferences by lazy {
        getSharedPreferences(ACCESS_CONTROL_UI_PREFERENCES, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)

        mode = loadMode()
        allowedApps = backend.settings.value.allowedApps
        disallowedApps = backend.settings.value.disallowedApps
        showSystemApps = uiPreferences.getBoolean(SHOW_SYSTEM_APPS_KEY, false)
        sort = AccessControlDesign.Sort.values().firstOrNull {
            it.name == uiPreferences.getString(SORT_KEY, null)
        } ?: AccessControlDesign.Sort.NAME
        reverse = uiPreferences.getBoolean(REVERSE_KEY, false)
        savedSelection = currentSelection()

        design = AccessControlDesign(this)
        setContentView(design.root)
        render()

        lifecycleScope.launch {
            for (request in design.requests) {
                handleRequest(request)
            }
        }
        lifecycleScope.launch { loadApps() }
    }

    private suspend fun handleRequest(request: AccessControlDesign.Request) {
        if (saving) return
        when (request) {
            AccessControlDesign.Request.NavigateBack -> navigateBack()
            AccessControlDesign.Request.Save -> save()
            AccessControlDesign.Request.RetryLoad -> loadApps()
            is AccessControlDesign.Request.SetMode -> {
                if (mode != request.mode) {
                    mode = request.mode
                    when (mode) {
                        AppFilterMode.ALL -> Unit
                        AppFilterMode.ALLOWED -> allowedApps = emptySet()
                        AppFilterMode.DISALLOWED -> disallowedApps = emptySet()
                    }
                }
                render()
            }
            is AccessControlDesign.Request.SetQuery -> {
                query = request.query
                render()
            }
            is AccessControlDesign.Request.SetShowSystemApps -> {
                showSystemApps = request.enabled
                uiPreferences.edit().putBoolean(SHOW_SYSTEM_APPS_KEY, showSystemApps).apply()
                render()
            }
            is AccessControlDesign.Request.SetSort -> {
                sort = request.sort
                uiPreferences.edit().putString(SORT_KEY, sort.name).apply()
                render()
            }
            is AccessControlDesign.Request.SetReverse -> {
                reverse = request.enabled
                uiPreferences.edit().putBoolean(REVERSE_KEY, reverse).apply()
                render()
            }
            AccessControlDesign.Request.SelectAll -> {
                if (AccessControlSelectionPolicy.canSelectApps(mode)) {
                    setSelectedApps(visibleApps().map { it.packageName }.toSet())
                }
            }
            AccessControlDesign.Request.SelectNone -> {
                if (AccessControlSelectionPolicy.canSelectApps(mode)) {
                    setSelectedApps(emptySet())
                }
            }
            AccessControlDesign.Request.SelectInvert -> {
                if (AccessControlSelectionPolicy.canSelectApps(mode)) {
                    val visiblePackages = visibleApps().map { it.packageName }.toSet()
                    setSelectedApps(visiblePackages - selectedApps())
                }
            }
            AccessControlDesign.Request.ImportFromClipboard -> {
                if (AccessControlSelectionPolicy.canSelectApps(mode)) {
                    val clipboard = getSystemService(ClipboardManager::class.java)
                    val packages = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.text?.toString().orEmpty()
                        .lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
                    setSelectedApps(visibleApps().map { it.packageName }.toSet().intersect(packages))
                }
            }
            AccessControlDesign.Request.ExportToClipboard -> {
                if (AccessControlSelectionPolicy.canSelectApps(mode)) {
                    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                        ClipData.newPlainText("packages", selectedApps().joinToString("\n")),
                    )
                    design.showToast(getString(R.string.app_selector_exported))
                }
            }
            is AccessControlDesign.Request.ToggleApp -> {
                if (AccessControlSelectionPolicy.canSelectApps(mode)) {
                    setSelectedApps(selectedApps().toggle(request.packageName, request.selected))
                }
            }
        }
    }

    private suspend fun loadApps() {
        if (loading) return
        loading = true
        loadError = null
        render()
        try {
            installedApps = withContext(Dispatchers.IO) { loadInstalledApps() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val details = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            loadError = getString(R.string.app_selector_load_error, details)
        } finally {
            loading = false
            render()
        }
    }

    private suspend fun save() {
        val selection = currentSelection()
        if (selection == savedSelection) return

        saving = true
        render()
        try {
            withContext(Dispatchers.IO) {
                backend.updateSettings(
                    SettingsPatch(
                        appFilterMode = selection.mode.name,
                        allowedApps = selection.allowedApps,
                        disallowedApps = selection.disallowedApps,
                    ),
                )
            }
            savedSelection = selection
            design.showToast(getString(R.string.app_selector_saved))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            design.showToast(
                getString(
                    R.string.cmfa_settings_save_failed,
                    error.message ?: getString(R.string.profile_unknown_error),
                ),
            )
        } finally {
            saving = false
            render()
        }
    }

    private fun navigateBack() {
        if (currentSelection() == savedSelection) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.app_selector_unsaved_title)
            .setMessage(R.string.app_selector_unsaved_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.app_selector_discard) { _, _ -> finish() }
            .show()
    }

    private fun render() {
        design.render(
            AccessControlDesign.State(
                mode = mode,
                sort = sort,
                reverse = reverse,
                apps = visibleApps(),
                selected = selectedApps(),
                showSystemApps = showSystemApps,
                query = query,
                loading = loading,
                loadError = loadError,
                saving = saving,
                dirty = currentSelection() != savedSelection,
            ),
        )
    }

    private fun currentSelection(): AccessControlSelectionPolicy.Selection =
        AccessControlSelectionPolicy.selection(mode, allowedApps, disallowedApps)

    private fun selectedApps(): Set<String> = AccessControlSelectionPolicy.selectedApps(
        mode,
        allowedApps,
        disallowedApps,
    )

    private fun setSelectedApps(selected: Set<String>) {
        if (!AccessControlSelectionPolicy.canSelectApps(mode)) return
        when (mode) {
            AppFilterMode.ALL -> return
            AppFilterMode.ALLOWED -> allowedApps = selected
            AppFilterMode.DISALLOWED -> disallowedApps = selected
        }
        render()
    }

    private fun visibleApps(): List<AccessControlDesign.InstalledApp> {
        val selected = selectedApps()
        val selectedFirst = compareByDescending<AccessControlDesign.InstalledApp> {
            it.packageName in selected
        }
        val sortComparator: Comparator<AccessControlDesign.InstalledApp> = when (sort) {
            AccessControlDesign.Sort.NAME -> compareBy { it.label.lowercase() }
            AccessControlDesign.Sort.PACKAGE_NAME -> compareBy { it.packageName }
            AccessControlDesign.Sort.INSTALL_TIME -> compareBy { it.installTime }
            AccessControlDesign.Sort.UPDATE_TIME -> compareBy { it.updateTime }
        }
        val effectiveSort = if (reverse) {
            Comparator<AccessControlDesign.InstalledApp> { first, second ->
                sortComparator.compare(second, first)
            }
        } else {
            sortComparator
        }
        return installedApps
            .asSequence()
            .filter { showSystemApps || !it.system }
            .sortedWith(selectedFirst.then(effectiveSort))
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun loadInstalledApps(): List<AccessControlDesign.InstalledApp> {
        val packageManager = packageManager
        return packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .asSequence()
            .filter { it.packageName != packageName }
            .filter { info ->
                val application = info.applicationInfo ?: return@filter false
                info.requestedPermissions?.contains(Manifest.permission.INTERNET) == true ||
                    application.uid < Process.FIRST_APPLICATION_UID
            }
            .mapNotNull { info -> info.applicationInfo?.toInstalledApp(packageManager, info) }
            .toList()
    }

    private fun ApplicationInfo.toInstalledApp(
        packageManager: PackageManager,
        packageInfo: PackageInfo,
    ): AccessControlDesign.InstalledApp? =
        runCatching {
            AccessControlDesign.InstalledApp(
                packageName = packageName,
                label = packageManager.getApplicationLabel(this).toString(),
                icon = runCatching { packageManager.getApplicationIcon(this) }
                    .getOrElse { packageManager.defaultActivityIcon },
                system = flags and ApplicationInfo.FLAG_SYSTEM != 0,
                installTime = packageInfo.firstInstallTime,
                updateTime = packageInfo.lastUpdateTime,
            )
        }.getOrNull()

    private fun loadMode(): AppFilterMode =
        runCatching {
            AppFilterMode.valueOf(backend.settings.value.appFilterMode)
        }.getOrDefault(AppFilterMode.ALL)

    private fun Set<String>.toggle(
        packageName: String,
        selected: Boolean,
    ): Set<String> = if (selected) this + packageName else this - packageName

    private companion object {
        const val ACCESS_CONTROL_UI_PREFERENCES = "meta_access_control_ui"
        const val SHOW_SYSTEM_APPS_KEY = "show_system_apps"
        const val SORT_KEY = "sort"
        const val REVERSE_KEY = "reverse"
    }
}
