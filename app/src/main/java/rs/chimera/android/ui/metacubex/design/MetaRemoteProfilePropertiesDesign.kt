package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import rs.chimera.android.R
import rs.chimera.android.backend.ProfileRemotePolicy
import rs.chimera.android.backend.model.ProfileUpdateInterval
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
    private val localProfile: Boolean = false,
    private val existingProfile: Boolean = false,
    initialAutoUpdateIntervalMillis: Long = ProfileUpdateInterval.DEFAULT_MILLIS,
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
    private val originalInterval = restoredState?.getLong(STATE_ORIGINAL_INTERVAL, initialAutoUpdateIntervalMillis)
        ?: initialAutoUpdateIntervalMillis
    private var intervalMillis = restoredState?.getLong(STATE_INTERVAL, originalInterval) ?: originalInterval
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
    private var editingAllowed = true
    private var submitting = false
    private var textInputDialog: AlertDialog? = null

    init {
        applySystemBarInsets()
        root.findViewById<TextView>(R.id.toolbar_title).setText(R.string.profile_properties_screen_title)
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
        optionsRow.visibility = View.GONE
        bindProperty(nameRow, R.drawable.ic_outline_label, R.string.profile_properties_name)
        bindProperty(urlRow, R.drawable.ic_outline_inbox, R.string.profile_properties_url)
        root.findViewById<View>(R.id.property_age)?.let { row ->
            bindProperty(row, R.drawable.ic_baseline_key, R.string.profile_properties_age)
            row.findViewById<TextView>(R.id.property_value).setText(R.string.not_available)
            row.findViewById<View>(R.id.property_action).isEnabled = false
            row.alpha = 0.33f
        }
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
        intervalMillis != originalInterval ||
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
        outState.putLong(STATE_ORIGINAL_INTERVAL, originalInterval)
        outState.putLong(STATE_INTERVAL, intervalMillis)
        outState.putString(STATE_USER_AGENT, userAgent)
        outState.putString(STATE_PROXY_URL, proxyUrl)
    }

    fun setSubmitting(submitting: Boolean) {
        this.submitting = submitting
        if (submitting) textInputDialog?.dismiss()
        saveAction.visibility = if (submitting) View.GONE else View.VISIBLE
        saveProgress.visibility = if (submitting) View.VISIBLE else View.GONE
        updateValues()
        root.findViewById<View>(R.id.toolbar_navigation).isEnabled = !submitting
    }

    fun setEditingEnabled(enabled: Boolean) {
        editingAllowed = enabled
        if (!enabled) textInputDialog?.dismiss()
        updateValues()
    }

    private fun renderAvailability() {
        val enabled = editingAllowed && !submitting
        saveAction.isEnabled = enabled
        listOf(nameRow, urlRow, optionsRow, autoUpdateRow).forEach { row ->
            val available = editingAllowed && (row == nameRow || !localProfile)
            row.findViewById<View>(R.id.property_action).isEnabled = available && !submitting
            row.alpha = if (available) 1f else 0.33f
            if (!available) row.findViewById<TextView>(R.id.property_value).setText(R.string.not_available)
        }
        browseRow.isEnabled = !submitting
        browseRow.alpha = if (browseRow.isEnabled) 1f else 0.33f
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
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val horizontalInsets = view.landscapeSystemBarInsets(bars)
            root.setPaddingRelative(horizontalInsets.start, 0, horizontalInsets.end, 0)
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
            insets
        }
        root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    private fun bindBrowseRow() {
        browseRow.setOnClickListener { request(Request.BrowseFiles) }
        browseRow.findViewById<View>(R.id.property_label_icon)
            .setBackgroundResource(R.drawable.ic_outline_folder)
        browseRow.findViewById<TextView>(R.id.property_label_title)
            .setText(R.string.profile_properties_browse_files)
        browseRow.findViewById<TextView>(R.id.property_label_subtext)
            .setText(
                if (existingProfile) {
                    R.string.profile_properties_files_available
                } else {
                    R.string.profile_properties_browse_summary
                },
            )
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
        autoUpdateRow.findViewById<TextView>(R.id.property_value).text =
            if (autoUpdate) {
                context.getString(R.string.profile_properties_update_minutes, intervalMillis / ProfileUpdateInterval.MINUTE_MILLIS)
            } else {
                context.getString(R.string.profile_properties_auto_update_disabled)
            }
        renderAvailability()
    }

    private fun nameAction() = nameRow.findViewById<View>(R.id.property_action)
    private fun urlAction() = urlRow.findViewById<View>(R.id.property_action)
    private fun optionsAction() = optionsRow.findViewById<View>(R.id.property_action)
    private fun autoUpdateAction() = autoUpdateRow.findViewById<View>(R.id.property_action)

    private fun editName() = editText(
        title = R.string.profile_properties_name,
        hint = R.string.profile_properties_title,
        error = R.string.profile_properties_name_input_error,
        value = name,
        validator = String::isNotBlank,
    ) {
        name = it
        updateValues()
    }

    private fun editUrl() = editText(
        title = R.string.profile_properties_url,
        hint = R.string.profile_properties_url_input_hint,
        error = R.string.profile_properties_url_placeholder,
        value = url,
        uriInput = true,
        validator = { ProfileRemotePolicy.isValidUrl(it.trim()) },
    ) {
        url = it
        updateValues()
    }

    private fun editText(
        title: Int,
        hint: Int,
        error: Int,
        value: String,
        uriInput: Boolean = false,
        validator: (String) -> Boolean,
        onSave: (String) -> Unit,
    ) {
        if (!editingAllowed || submitting) return
        textInputDialog?.dismiss()
        val content = context.layoutInflater.inflate(R.layout.meta_dialog_text_field, context.root, false)
        val field = content.findViewById<EditText>(R.id.meta_text_field)
        val layout = content.findViewById<TextInputLayout>(R.id.meta_text_layout)
        if (uriInput) field.configureRemoteInput()
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(content)
            .setCancelable(true)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = field.text?.toString().orEmpty()
                if (validator(text)) onSave(text.trim())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        val keyboard = Runnable {
            if (field.isAttachedToWindow && field.hasFocus()) {
                context.getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0)
            }
        }
        val attachment = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = dialog.dismiss()
        }
        dialog.setOnDismissListener {
            field.removeCallbacks(keyboard)
            root.removeOnAttachStateChangeListener(attachment)
            if (textInputDialog === dialog) textInputDialog = null
        }
        dialog.setOnShowListener {
            layout.hint = context.getString(hint)
            layout.isErrorEnabled = true
            field.doOnTextChanged { text, _, _, _ ->
                val valid = validator(text?.toString().orEmpty())
                layout.error = if (valid) null else context.getString(error)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = valid
            }
            field.setText(value)
            field.setSelection(0, value.length)
            field.post {
                field.requestFocus()
                field.postDelayed(keyboard, 300)
            }
        }
        textInputDialog = dialog
        root.addOnAttachStateChangeListener(attachment)
        dialog.show()
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
        editText(
            title = R.string.profile_properties_auto_update,
            hint = R.string.profile_properties_update_minutes_hint,
            error = R.string.profile_properties_update_minutes_error,
            value = if (autoUpdate) (intervalMillis / ProfileUpdateInterval.MINUTE_MILLIS).toString() else "",
            validator = { ProfileUpdateInterval.parseMinutes(it) != null },
        ) {
            val interval = checkNotNull(ProfileUpdateInterval.parseMinutes(it))
            autoUpdate = interval != 0L
            if (autoUpdate) intervalMillis = interval
            updateValues()
        }
    }

    private fun submit() {
        if (submitting || !editingAllowed) return
        val form = MetaRemoteProfileForm(
            name = name.trim().ifEmpty { null },
            url = url.trim(),
            autoUpdate = autoUpdate && !snapshotMode && !localProfile,
            autoUpdateIntervalMillis = intervalMillis,
            expectedAutoUpdateIntervalMillis = originalInterval,
            userAgent = userAgent.trim().ifEmpty { null },
            proxyUrl = proxyUrl.trim().ifEmpty { null },
        )
        when {
            form.name.isNullOrBlank() -> showToast(context.getString(R.string.profile_properties_name_empty))
            !localProfile && !ProfileRemotePolicy.isValidUrl(form.url) -> showToast(context.getString(R.string.profile_url_invalid))
            !localProfile && form.proxyUrl != null && !ProfileRemotePolicy.isValidProxyUrl(form.proxyUrl) ->
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
        const val STATE_ORIGINAL_INTERVAL = "remote_profile_original_interval"
        const val STATE_INTERVAL = "remote_profile_interval"
        const val STATE_USER_AGENT = "remote_profile_user_agent"
        const val STATE_PROXY_URL = "remote_profile_proxy_url"
    }
}
