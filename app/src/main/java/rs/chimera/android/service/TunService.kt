package rs.chimera.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import rs.chimera.android.Global
import rs.chimera.android.MainActivity
import rs.chimera.android.R
import rs.chimera.android.ffi.ChimeraFfi

var tunService: TunService? = null

class TunService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tunService = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForegroundService()
        val startResult = ChimeraFfi.startCore(
            profilePath = Global.profilePath,
            cacheDir = cacheDir.absolutePath,
        )
        if (startResult.isFailure) {
            Log.e(TAG, "Failed to initialize Rust core", startResult.exceptionOrNull())
            Global.isServiceRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }
        Global.isServiceRunning.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        ChimeraFfi.stopCore().exceptionOrNull()?.let { error ->
            Log.w(TAG, "Failed to stop Rust core cleanly", error)
        }
        tunService = null
        Global.isServiceRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun stopVpn() {
        stopSelf()
    }

    private fun startAsForegroundService() {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.service_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "chimera_service"
        const val NOTIFICATION_ID = 1001
        const val TAG = "ChimeraTunService"
    }
}
