package rs.chimera.android.backend

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ServiceState

/** Keeps one persisted daily job in sync with the profile catalog. */
internal class ProfileAutoUpdateScheduler(
    context: Context,
    private val scheduler: JobScheduler = context.getSystemService(JobScheduler::class.java),
) {
    private val componentName = ComponentName(context, ProfileAutoUpdateJobService::class.java)

    fun refresh(profiles: List<ProfileSummary>) {
        val scheduled = scheduler.allPendingJobs.any { job -> job.id == JOB_ID }
        if (ProfileAutoUpdatePolicy.shouldSchedule(profiles)) {
            if (!scheduled) schedule()
        } else if (scheduled) {
            scheduler.cancel(JOB_ID)
        }
    }

    private fun schedule() {
        val result = scheduler.schedule(
            JobInfo.Builder(JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(INTERVAL_MILLIS)
                .build(),
        )
        check(result == JobScheduler.RESULT_SUCCESS) {
            "Unable to schedule automatic profile updates"
        }
    }

    internal companion object {
        const val JOB_ID = 0x4348_5052
        const val INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

/** Runs the backend update flow outside of an activity or foreground service. */
class ProfileAutoUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val backend = BackendProvider.provide()
            val result = runCatching {
                ProfileAutoUpdateRunner(
                    object : ProfileAutoUpdateOperations {
                        override val serviceState: StateFlow<ServiceState> = backend.serviceState

                        override suspend fun listProfiles(): List<ProfileSummary> =
                            backend.listProfiles()

                        override suspend fun updateRemoteProfile(id: String) {
                            backend.updateRemoteProfile(id)
                        }

                        override suspend fun restartVpn() {
                            backend.stopVpn()
                            backend.startVpnAfterPermission()
                        }
                    },
                ).run()
            }
            result.onSuccess { summary ->
                Log.i(
                    TAG,
                    "Automatic profile update attempted=${summary.attempted} " +
                        "updated=${summary.updated} failures=${summary.failures.size} " +
                        "restartedVpn=${summary.restartedVpn}",
                )
            }.onFailure { error ->
                Log.e(TAG, "Automatic profile update job failed", error)
            }
            jobFinished(
                params,
                result.fold(
                    onSuccess = ProfileAutoUpdateResult::shouldRetry,
                    onFailure = { true },
                ),
            )
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ProfileAutoUpdate"
    }
}
