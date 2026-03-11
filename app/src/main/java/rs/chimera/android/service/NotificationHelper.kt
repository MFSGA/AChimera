package rs.chimera.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import rs.chimera.android.MainActivity
import rs.chimera.android.R

object NotificationHelper {
    const val CHANNEL_ID = "chimera_service"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.service_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.service_notification_text_running)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildStartingNotification(context: Context): Notification =
        baseBuilder(context)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(context.getString(R.string.service_notification_text_starting))
            .build()

    fun buildRunningNotification(context: Context): Notification =
        baseBuilder(context)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(context.getString(R.string.service_notification_text_running))
            .build()

    fun buildFailedNotification(context: Context, message: String?): Notification =
        baseBuilder(context)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentTitle(context.getString(R.string.service_notification_title_failed))
            .setContentText(message ?: context.getString(R.string.service_notification_text_failed))
            .build()

    fun notifyRunning(context: Context) {
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID, buildRunningNotification(context))
    }

    fun notifyFailed(context: Context, message: String?) {
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID, buildFailedNotification(context, message))
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        val intent = PendingIntent.getActivity(
            context,
            0,
            MainActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(intent)
    }
}
