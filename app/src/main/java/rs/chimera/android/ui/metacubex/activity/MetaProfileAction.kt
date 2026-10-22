package rs.chimera.android.ui.metacubex.activity

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileSummary

internal enum class MetaProfileAction(
    @param:StringRes @get:StringRes val labelRes: Int,
    @param:DrawableRes @get:DrawableRes val iconRes: Int,
) {
    Update(R.string.profile_menu_update, R.drawable.ic_baseline_update),
    Edit(R.string.profile_menu_edit, R.drawable.ic_baseline_edit),
    Duplicate(R.string.profile_menu_duplicate, R.drawable.ic_baseline_content_copy),
    ShareQrCode(R.string.profile_menu_share_qr_code, R.drawable.baseline_qr_code_scanner),
    Delete(R.string.profile_menu_delete, R.drawable.ic_outline_delete),
    Details(R.string.profile_details, R.drawable.ic_baseline_info),
    Verify(R.string.profile_verify, R.drawable.ic_outline_check_circle),
    ;

    val isCmfaMenuAction: Boolean
        get() = when (this) {
            Update, Edit, Duplicate, ShareQrCode, Delete -> true
            Details, Verify -> false
        }

    fun isAvailableFor(profile: ProfileSummary): Boolean = when (this) {
        Edit, Details, Duplicate, Delete -> true
        Verify -> profile.isActive
        Update -> profile.isRemote
        ShareQrCode -> profile.isRemote && !profile.url.isNullOrBlank()
    }
}

internal fun profileActionMenuViewportHeight(
    actions: List<MetaProfileAction>,
    measuredRowHeights: Map<MetaProfileAction, Int>,
    maxAvailableHeight: Int,
): Int = minOf(
    actions
        .takeWhile(MetaProfileAction::isCmfaMenuAction)
        .sumOf { measuredRowHeights[it] ?: 0 },
    maxAvailableHeight.coerceAtLeast(0),
)
