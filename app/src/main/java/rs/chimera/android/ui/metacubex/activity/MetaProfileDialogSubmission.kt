package rs.chimera.android.ui.metacubex.activity

import android.app.AlertDialog
import android.view.View

internal fun AlertDialog.setProfileSubmissionLocked(
    locked: Boolean,
    vararg inputs: View,
) {
    val enabled = !locked
    inputs.forEach { it.isEnabled = enabled }
    getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = enabled
    getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = enabled
    setCanceledOnTouchOutside(enabled)
    setCancelable(enabled)
}
