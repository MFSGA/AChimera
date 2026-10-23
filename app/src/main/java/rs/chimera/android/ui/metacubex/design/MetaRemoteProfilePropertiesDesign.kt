package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import rs.chimera.android.R
import rs.chimera.android.backend.ProfileRemotePolicy
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

internal class MetaRemoteProfilePropertiesDesign(
    context: Context,
    initialUrl: String,
    initialName: String = context.getString(R.string.profile_properties_new_name),
    initialAutoUpdate: Boolean = false,
    initialUserAgent: String = "",
    initialProxyUrl: String = "",
    restoredState: Bundle? = null,
    private val snapshotMode: Boolean = false,
) : Design<MetaRemoteProfilePropertiesDesign.Request>(context) {
    sealed class Request {
        data object NavigateBack : Request()
        data object BrowseFiles : Request()
        data class Submit(val form: MetaRemoteProfileForm) : Request()
    }

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_remote_profile_properties,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<View>(R.id.remote_profile_toolbar)
    private val scrollRoot = root.findViewById<ScrollView>(R.id.remote_profile_scroll)
    private val saveContainer = root.findViewById<View>(R.id.remote_profile_save_container)
    private val saveAction = root.findViewById<ImageView>(R.id.remote_profile_save)
    private val saveProgress = root.findViewById<ProgressBar>(R.id.remote_profile_save_progress)
    private val nameRow = root.findViewById<View>(R.id.property_name)
    private val urlRow = root.findViewById<View>(R.id.property_url)
    private val optionsRow = root.findViewById<View>(R.id.property_options)
    private val autoUpdateRow = root.findViewById<View>(R.id.property_auto_update)
    private val browseRow = root.findViewById<View>(R.id.property_browse)

    private val originalName = restoredState?.getString(STATE_ORIGINAL_NAME) ?: initialName
    private val originalUrl = restoredState?.getString(STATE_ORIGINAL_URL) ?: initialUrl
    private val originalAutoUpdate = restoredState?.getBoolean(STATE_ORIGINAL_AUTO_UPDATE) ?: initialAutoUpdate
    private val originalUserAgent = restoredState?.getString(STATE_ORIGINAL_USER_AGENT) ?: initialUserAgent
    private val originalProxyUrl = restoredState?.getString(STATE_ORIGINAL_PROXY_URL) ?: initialProxyUrl
    private var name = restoredState?.getString(STATE_NAME) ?: originalName
    private var url = restoredState?.getString(STATE_URL) ?: originalUrl
    private var autoUpdate = if (snapshotMode) {
        false
    } else {
        restoredState?.getBoolean(STATE_AUTO_UPDATE) ?: originalAutoUpdate
    }
    private var userAgent = restoredState?.getString(STATE_USER_AGENT) ?: originalUserAgent
    private var proxyUrl = restoredState?.getString(STATE_PROXY_URL) ?: originalProxyUrl
    private var appBarElevated = false
    private var appBarElevationAnimator: ValueAnimator? = null

    init {
        applySystemBarInsets()
        root.findViewById<TextView>(R.id.toolbar_title).setText(R.string.profile_properties_title)
        root.findViewById<TextView>(R.id.remote_profile_tips).text = HtmlCompat.fromHtml(
            context.getString(
                if (snapshotMode) {
                    R.string.profile_provider_snapshot_tip
                } else {
                    R.string.profile_properties_tip
                },
            ),
            HtmlCompat.FROM_HTML_MODE_COMPACT,
        )
        if (snapshotMode) autoUpdateRow.visibility = View.GONE
        bindProperty(nameRow, R.drawable.ic_outline_label, R.string.profile_properties_name)
        bindProperty(urlRow, R.drawable.ic_outline_inbox, R.string.profile_properties_url)
        bindProperty(
            optionsRow,
            R.drawable.ic_baseline_settings,
            R.string.profile_properties_client_options,
        )
        bindProperty(
            autoUpdateRow,
            R.drawable.ic_outline_update,
            R.string.profile_properties_auto_update,
        )
        root.findViewById<View>(R.id.toolbar_navigation)
            .setOnClickListener { request(Request.NavigateBack) }
        saveAction.setOnClickListener { submit() }
        nameAction().setOnClickListener { editName() }
        urlAction().setOnClickListener { editUrl() }
        optionsAction().setOnClickListener { editClientOptions() }
        autoUpdateAction().setOnClickListener { editAutoUpdate() }
        bindBrowseRow()
        updateValues()
        scrollRoot.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            setToolbarElevated(scrollY > 0)
        }
    }

    fun hasChanges(): Boolean = name != originalName ||
        url != originalUrl ||
        autoUpdate != originalAutoUpdate ||
        userAgent != originalUserAgent ||
        proxyUrl != originalProxyUrl

    fun saveState(outState: Bundle) {
        outState.putString(STATE_ORIGINAL_NAME, originalName)
        outState.putString(STATE_ORIGINAL_URL, originalUrl)
        outState.putBoolean(STATE_ORIGINAL_AUTO_UPDATE, originalAutoUpdate)
        outState.putString(STATE_ORIGINAL_USER_AGENT, originalUserAgent)
        outState.putString(STATE_ORIGINAL_PROXY_URL, originalProxyUrl)
        outState.putString(STATE_NAME, name)
        outState.putString(STATE_URL, url)
        outState.putBoolean(STATE_AUTO_UPDATE, autoUpdate)
        outState.putString(STATE_USER_AGENT, userAgent)
        outState.putString(STATE_PROXY_URL, proxyUrl)
    }

    fun setSubmitting(submitting: Boolean) {
        saveAction.visibility = if (submitting) View.GONE else View.VISIBLE
        saveProgress.visibility = if (submitting) View.VISIBLE else View.GONE
        listOf(nameRow, urlRow, optionsRow, autoUpdateRow).forEach { row ->
            row.findViewById<View>(R.id.property_action).isEnabled = !submitting
        }
        root.findViewById<View>(R.id.toolbar_navigation).isEnabled = !submitting
    }

    fun showToast(message: CharSequence) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun bindProperty(row: View, icon: Int, title: Int) {
        row.findViewById<View>(R.id.property_icon).setBackgroundResource(icon)
        row.findViewById<TextView>(R.id.property_title).setText(title)
        if (row == urlRow) {
            row.findViewById<TextView>(R.id.property_value).hint =
                context.getString(R.string.profile_properties_url_placeholder)
        }
    }

    private fun applySystemBarInsets() {
        val scrollTopPadding = scrollRoot.paddingTop
        val toolbarHeight = toolbar.layoutParams.height
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            scrollRoot.setPadding(
                scrollRoot.paddingLeft,
                scrollTopPadding + bars.top,
                scrollRoot.paddingRight,
                bars.bottom,
            )
            toolbar.setPadding(
                toolbar.paddingLeft,
                bars.top,
                toolbar.paddingRight,
                toolbar.paddingBottom,
            )
            toolbar.layoutParams = toolbar.layoutParams.apply {
                height = toolbarHeight + bars.top
            }
            val contentOffset = bars.top / 2f
            toolbar.findViewById<View>(R.id.toolbar_navigation).translationY = contentOffset
            toolbar.findViewById<TextView>(R.id.toolbar_title).translationY = contentOffset
            saveContainer.translationY = contentOffset
            insets
        }
        root.post { ViewCompat.requestApplyInsets(root) }
    }

    private fun bindBrowseRow() {
        browseRow.setOnClickListener { request(Request.BrowseFiles) }
        browseRow.findViewById<View>(R.id.property_label_icon)
            .setBackgroundResource(R.drawable.ic_outline_folder)
        browseRow.findViewById<TextView>(R.id.property_label_title)
            .setText(R.string.profile_properties_browse_files)
        browseRow.findViewById<TextView>(R.id.property_label_subtext)
            .setText(R.string.profile_properties_browse_summary)
    }

    private fun updateValues() {
        nameRow.findViewById<TextView>(R.id.property_value).text = name
        urlRow.findViewById<TextView>(R.id.property_value).text = url
        optionsRow.findViewById<TextView>(R.id.property_value).setText(
            if (userAgent.isBlank() && proxyUrl.isBlank()) {
                R.string.profile_properties_options_default
            } else {
                R.string.profile_properties_options_configured
            },
        )
        autoUpdateRow.findViewById<TextView>(R.id.property_value).setText(
            if (autoUpdate) {
                R.string.profile_properties_auto_update_enabled
            } else {
                R.string.profile_properties_auto_update_disabled
            },
        )
    }

    private fun nameAction() = nameRow.findViewById<View>(R.id.property_action)
    private fun urlAction() = urlRow.findViewById<View>(R.id.property_action)
    private fun optionsAction() = optionsRow.findViewById<View>(R.id.property_action)
    private fun autoUpdateAction() = autoUpdateRow.findViewById<View>(R.id.property_action)

    private fun editName() = editText(
        title = R.string.profile_properties_name,
        hint = R.string.profile_properties_new_name,
        value = name,
    ) {
        name = it
        updateValues()
    }

    private fun editUrl() = editText(
        title = R.string.profile_properties_url,
        hint = R.string.profile_url_hint,
        value = url,
        uriInput = true,
    ) {
        url = it
        updateValues()
    }

    private fun editText(
        title: Int,
        hint: Int,
        value: String,
        uriInput: Boolean = false,
        onSave: (String) -> Unit,
    ) {
        val input = EditText(context).apply {
            setText(value)
            setSelectAllOnFocus(true)
            this.hint = context.getString(hint)
            if (uriInput) configureRemoteInput()
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ -> onSave(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editClientOptions() {
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        val userAgentInput = EditText(context).apply {
            setText(userAgent)
            hint = context.getString(R.string.profile_user_agent_hint)
            configureRemoteInput()
        }
        val proxyInput = EditText(context).apply {
            setText(proxyUrl)
            hint = context.getString(R.string.profile_proxy_hint)
            configureRemoteInput()
        }
        fields.addView(userAgentInput)
        fields.addView(proxyInput)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.profile_properties_client_options)
            .setView(fields)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nextProxy = proxyInput.text.toString().trim()
                if (nextProxy.isNotEmpty() && !ProfileRemotePolicy.isValidProxyUrl(nextProxy)) {
                    proxyInput.error = context.getString(R.string.profile_proxy_invalid)
                    return@setOnClickListener
                }
                userAgent = userAgentInput.text.toString().trim()
                proxyUrl = nextProxy
                updateValues()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun editAutoUpdate() {
        val enabled = CheckBox(context).apply {
            text = context.getString(R.string.profile_auto_update)
            isChecked = autoUpdate
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.profile_properties_auto_update)
            .setView(enabled)
            .setPositiveButton(R.string.save) { _, _ ->
                autoUpdate = enabled.isChecked
                updateValues()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun submit() {
        val form = MetaRemoteProfileForm(
            name = name.trim().ifEmpty { null },
            url = url.trim(),
            autoUpdate = autoUpdate && !snapshotMode,
            userAgent = userAgent.trim().ifEmpty { null },
            proxyUrl = proxyUrl.trim().ifEmpty { null },
        )
        when {
            form.name.isNullOrBlank() -> showToast(context.getString(R.string.profile_properties_name_empty))
            !ProfileRemotePolicy.isValidUrl(form.url) -> showToast(context.getString(R.string.profile_url_invalid))
            form.proxyUrl != null && !ProfileRemotePolicy.isValidProxyUrl(form.proxyUrl) ->
                showToast(context.getString(R.string.profile_proxy_invalid))
            else -> request(Request.Submit(form))
        }
    }

    private fun setToolbarElevated(elevated: Boolean) {
        if (appBarElevated == elevated) return
        appBarElevated = elevated
        appBarElevationAnimator?.cancel()
        val targetElevation = if (elevated) {
            5 * context.resources.displayMetrics.density
        } else {
            0f
        }
        appBarElevationAnimator = ValueAnimator.ofFloat(toolbar.elevation, targetElevation).apply {
            duration = 300L
            addUpdateListener { toolbar.elevation = it.animatedValue as Float }
            start()
        }
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    private fun EditText.configureRemoteInput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_URI or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    companion object {
        const val STATE_ORIGINAL_NAME = "remote_profile_original_name"
        const val STATE_ORIGINAL_URL = "remote_profile_original_url"
        const val STATE_ORIGINAL_AUTO_UPDATE = "remote_profile_original_auto_update"
        const val STATE_ORIGINAL_USER_AGENT = "remote_profile_original_user_agent"
        const val STATE_ORIGINAL_PROXY_URL = "remote_profile_original_proxy_url"
        const val STATE_NAME = "remote_profile_name"
        const val STATE_URL = "remote_profile_url"
        const val STATE_AUTO_UPDATE = "remote_profile_auto_update"
        const val STATE_USER_AGENT = "remote_profile_user_agent"
        const val STATE_PROXY_URL = "remote_profile_proxy_url"
    }
}
