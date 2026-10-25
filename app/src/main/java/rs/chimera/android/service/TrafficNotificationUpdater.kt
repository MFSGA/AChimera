package rs.chimera.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.util.NotificationHelper

internal class TrafficNotificationUpdater(
    context: Context,
    private val backend: ChimeraBackend,
) {
    private val applicationContext = context.applicationContext

    fun start(scope: CoroutineScope): Job? {
        if (!AppPreferences.showTrafficInNotification(applicationContext)) return null

        return scope.launch {
            screenInteractiveChanges(applicationContext).collectLatest { interactive ->
                if (!interactive) return@collectLatest

                val rateTracker = TrafficNotificationRateTracker()
                while (backend.serviceState.value == ServiceState.RUNNING) {
                    try {
                        val snapshot = backend.fetchTrafficSnapshot()
                        val rates = rateTracker.record(snapshot, SystemClock.elapsedRealtime())
                        NotificationHelper.notifyRunning(applicationContext, snapshot, rates)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        // Traffic is optional notification content; keep the last valid status.
                    }
                    delay(TRAFFIC_NOTIFICATION_INTERVAL_MILLIS)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun screenInteractiveChanges(context: Context): Flow<Boolean> = callbackFlow {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> trySend(true)
                    Intent.ACTION_SCREEN_OFF -> trySend(false)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(powerManager?.isInteractive ?: true)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private companion object {
        const val TRAFFIC_NOTIFICATION_INTERVAL_MILLIS = 1_000L
    }
}
