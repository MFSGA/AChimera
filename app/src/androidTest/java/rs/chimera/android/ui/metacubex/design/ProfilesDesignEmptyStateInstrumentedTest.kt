package rs.chimera.android.ui.metacubex.design

import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity

@RunWith(AndroidJUnit4::class)
class ProfilesDesignEmptyStateInstrumentedTest {
    @Test
    fun emptyProfileListMatchesReferenceAndKeepsToolbarImportAction() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProfilesDesign
            scenario.onActivity { activity ->
                design = ProfilesDesign(activity)
                activity.addContentView(
                    design.root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )

                design.submitList(emptyList())
            }

            scenario.onActivity {
                assertEquals(View.VISIBLE, design.root.findViewById<View>(R.id.profile_list).visibility)
                assertEquals(View.GONE, design.root.findViewById<View>(R.id.state_container).visibility)
                assertTrue(design.root.findViewById<View>(R.id.action_add).isClickable)
            }
        }
    }

    @Test
    fun longProfileNameWrapsInsteadOfBeingTruncated() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProfilesDesign
            scenario.onActivity { activity ->
                design = ProfilesDesign(activity)
                activity.addContentView(
                    design.root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                design.submitList(
                    listOf(
                        ProfileSummary(
                            id = "long-title",
                            name = "A very long imported profile name that should wrap across lines",
                            filePath = "",
                            type = ProfileType.REMOTE,
                            isActive = false,
                            isRemote = true,
                            lastUpdated = null,
                            fileSize = 0L,
                        ),
                    ),
                )
            }

            var lineCount = 0
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (lineCount < 2 && android.os.SystemClock.uptimeMillis() < deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    val list = design.root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.profile_list)
                    val item = list.findViewHolderForAdapterPosition(0)?.itemView
                    lineCount = item?.findViewById<TextView>(R.id.text_profile_name)?.lineCount ?: 0
                }
                if (lineCount < 2) android.os.SystemClock.sleep(50)
            }

            assertTrue("Long profile names should wrap like the reference card", lineCount >= 2)
        }
    }

    @Test
    fun profileAccentUsesCmfaColorInsteadOfThemePrimary() {
        val previousNightMode = AppCompatDelegate.getDefaultNightMode()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        try {
            ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
                lateinit var design: ProfilesDesign
                var expectedColor = 0
                scenario.onActivity { activity ->
                    design = ProfilesDesign(activity)
                    activity.addContentView(
                        design.root,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    expectedColor = ContextCompat.getColor(activity, R.color.metacubex_primary_dark)
                    val primaryValue = TypedValue()
                    activity.theme.resolveAttribute(
                        com.google.android.material.R.attr.colorPrimary,
                        primaryValue,
                        true,
                    )
                    val themePrimary = if (primaryValue.resourceId != 0) {
                        ContextCompat.getColor(activity, primaryValue.resourceId)
                    } else {
                        primaryValue.data
                    }
                    assertNotEquals("The light theme must distinguish the fixed CMFA stripe color", expectedColor, themePrimary)
                    design.submitList(
                        listOf(
                            ProfileSummary(
                                id = "profile",
                                name = "新配置",
                                filePath = "",
                                type = ProfileType.REMOTE,
                                isActive = true,
                                isRemote = true,
                                lastUpdated = System.currentTimeMillis() - 10 * 60 * 60 * 1000L,
                                fileSize = 0L,
                                subscriptionUploadBytes = (1L shl 40) * 104 / 100,
                                subscriptionDownloadBytes = 0L,
                                subscriptionTotalBytes = (1L shl 40) * 228 / 100,
                            ),
                        ),
                    )
                }

                var actualColor: Int? = null
                val deadline = android.os.SystemClock.uptimeMillis() + 5_000
                while (actualColor == null && android.os.SystemClock.uptimeMillis() < deadline) {
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    scenario.onActivity {
                        val list = design.root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.profile_list)
                        val item = list.findViewHolderForAdapterPosition(0)?.itemView
                        val indicator = item?.findViewById<View>(R.id.profile_active_indicator)
                        actualColor = (indicator?.background as? ColorDrawable)?.color
                    }
                    if (actualColor == null) android.os.SystemClock.sleep(50)
                }

                assertEquals(expectedColor, actualColor)
            }
        } finally {
            AppCompatDelegate.setDefaultNightMode(previousNightMode)
        }
    }
}
