package rs.chimera.android.backend

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AppForegroundTracker(
    private val activityCounter: ForegroundActivityCounter = ForegroundActivityCounter(),
) : Application.ActivityLifecycleCallbacks {
    val isForeground: StateFlow<Boolean> = activityCounter.isForeground

    override fun onActivityStarted(activity: Activity) {
        activityCounter.onActivityStarted()
    }

    override fun onActivityStopped(activity: Activity) {
        activityCounter.onActivityStopped()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}

internal class ForegroundActivityCounter {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private var startedActivityCount = 0

    fun onActivityStarted() {
        startedActivityCount += 1
        _isForeground.value = true
    }

    fun onActivityStopped() {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        _isForeground.value = startedActivityCount > 0
    }
}

internal object AppForegroundState {
    private val tracker = AppForegroundTracker()

    val isForeground: StateFlow<Boolean> = tracker.isForeground

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(tracker)
    }
}

internal fun shouldPollRuntimeTelemetry(
    serviceState: rs.chimera.android.backend.model.ServiceState,
    appForeground: Boolean,
): Boolean = serviceState == rs.chimera.android.backend.model.ServiceState.RUNNING && appForeground
