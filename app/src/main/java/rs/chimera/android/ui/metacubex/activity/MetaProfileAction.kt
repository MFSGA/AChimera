package rs.chimera.android.ui.metacubex.activity

import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileSummary

internal enum class MetaProfileAction(val labelRes: Int) {
    Details(R.string.profile_details),
    Verify(R.string.profile_verify),
    EditRemoteSettings(R.string.profile_edit_settings),
    Update(R.string.profile_update),
    Rename(R.string.profile_rename),
    Delete(R.string.profile_delete),
    ;

    fun isAvailableFor(profile: ProfileSummary): Boolean = when (this) {
        Details, Rename, Delete -> true
        Verify -> profile.isActive
        EditRemoteSettings, Update -> profile.isRemote
    }
}
