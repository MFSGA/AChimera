package rs.chimera.android.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import rs.chimera.android.MainActivity
import rs.chimera.android.R

object NotificationHelper {
    const val CHANNEL_ID = "chimera_service"
    const val NOTIFICATION_ID = 1001

    /**
     * Ensure the notification channel exists (AChimera style with string resources).
     */
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

    /**
     * Create notification channel with hardcoded defaults (upstream style).
     * Falls back to this if string resources are unavailable.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Chimera Service",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Chimera VPN service notification"
                    setShowBadge(false)
                }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // --- Simple notification builders (upstream style) ---

    /**
     * Create a simple ongoing notification (upstream style).
     */
    fun createNotification(context: Context): Notification {
        ensureChannel(context)

        val intent = MainActivity.intent(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setContentTitle("Chimera")
            .setContentText("VPN service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // --- State-specific notification builders (AChimera style) ---

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

    // --- Notification posting helpers (AChimera style) ---

    fun notifyRunning(context: Context) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, buildRunningNotification(context))
        }.getOrElse { error ->
            if (error !is SecurityException) throw error
        }
    }

    fun notifyFailed(context: Context, message: String?) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, buildFailedNotification(context, message))
        }.getOrElse { error ->
            if (error !is SecurityException) throw error
        }
    }

    // --- Internal helpers ---

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        ensureChannel(context)

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
