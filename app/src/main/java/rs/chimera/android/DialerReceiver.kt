package rs.chimera.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class DialerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldLaunch()) return

        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun shouldLaunch(): Boolean = synchronized(DialerReceiver::class.java) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastLaunchAtMillis
        if (previous != null && now - previous < DUPLICATE_BROADCAST_WINDOW_MILLIS) {
            false
        } else {
            lastLaunchAtMillis = now
            true
        }
    }

    private companion object {
        const val DUPLICATE_BROADCAST_WINDOW_MILLIS = 1_000L
        var lastLaunchAtMillis: Long? = null
    }
}
