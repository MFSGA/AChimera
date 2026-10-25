package rs.chimera.android

import android.app.job.JobScheduler
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.backend.ProfileAutoUpdateScheduler
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ProfileUpdateInterval

@RunWith(AndroidJUnit4::class)
class ProfileUpdateIntervalSchedulerInstrumentedTest {
    @Test
    fun sharedJobChangesPeriodAndCancelsWithoutTouchingTheRealJobNamespace() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        if (Build.VERSION.SDK_INT < 34) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val jobs = context.getSystemService(JobScheduler::class.java).forNamespace("cmfa-interval-fixture")
        val scheduler = ProfileAutoUpdateScheduler(context, jobs)
        val profile = ProfileSummary(
            "interval", "interval", "/fixture.yaml", type = ProfileType.REMOTE,
            isActive = false, isRemote = true, lastUpdated = null, fileSize = 1,
            url = "https://example.com/profile.yaml", autoUpdate = true,
        )
        fun period() = jobs.allPendingJobs.singleOrNull { it.id == ProfileAutoUpdateScheduler.JOB_ID }?.intervalMillis
        try {
            scheduler.refresh(listOf(profile))
            assertEquals(ProfileUpdateInterval.DEFAULT_MILLIS, period())
            scheduler.refresh(listOf(profile, profile.copy(autoUpdateIntervalMillis = 30L * 60_000)))
            assertEquals(30L * 60_000, period())
            scheduler.refresh(listOf(profile.copy(autoUpdateIntervalMillis = ProfileUpdateInterval.MIN_MILLIS)))
            assertEquals(ProfileUpdateInterval.MIN_MILLIS, period())
            scheduler.refresh(listOf(profile))
            assertEquals(ProfileUpdateInterval.DEFAULT_MILLIS, period())
            scheduler.refresh(emptyList())
            assertNull(period())
        } finally {
            jobs.cancelAll()
        }
    }
}
