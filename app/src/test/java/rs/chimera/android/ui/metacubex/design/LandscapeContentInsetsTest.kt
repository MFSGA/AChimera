package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Test

class LandscapeContentInsetsTest {
    @Test
    fun wideLandscapeCentersTheReferenceMinimumWidth() {
        assertEquals(
            LandscapeContentInsets(start = 555, end = 555),
            landscapeContentInsets(
                width = 2424,
                height = 1080,
                minWidth = 1313,
                start = 0,
                end = 0,
            ),
        )
    }

    @Test
    fun portraitAndNarrowLandscapeKeepSystemInsets() {
        val systemInsets = LandscapeContentInsets(start = 24, end = 18)

        assertEquals(
            systemInsets,
            landscapeContentInsets(1080, 2424, 1313, systemInsets.start, systemInsets.end),
        )
        assertEquals(
            systemInsets,
            landscapeContentInsets(1200, 1080, 1313, systemInsets.start, systemInsets.end),
        )
    }

    @Test
    fun landscapePaddingNeverReducesExistingSystemInsets() {
        assertEquals(
            LandscapeContentInsets(start = 600, end = 555),
            landscapeContentInsets(
                width = 2424,
                height = 1080,
                minWidth = 1313,
                start = 600,
                end = 0,
            ),
        )
    }
}
