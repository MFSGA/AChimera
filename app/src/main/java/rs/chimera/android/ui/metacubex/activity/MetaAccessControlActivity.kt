package rs.chimera.android.ui.metacubex.activity

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import rs.chimera.android.viewmodel.AppFilterMode

class MetaAccessControlActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private lateinit var design: AccessControlDesign
    private var mode = AppFilterMode.ALL
    private var allowedApps = emptySet<String>()
    private var disallowedApps = emptySet<String>()
    private var savedSelection = FilterSelection(AppFilterMode.ALL, emptySet(), emptySet())
    private var installedApps = emptyList<AccessControlDesign.InstalledApp>()
    private var query = ""
    private var showSystemApps = false
    private var loading = false
    private var saving = false
    private var loadError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)

        mode = loadMode()
        allowedApps = prefs.getStringSet("allowed_apps", emptySet()).orEmpty().toSet()
        disallowedApps = prefs.getStringSet("disallowed_apps", emptySet()).orEmpty().toSet()
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
                mode = request.mode
                render()
            }
            is AccessControlDesign.Request.SetQuery -> {
                query = request.query
                render()
            }
            is AccessControlDesign.Request.SetShowSystemApps -> {
                showSystemApps = request.enabled
                render()
            }
            is AccessControlDesign.Request.ToggleApp -> {
                when (mode) {
                    AppFilterMode.ALLOWED -> {
                        allowedApps = allowedApps.toggle(request.packageName, request.selected)
                    }
                    AppFilterMode.DISALLOWED -> {
                        disallowedApps = disallowedApps.toggle(request.packageName, request.selected)
                    }
                    AppFilterMode.ALL -> Unit
                }
                render()
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
        val selected = when (mode) {
            AppFilterMode.ALL -> emptySet()
            AppFilterMode.ALLOWED -> allowedApps
            AppFilterMode.DISALLOWED -> disallowedApps
        }
        val filteredApps = if (mode == AppFilterMode.ALL) {
            emptyList()
        } else {
            installedApps.filter { app ->
                (showSystemApps || !app.system) &&
                    (
                        query.isBlank() ||
                            app.label.contains(query, ignoreCase = true) ||
                            app.packageName.contains(query, ignoreCase = true)
                    )
            }
        }
        design.render(
            AccessControlDesign.State(
                mode = mode,
                apps = filteredApps,
                selected = selected,
                showSystemApps = showSystemApps,
                loading = loading,
                loadError = loadError,
                saving = saving,
                dirty = currentSelection() != savedSelection,
            ),
        )
    }

    private fun currentSelection(): FilterSelection =
        when (mode) {
            AppFilterMode.ALL -> FilterSelection(mode, emptySet(), emptySet())
            AppFilterMode.ALLOWED -> FilterSelection(mode, allowedApps, emptySet())
            AppFilterMode.DISALLOWED -> FilterSelection(mode, emptySet(), disallowedApps)
        }

    @Suppress("DEPRECATION")
    private fun loadInstalledApps(): List<AccessControlDesign.InstalledApp> {
        val packageManager = packageManager
        return packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != packageName }
            .mapNotNull { info -> info.toInstalledApp(packageManager) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun ApplicationInfo.toInstalledApp(
        packageManager: PackageManager,
    ): AccessControlDesign.InstalledApp? =
        runCatching {
            AccessControlDesign.InstalledApp(
                packageName = packageName,
                label = packageManager.getApplicationLabel(this).toString(),
                system = flags and ApplicationInfo.FLAG_SYSTEM != 0,
            )
        }.getOrNull()

    private fun loadMode(): AppFilterMode =
        runCatching {
            AppFilterMode.valueOf(prefs.getString("app_filter_mode", "ALL") ?: "ALL")
        }.getOrDefault(AppFilterMode.ALL)

    private fun Set<String>.toggle(
        packageName: String,
        selected: Boolean,
    ): Set<String> = if (selected) this + packageName else this - packageName

    private data class FilterSelection(
        val mode: AppFilterMode,
        val allowedApps: Set<String>,
        val disallowedApps: Set<String>,
    )
}
