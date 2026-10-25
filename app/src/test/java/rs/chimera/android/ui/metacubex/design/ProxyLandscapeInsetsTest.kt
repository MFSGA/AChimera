package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyLandscapeInsetsTest {
    @Test
    fun wideLandscapeCentersTheReferenceMinimumWidth() {
        assertEquals(
            ProxyLandscapeInsets(start = 555, end = 555),
            proxyLandscapeInsets(
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
        val systemInsets = ProxyLandscapeInsets(start = 24, end = 18)

        assertEquals(
            systemInsets,
            proxyLandscapeInsets(1080, 2424, 1313, systemInsets.start, systemInsets.end),
        )
        assertEquals(
            systemInsets,
            proxyLandscapeInsets(1200, 1080, 1313, systemInsets.start, systemInsets.end),
        )
    }

    @Test
    fun landscapePaddingNeverReducesExistingSystemInsets() {
        assertEquals(
            ProxyLandscapeInsets(start = 600, end = 555),
            proxyLandscapeInsets(
                width = 2424,
                height = 1080,
                minWidth = 1313,
                start = 600,
                end = 0,
            ),
        )
    }
}
