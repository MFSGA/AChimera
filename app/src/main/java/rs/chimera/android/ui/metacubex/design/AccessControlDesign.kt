package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root
import rs.chimera.android.viewmodel.AppFilterMode

class AccessControlDesign(context: Context) : Design<AccessControlDesign.Request>(context) {
    data class InstalledApp(
        val packageName: String,
        val label: String,
        val system: Boolean,
    )

    data class State(
        val mode: AppFilterMode,
        val apps: List<InstalledApp>,
        val selected: Set<String>,
        val showSystemApps: Boolean,
        val loading: Boolean,
        val loadError: String?,
        val saving: Boolean,
        val dirty: Boolean,
    )

    sealed class Request {
        data object NavigateBack : Request()
        data object Save : Request()
        data object RetryLoad : Request()
        data class SetMode(val mode: AppFilterMode) : Request()
        data class SetQuery(val query: String) : Request()
        data class SetShowSystemApps(val enabled: Boolean) : Request()
        data class ToggleApp(val packageName: String, val selected: Boolean) : Request()
    }

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_access_control,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<MaterialToolbar>(R.id.toolbar)
    private val filterMode = root.findViewById<RadioGroup>(R.id.filter_mode)
    private val controls = root.findViewById<LinearLayout>(R.id.app_controls)
    private val search = root.findViewById<EditText>(R.id.search_apps)
    private val showSystem = root.findViewById<SwitchMaterial>(R.id.show_system_apps)
    private val selectedCount = root.findViewById<TextView>(R.id.selected_count)
    private val appList = root.findViewById<ListView>(R.id.app_list)
    private val stateContainer = root.findViewById<LinearLayout>(R.id.state_container)
    private val loading = root.findViewById<ProgressBar>(R.id.loading)
    private val stateMessage = root.findViewById<TextView>(R.id.empty_message)
    private val retry = root.findViewById<MaterialButton>(R.id.retry_load)
    private val save = root.findViewById<MaterialButton>(R.id.save_apps)
    private val adapter = ArrayAdapter<String>(
        context,
        android.R.layout.simple_list_item_multiple_choice,
        mutableListOf(),
    )
    private var displayedApps = emptyList<InstalledApp>()
    private var rendering = false

    init {
        appList.adapter = adapter
        appList.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        toolbar.setNavigationOnClickListener { request(Request.NavigateBack) }
        save.setOnClickListener { request(Request.Save) }
        retry.setOnClickListener { request(Request.RetryLoad) }
        filterMode.setOnCheckedChangeListener { _, checkedId ->
            if (rendering) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.mode_allowed -> AppFilterMode.ALLOWED
                R.id.mode_disallowed -> AppFilterMode.DISALLOWED
                else -> AppFilterMode.ALL
            }
            request(Request.SetMode(mode))
        }
        search.doAfterTextChanged { request(Request.SetQuery(it?.toString().orEmpty())) }
        showSystem.setOnCheckedChangeListener { _, checked ->
            if (!rendering) request(Request.SetShowSystemApps(checked))
        }
        appList.setOnItemClickListener { _, _, position, _ ->
            displayedApps.getOrNull(position)?.let { app ->
                request(Request.ToggleApp(app.packageName, appList.isItemChecked(position)))
            }
        }
    }

    fun render(state: State) {
        rendering = true
        filterMode.check(
            when (state.mode) {
                AppFilterMode.ALL -> R.id.mode_all
                AppFilterMode.ALLOWED -> R.id.mode_allowed
                AppFilterMode.DISALLOWED -> R.id.mode_disallowed
            },
        )
        showSystem.isChecked = state.showSystemApps
        val showApps = state.mode != AppFilterMode.ALL
        val showState = showApps && (state.loading || state.loadError != null || state.apps.isEmpty())
        controls.visibility = if (showApps) View.VISIBLE else View.GONE
        appList.visibility = if (showApps && !showState) View.VISIBLE else View.GONE
        stateContainer.visibility = if (showState) View.VISIBLE else View.GONE
        loading.visibility = if (state.loading) View.VISIBLE else View.GONE
        stateMessage.visibility = if (state.loading) View.GONE else View.VISIBLE
        stateMessage.text = state.loadError ?: context.getString(R.string.app_selector_empty)
        retry.visibility = if (state.loadError != null) View.VISIBLE else View.GONE
        selectedCount.text = context.getString(R.string.app_selector_selected, state.selected.size)

        filterMode.isEnabled = !state.saving
        search.isEnabled = !state.saving
        showSystem.isEnabled = !state.saving
        appList.isEnabled = !state.saving
        retry.isEnabled = !state.saving
        save.isEnabled = state.dirty && !state.saving
        save.setText(if (state.saving) R.string.app_selector_saving else R.string.save)

        displayedApps = state.apps
        adapter.clear()
        adapter.addAll(state.apps.map { "${it.label}\n${it.packageName}" })
        adapter.notifyDataSetChanged()
        for (index in state.apps.indices) {
            appList.setItemChecked(index, state.apps[index].packageName in state.selected)
        }
        rendering = false
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }
}
