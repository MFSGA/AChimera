package rs.chimera.android.service

import kotlinx.coroutines.Job

internal class VpnLifecycleGate {
    data class StopRequest(
        val accepted: Boolean,
        val startupJob: Job?,
    )

    private var startRequested = false
    private var stopRequested = false
    private var cleanupStarted = false
    private var startupJob: Job? = null

    @Synchronized
    fun requestStart(): Boolean {
        if (startRequested || stopRequested || cleanupStarted) return false
        startRequested = true
        return true
    }

    @Synchronized
    fun registerStartup(job: Job): Boolean {
        if (stopRequested || cleanupStarted) {
            job.cancel()
            return false
        }
        startupJob = job
        return true
    }

    @Synchronized
    fun clearStartup(job: Job) {
        if (startupJob === job) {
            startupJob = null
        }
    }

    @Synchronized
    fun requestStop(): StopRequest {
        if (stopRequested || cleanupStarted) {
            return StopRequest(accepted = false, startupJob = null)
        }
        stopRequested = true
        val job = startupJob
        job?.cancel()
        return StopRequest(accepted = true, startupJob = job)
    }

    @Synchronized
    fun cancelStartup(): Job? {
        stopRequested = true
        return startupJob?.also { it.cancel() }
    }

    @Synchronized
    fun beginCleanup(): Boolean {
        if (cleanupStarted) return false
        cleanupStarted = true
        return true
    }

    @Synchronized
    fun canRunRuntime(): Boolean =
        startRequested && !stopRequested && !cleanupStarted

    @Synchronized
    fun canHandleUnexpectedCoreStop(): Boolean =
        canRunRuntime()
}
