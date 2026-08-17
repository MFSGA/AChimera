package rs.chimera.android.ui.metacubex.activity

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.StringRes
import rs.chimera.android.R
import rs.chimera.android.backend.ProfileRemotePolicy

internal data class MetaRemoteProfileForm(
    val name: String?,
    val url: String,
    val autoUpdate: Boolean,
    val userAgent: String?,
    val proxyUrl: String?,
)

internal fun showMetaRemoteProfileDialog(
    context: Context,
    @StringRes titleRes: Int,
    @StringRes positiveButtonRes: Int = android.R.string.ok,
    initialName: String = "",
    initialUrl: String = "",
    initialAutoUpdate: Boolean = false,
    initialUserAgent: String = "",
    initialProxyUrl: String = "",
    requireName: Boolean,
    onSubmit: (MetaRemoteProfileForm) -> Unit,
) {
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, 0)
    }
    val nameInput = EditText(context).apply {
        hint = context.getString(R.string.profile_name_label)
        setText(initialName)
    }
    val urlInput = EditText(context).apply {
        hint = context.getString(R.string.profile_url_hint)
        setText(initialUrl)
        configureSensitiveRemoteInput()
    }
    val userAgentInput = EditText(context).apply {
        hint = context.getString(R.string.profile_user_agent_hint)
        setText(initialUserAgent)
        configureSensitiveRemoteInput()
    }
    val proxyInput = EditText(context).apply {
        hint = context.getString(R.string.profile_proxy_hint)
        setText(initialProxyUrl)
        configureSensitiveRemoteInput()
    }
    val autoUpdateInput = CheckBox(context).apply {
        text = context.getString(R.string.profile_auto_update)
        isChecked = initialAutoUpdate
    }
    container.addView(nameInput)
    container.addView(urlInput)
    container.addView(userAgentInput)
    container.addView(proxyInput)
    container.addView(autoUpdateInput)

    val dialog = AlertDialog.Builder(context)
        .setTitle(titleRes)
        .setView(container)
        .setPositiveButton(positiveButtonRes, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val url = urlInput.text.toString().trim()
            if (requireName && name.isEmpty()) {
                nameInput.error = context.getString(R.string.profile_name_label)
                return@setOnClickListener
            }
            if (!ProfileRemotePolicy.isValidUrl(url)) {
                urlInput.error = context.getString(R.string.profile_url_invalid)
                return@setOnClickListener
            }
            val proxyUrl = proxyInput.text.toString().trim()
            if (proxyUrl.isNotEmpty() && !ProfileRemotePolicy.isValidProxyUrl(proxyUrl)) {
                proxyInput.error = context.getString(R.string.profile_proxy_invalid)
                return@setOnClickListener
            }
            dialog.dismiss()
            onSubmit(
                MetaRemoteProfileForm(
                    name = name.ifEmpty { null },
                    url = url,
                    autoUpdate = autoUpdateInput.isChecked,
                    userAgent = userAgentInput.text.toString().trim().ifEmpty { null },
                    proxyUrl = proxyUrl.ifEmpty { null },
                ),
            )
        }
    }
    dialog.show()
}

private fun EditText.configureSensitiveRemoteInput() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }
    inputType = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_VARIATION_URI or
        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
}
