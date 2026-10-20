package rs.chimera.android.ui.metacubex.design

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.adapter.AccessControlAdapter
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root
import rs.chimera.android.viewmodel.AppFilterMode

class AccessControlDesign(context: Context) : Design<AccessControlDesign.Request>(context) {
    data class InstalledApp(
        val packageName: String,
        val label: String,
        val icon: Drawable,
        val system: Boolean,
        val installTime: Long,
        val updateTime: Long,
    )

    data class State(
        val mode: AppFilterMode,
        val sort: Sort,
        val reverse: Boolean,
        val apps: List<InstalledApp>,
        val selected: Set<String>,
        val showSystemApps: Boolean,
        val query: String,
        val loading: Boolean,
        val loadError: String?,
        val saving: Boolean,
        val dirty: Boolean,
    )

    enum class Sort {
        NAME,
        PACKAGE_NAME,
        INSTALL_TIME,
        UPDATE_TIME,
    }

    sealed class Request {
        data object NavigateBack : Request()
        data object Save : Request()
        data object RetryLoad : Request()
        data class SetMode(val mode: AppFilterMode) : Request()
        data class SetQuery(val query: String) : Request()
        data class SetShowSystemApps(val enabled: Boolean) : Request()
        data class SetSort(val sort: Sort) : Request()
        data class SetReverse(val enabled: Boolean) : Request()
        data object SelectAll : Request()
        data object SelectNone : Request()
        data object SelectInvert : Request()
        data object ImportFromClipboard : Request()
        data object ExportToClipboard : Request()
        data class ToggleApp(val packageName: String, val selected: Boolean) : Request()
    }

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_access_control,
        context.root,
        false,
    )

    private val toolbarBack = root.findViewById<ImageView>(R.id.toolbar_back)
    private val toolbarSearch = root.findViewById<ImageView>(R.id.access_control_search_button)
    private val toolbarMenu = root.findViewById<ImageView>(R.id.access_control_menu_button)
    private val appList = root.findViewById<RecyclerView>(R.id.app_list)
    private val stateContainer = root.findViewById<LinearLayout>(R.id.state_container)
    private val loading = root.findViewById<ProgressBar>(R.id.loading)
    private val stateMessage = root.findViewById<TextView>(R.id.empty_message)
    private val retry = root.findViewById<MaterialButton>(R.id.retry_load)
    private val adapter = AccessControlAdapter { app, selected ->
        request(Request.ToggleApp(app.packageName, selected))
    }
    private var searchDialog: Dialog? = null
    private var searchInput: EditText? = null
    private var searchAdapter: AccessControlAdapter? = null
    private var displayedApps = emptyList<InstalledApp>()
    private var displayedSelection = emptySet<String>()
    private var displayedSearchApps = emptyList<InstalledApp>()
    private var displayedSearchSelection = emptySet<String>()
    private var currentState: State? = null
    private var rendering = false

    init {
        root.applySystemBarInsets()
        appList.adapter = adapter
        appList.layoutManager = LinearLayoutManager(context)
        toolbarBack.setOnClickListener { request(Request.NavigateBack) }
        toolbarSearch.setOnClickListener { showSearchDialog() }
        toolbarMenu.setOnClickListener { showOptionsMenu() }
        retry.setOnClickListener { request(Request.RetryLoad) }
    }

    fun render(state: State) {
        currentState = state
        rendering = true
        val canSelectApps = AccessControlSelectionPolicy.canSelectApps(state.mode)
        val showState = state.loading || state.loadError != null || state.apps.isEmpty()
        toolbarSearch.visibility = if (canSelectApps) View.VISIBLE else View.GONE
        appList.visibility = if (canSelectApps && !showState) View.VISIBLE else View.GONE
        stateContainer.visibility = if (canSelectApps && showState) View.VISIBLE else View.GONE
        loading.visibility = if (state.loading) View.VISIBLE else View.GONE
        stateMessage.visibility = if (state.loading) View.GONE else View.VISIBLE
        stateMessage.text = state.loadError ?: context.getString(R.string.app_selector_empty)
        retry.visibility = if (state.loadError != null) View.VISIBLE else View.GONE
        retry.isEnabled = !state.saving

        if (displayedApps != state.apps || displayedSelection != state.selected) {
            displayedApps = state.apps
            displayedSelection = state.selected
            adapter.submitState(state.apps, state.selected)
        }
        val searchApps = if (state.query.isBlank()) {
            emptyList()
        } else {
            state.apps.filter { app ->
                app.label.contains(state.query, ignoreCase = true) ||
                    app.packageName.contains(state.query, ignoreCase = true)
            }
        }
        if (displayedSearchApps != searchApps || displayedSearchSelection != state.selected) {
            displayedSearchApps = searchApps
            displayedSearchSelection = state.selected
            searchAdapter?.submitState(searchApps, state.selected)
        }
        if (searchInput?.text?.toString() != state.query) {
            searchInput?.setText(state.query)
            searchInput?.setSelection(state.query.length)
        }
        rendering = false
    }

    private fun showOptionsMenu() {
        val state = currentState ?: return
        val canSelectApps = AccessControlSelectionPolicy.canSelectApps(state.mode)
        val popup = PopupMenu(context, toolbarMenu)
        popup.menuInflater.inflate(R.menu.menu_access_control, popup.menu)
        popup.menu.apply {
            findItem(R.id.access_control_mode_all).isChecked = state.mode == AppFilterMode.ALL
            findItem(R.id.access_control_mode_allowed).isChecked = state.mode == AppFilterMode.ALLOWED
            findItem(R.id.access_control_mode_disallowed).isChecked = state.mode == AppFilterMode.DISALLOWED
            findItem(R.id.access_control_system_apps).isChecked = state.showSystemApps
            findItem(R.id.access_control_sort_name).isChecked = state.sort == Sort.NAME
            findItem(R.id.access_control_sort_package).isChecked = state.sort == Sort.PACKAGE_NAME
            findItem(R.id.access_control_sort_install_time).isChecked = state.sort == Sort.INSTALL_TIME
            findItem(R.id.access_control_sort_update_time).isChecked = state.sort == Sort.UPDATE_TIME
            findItem(R.id.access_control_reverse).isChecked = state.reverse
            findItem(R.id.access_control_select_all).isEnabled = canSelectApps && !state.saving
            findItem(R.id.access_control_select_none).isEnabled = canSelectApps && !state.saving
            findItem(R.id.access_control_select_invert).isEnabled = canSelectApps && !state.saving
            findItem(R.id.access_control_import).isEnabled = canSelectApps && !state.saving
            findItem(R.id.access_control_export).isEnabled = canSelectApps && !state.saving
            findItem(R.id.access_control_save).apply {
                isVisible = state.dirty || state.saving
                isEnabled = state.dirty && !state.saving
                title = context.getString(if (state.saving) R.string.app_selector_saving else R.string.save)
            }
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.access_control_mode_all -> {
                    request(Request.SetMode(AppFilterMode.ALL))
                    true
                }
                R.id.access_control_mode_allowed -> {
                    request(Request.SetMode(AppFilterMode.ALLOWED))
                    true
                }
                R.id.access_control_mode_disallowed -> {
                    request(Request.SetMode(AppFilterMode.DISALLOWED))
                    true
                }
                R.id.access_control_system_apps -> {
                    request(Request.SetShowSystemApps(!state.showSystemApps))
                    true
                }
                R.id.access_control_sort_name -> {
                    request(Request.SetSort(Sort.NAME))
                    true
                }
                R.id.access_control_sort_package -> {
                    request(Request.SetSort(Sort.PACKAGE_NAME))
                    true
                }
                R.id.access_control_sort_install_time -> {
                    request(Request.SetSort(Sort.INSTALL_TIME))
                    true
                }
                R.id.access_control_sort_update_time -> {
                    request(Request.SetSort(Sort.UPDATE_TIME))
                    true
                }
                R.id.access_control_reverse -> {
                    request(Request.SetReverse(!state.reverse))
                    true
                }
                R.id.access_control_select_all -> {
                    request(Request.SelectAll)
                    true
                }
                R.id.access_control_select_none -> {
                    request(Request.SelectNone)
                    true
                }
                R.id.access_control_select_invert -> {
                    request(Request.SelectInvert)
                    true
                }
                R.id.access_control_import -> {
                    request(Request.ImportFromClipboard)
                    true
                }
                R.id.access_control_export -> {
                    request(Request.ExportToClipboard)
                    true
                }
                R.id.access_control_save -> {
                    request(Request.Save)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showSearchDialog() {
        if (searchDialog?.isShowing == true) return

        val dialog = Dialog(context, R.style.Theme_Chimera_MetaCubeX_FullScreenDialog)
        val content = context.layoutInflater.inflate(R.layout.meta_dialog_access_control_search, null)
        val close = content.findViewById<ImageView>(R.id.close_search)
        val input = content.findViewById<EditText>(R.id.search_keyword)
        val results = content.findViewById<RecyclerView>(R.id.search_results)
        val resultAdapter = AccessControlAdapter { app, selected ->
            request(Request.ToggleApp(app.packageName, selected))
        }
        searchDialog = dialog
        searchInput = input
        searchAdapter = resultAdapter
        results.adapter = resultAdapter
        results.layoutManager = LinearLayoutManager(context)
        content.applySystemBarInsets()
        input.setText("")
        input.doAfterTextChanged {
            if (!rendering) request(Request.SetQuery(it?.toString().orEmpty()))
        }
        close.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(content)
        dialog.setOnDismissListener {
            searchDialog = null
            searchInput = null
            searchAdapter = null
            displayedSearchApps = emptyList()
            displayedSearchSelection = emptySet()
            request(Request.SetQuery(""))
        }
        dialog.setOnShowListener {
            input.post {
                input.requestFocus()
                input.postDelayed({
                    context.getSystemService(InputMethodManager::class.java)
                        ?.showSoftInput(input, 0)
                }, 300)
            }
        }
        dialog.window?.apply {
            val surfaceColor = MaterialColors.getColor(content, com.google.android.material.R.attr.colorSurface)
            setBackgroundDrawable(ColorDrawable(surfaceColor))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            statusBarColor = android.graphics.Color.TRANSPARENT
            decorView.setBackgroundColor(surfaceColor)
            setDimAmount(0f)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }
            navigationBarColor = surfaceColor
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val lightSystemBars = nightMode != Configuration.UI_MODE_NIGHT_YES
            WindowInsetsControllerCompat(this, decorView).apply {
                isAppearanceLightStatusBars = lightSystemBars
                isAppearanceLightNavigationBars = lightSystemBars
            }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }
}
