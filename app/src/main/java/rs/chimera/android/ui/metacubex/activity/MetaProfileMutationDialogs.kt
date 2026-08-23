package rs.chimera.android.ui.metacubex.activity

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import rs.chimera.android.R

internal fun showMetaRenameProfileDialog(
    context: Context,
    initialName: String,
    onSubmit: (
        newName: String,
        dismiss: () -> Unit,
        setSubmitting: (Boolean) -> Unit,
    ) -> Unit,
) {
    val input = EditText(context).apply {
        setText(initialName)
        setSelectAllOnFocus(true)
    }
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.profile_rename_title)
        .setView(input)
        .setPositiveButton(android.R.string.ok, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = input.text.toString().trim()
            if (newName.isEmpty()) return@setOnClickListener
            dialog.setProfileSubmissionLocked(true, input)
            onSubmit(
                newName,
                dialog::dismiss,
                { submitting -> dialog.setProfileSubmissionLocked(submitting, input) },
            )
        }
    }
    dialog.show()
}

internal fun showMetaDeleteProfileDialog(
    context: Context,
    profileName: String,
    onSubmit: (
        dismiss: () -> Unit,
        setSubmitting: (Boolean) -> Unit,
    ) -> Unit,
) {
    val dialog = AlertDialog.Builder(context)
        .setTitle(profileName)
        .setMessage(context.getString(R.string.profile_delete_confirm))
        .setPositiveButton(R.string.profile_delete, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.setProfileSubmissionLocked(true)
            onSubmit(
                dialog::dismiss,
                { submitting -> dialog.setProfileSubmissionLocked(submitting) },
            )
        }
    }
    dialog.show()
}
