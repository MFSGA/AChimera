package rs.chimera.android.util

import android.content.Context
import rs.chimera.android.R
import rs.chimera.android.backend.SettingsApplyException

internal fun Throwable.toSettingsUpdateMessage(context: Context): String {
    val error = if (this is SettingsApplyException) cause ?: this else this
    val detail = error.toUserVisibleMessage(context, R.string.profile_unknown_error)
    return if (this is SettingsApplyException) {
        context.getString(R.string.settings_saved_apply_failed, detail)
    } else {
        context.getString(R.string.cmfa_settings_save_failed, detail)
    }
}
