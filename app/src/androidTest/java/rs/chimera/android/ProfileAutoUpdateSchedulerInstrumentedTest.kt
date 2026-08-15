package rs.chimera.android

import android.app.job.JobInfo
import android.app.job.JobScheduler
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.backend.ProfileAutoUpdatePolicy
import rs.chimera.android.backend.ProfileAutoUpdateScheduler
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

@RunWith(AndroidJUnit4::class)
class ProfileAutoUpdateSchedulerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val jobScheduler = context.getSystemService(JobScheduler::class.java)

    @After
    fun tearDown() {
        jobScheduler.cancel(ProfileAutoUpdateScheduler.JOB_ID)
    }

    @Test
    @Suppress("DEPRECATION")
    fun schedulerRegistersAndCancelsPersistedNetworkJob() {
        val scheduler = ProfileAutoUpdateScheduler(context, jobScheduler)
        scheduler.refresh(listOf(remoteProfile()))

        val job = jobScheduler.allPendingJobs.firstOrNull {
            it.id == ProfileAutoUpdateScheduler.JOB_ID
        }
        assertNotNull("Automatic profile update job was not registered", job)
        checkNotNull(job)
        assertTrue(job.isPersisted)
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.networkType)
        assertEquals(ProfileAutoUpdateScheduler.INTERVAL_MILLIS, job.intervalMillis)
        assertEquals(ProfileAutoUpdatePolicy.BASE_RETRY_DELAY_MILLIS, job.initialBackoffMillis)
        assertEquals(JobInfo.BACKOFF_POLICY_EXPONENTIAL, job.backoffPolicy)
        assertEquals(
            "rs.chimera.android.backend.ProfileAutoUpdateJobService",
            job.service.className,
        )

        scheduler.refresh(emptyList())

        assertNull(
            jobScheduler.allPendingJobs.firstOrNull {
                it.id == ProfileAutoUpdateScheduler.JOB_ID
            },
        )
    }

    private fun remoteProfile() = ProfileSummary(
        id = "scheduled",
        name = "scheduled",
        filePath = "/profiles/scheduled.yaml",
        type = ProfileType.REMOTE,
        isActive = false,
        isRemote = true,
        lastUpdated = null,
        fileSize = 1,
        url = "https://example.test/profile.yaml",
        autoUpdate = true,
    )
}
