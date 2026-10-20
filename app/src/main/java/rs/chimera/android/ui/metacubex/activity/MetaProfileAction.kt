package rs.chimera.android.ui.metacubex.activity

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileSummary

internal enum class MetaProfileAction(
    @param:StringRes @get:StringRes val labelRes: Int,
    @param:DrawableRes @get:DrawableRes val iconRes: Int,
) {
    Update(R.string.profile_update, R.drawable.ic_baseline_update),
    EditRemoteSettings(R.string.profile_edit_settings, R.drawable.ic_baseline_edit),
    Details(R.string.profile_details, R.drawable.ic_baseline_info),
    Verify(R.string.profile_verify, R.drawable.ic_outline_check_circle),
    Rename(R.string.profile_rename, R.drawable.ic_baseline_edit),
    Delete(R.string.profile_delete, R.drawable.ic_outline_delete),
    ;

    fun isAvailableFor(profile: ProfileSummary): Boolean = when (this) {
        Details, Rename, Delete -> true
        Verify -> profile.isActive
        EditRemoteSettings, Update -> profile.isRemote
    }
}
