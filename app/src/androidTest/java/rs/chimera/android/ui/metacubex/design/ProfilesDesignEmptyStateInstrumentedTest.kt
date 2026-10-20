package rs.chimera.android.ui.metacubex.design

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
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
}
