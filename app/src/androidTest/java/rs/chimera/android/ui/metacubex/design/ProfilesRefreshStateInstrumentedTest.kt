package rs.chimera.android.ui.metacubex.design

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity

@RunWith(AndroidJUnit4::class)
class ProfilesRefreshStateInstrumentedTest {
    @Test
    fun batchRefreshRotatesToolbarActionWithoutHidingProfiles() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProfilesDesign
            scenario.onActivity { activity ->
                design = ProfilesDesign(activity)
                activity.setContentView(design.root)
                design.submitList(listOf(remoteProfile()))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val profileList = design.root.findViewById<View>(R.id.profile_list)
                val refreshAction = design.root.findViewById<View>(R.id.action_refresh)
                assertEquals(View.VISIBLE, profileList.visibility)
                assertEquals(View.VISIBLE, refreshAction.visibility)

                design.setRefreshInProgress(true)

                assertEquals(View.VISIBLE, profileList.visibility)
                assertNotNull(refreshAction.animation)

                design.setRefreshInProgress(false)

                assertEquals(View.VISIBLE, profileList.visibility)
                assertNull(refreshAction.animation)
            }
        }
    }

    private fun remoteProfile() = ProfileSummary(
        id = "remote-profile",
        name = "CMFA",
        filePath = "",
        type = ProfileType.REMOTE,
        isActive = true,
        isRemote = true,
        lastUpdated = null,
        fileSize = 0L,
    )
}
