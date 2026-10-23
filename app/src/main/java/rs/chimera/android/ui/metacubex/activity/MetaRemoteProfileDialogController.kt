package rs.chimera.android.ui.metacubex.activity

import android.app.AlertDialog

internal class MetaRemoteProfileDialogController(
    private val dialog: AlertDialog,
    private val updateEditingEnabled: (Boolean) -> Unit,
) {
    fun setEditingEnabled(enabled: Boolean) = updateEditingEnabled(enabled)

    fun setOnDismissListener(listener: () -> Unit) {
        dialog.setOnDismissListener { listener() }
    }
}
