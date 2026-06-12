package rs.chimera.android.backend.model

import android.app.Activity
import android.content.Intent

sealed interface StartVpnResult {
    data class Prepared(val intent: Intent) : StartVpnResult
    object PermissionNotRequired : StartVpnResult
    data class Error(val message: String) : StartVpnResult
}

fun StartVpnResult.isSuccess(): Boolean = this is StartVpnResult.Prepared || this is StartVpnResult.PermissionNotRequired