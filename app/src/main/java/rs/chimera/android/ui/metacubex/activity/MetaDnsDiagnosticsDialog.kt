package rs.chimera.android.ui.metacubex.activity

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.util.runCatchingPreservingCancellation
import rs.chimera.android.util.toUserVisibleMessage

internal fun AppCompatActivity.showMetaDnsDiagnosticsDialog(
    queryDns: suspend (name: String, recordType: String) -> String,
) {
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, 0)
    }
    val nameInput = EditText(this).apply {
        hint = getString(R.string.dns_query_name)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setText(DEFAULT_DNS_QUERY_NAME)
        setSelectAllOnFocus(true)
    }
    val typeInput = EditText(this).apply {
        hint = getString(R.string.dns_record_type)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        setText(DEFAULT_DNS_RECORD_TYPE)
        setSelectAllOnFocus(true)
    }
    container.addView(nameInput)
    container.addView(typeInput)

    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.dns_diagnostics_title)
        .setMessage(R.string.dns_diagnostics_summary)
        .setView(container)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.dns_query_action, null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val recordType = typeInput.text.toString().trim().uppercase()
            if (name.isEmpty()) {
                nameInput.error = getString(R.string.dns_query_name_required)
                return@setOnClickListener
            }
            if (recordType.isEmpty()) {
                typeInput.error = getString(R.string.dns_record_type_required)
                return@setOnClickListener
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            lifecycleScope.launch {
                runCatchingPreservingCancellation { queryDns(name, recordType) }
                    .onSuccess { result ->
                        dialog.dismiss()
                        AlertDialog.Builder(this@showMetaDnsDiagnosticsDialog)
                            .setTitle(R.string.dns_diagnostics_result)
                            .setMessage(result)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }.onFailure { error ->
                        typeInput.error = error.toUserVisibleMessage(
                            this@showMetaDnsDiagnosticsDialog,
                            R.string.profile_unknown_error,
                        )
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
            }
        }
    }
    dialog.show()
}

private const val DEFAULT_DNS_QUERY_NAME = "example.com"
private const val DEFAULT_DNS_RECORD_TYPE = "A"
