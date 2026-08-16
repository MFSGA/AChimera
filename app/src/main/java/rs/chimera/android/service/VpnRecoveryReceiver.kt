package rs.chimera.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import rs.chimera.android.util.PrivacySafeLog
import androidx.core.content.ContextCompat
import rs.chimera.android.Global

internal object VpnRecoveryBroadcastPolicy {
    fun isSupported(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
}

class VpnRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (!VpnRecoveryBroadcastPolicy.isSupported(action)) return

        val store = VpnDesiredStateStore(context)
        when (
            VpnRecoveryPolicy.decide(
                snapshot = store.snapshot(),
                profileAvailable = Global.restoreProfilePath().isNotBlank(),
                permissionGranted = VpnService.prepare(context) == null,
            )
        ) {
            VpnRecoveryDecision.RESTORE -> restore(context, action.orEmpty())
            VpnRecoveryDecision.DISCARD_STOPPED -> Unit
            VpnRecoveryDecision.BLOCK_MISSING_PROFILE ->
                persistBlockedStop(store, VpnDesiredStateReason.RECOVERY_MISSING_PROFILE, action)
            VpnRecoveryDecision.BLOCK_PERMISSION_REQUIRED ->
                persistBlockedStop(store, VpnDesiredStateReason.RECOVERY_PERMISSION_REQUIRED, action)
        }
    }

    private fun restore(context: Context, action: String) {
        VpnRuntimeRegistry.requestStart()
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TunService::class.java),
            )
        }.onSuccess {
            Log.i(TAG, "Requested VPN recovery after $action")
        }.onFailure { error ->
            VpnRuntimeRegistry.requestStop()
            PrivacySafeLog.error(TAG, "Unable to restore VPN", error, debugDetail = action)
        }
    }

    private fun persistBlockedStop(
        store: VpnDesiredStateStore,
        reason: VpnDesiredStateReason,
        action: String?,
    ) {
        runCatching { store.markStopped(reason) }
            .onFailure { error ->
                PrivacySafeLog.warning(TAG, "Unable to persist blocked VPN recovery", error, debugDetail = action)
            }
        VpnRuntimeRegistry.requestStop()
    }

    private companion object {
        const val TAG = "VpnRecoveryReceiver"
    }
}
