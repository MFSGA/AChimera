package rs.chimera.android.backend

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.util.PrivacySafeLog

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
        val job = JobInfo.Builder(JOB_ID, componentName)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(ProfileAutoUpdatePolicy.UPDATE_INTERVAL_MILLIS)
            .setBackoffCriteria(
                ProfileAutoUpdatePolicy.BASE_RETRY_DELAY_MILLIS,
                JobInfo.BACKOFF_POLICY_EXPONENTIAL,
            )
            .build()
        val scheduled = ProfileAutoUpdateScheduleRetry.run {
            scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
        }
        check(scheduled) { "Unable to schedule automatic profile updates" }
    }

    internal companion object {
        const val JOB_ID = 0x4348_5052
        const val INTERVAL_MILLIS = ProfileAutoUpdatePolicy.UPDATE_INTERVAL_MILLIS
    }
}

/** Runs persisted remote profile updates without requiring an Activity. */
class ProfileAutoUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val backend = BackendProvider.provide()
            val stateStore = ProfileAutoUpdateStateStore(applicationContext)
            val result = runProfileAutoUpdateJob {
                ProfileAutoUpdateRunner(
                    object : ProfileAutoUpdateOperations {
                        override val serviceState: StateFlow<ServiceState> = backend.serviceState

                        override suspend fun listProfiles(): List<ProfileSummary> =
                            backend.listProfiles()

                        override suspend fun updateRemoteProfile(id: String) {
                            backend.updateRemoteProfile(id)
                        }

                        override suspend fun recordAutoUpdateState(
                            id: String,
                            state: ProfileAutoUpdateState,
                        ) {
                            stateStore.write(id, state)
                        }

                        override suspend fun restartVpn() {
                            backend.restartVpn()
                        }
                    },
                ).run()
            }
            result.onSuccess { summary ->
                Log.i(
                    TAG,
                    "Automatic profile update attempted=${summary.attempted} " +
                        "updated=${summary.updated} deferred=${summary.deferred} " +
                        "failures=${summary.failures.size} restartedVpn=${summary.restartedVpn}",
                )
            }.onFailure { error ->
                PrivacySafeLog.error(TAG, "Automatic profile update job failed", error)
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

internal suspend fun runProfileAutoUpdateJob(
    block: suspend () -> ProfileAutoUpdateResult,
): Result<ProfileAutoUpdateResult> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
