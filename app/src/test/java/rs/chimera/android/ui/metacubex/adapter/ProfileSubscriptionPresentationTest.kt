package rs.chimera.android.ui.metacubex.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ProfileSubscriptionPresentationTest {
    @Test
    fun formatsUsageExpiryAndProgressLikeReference() {
        val presentation = profileSubscriptionPresentation(
            uploadBytes = 100L * GIB,
            downloadBytes = 900L * GIB,
            totalBytes = 2L * TIB,
            expiresAtMillis = null,
            locale = Locale.US,
        )

        assertEquals("1000.00 GiB/2.00 TiB", presentation.usageText)
        assertEquals("1000.00 GiB/", presentation.usageUsedText)
        assertEquals("2.00 TiB", presentation.usageTotalText)
        assertNull(presentation.expiresText)
        assertEquals(488, presentation.progressPermille)
    }

    @Test
    fun hidesUsageWhenDownloadAndQuotaAreUnavailable() {
        val presentation = profileSubscriptionPresentation(
            uploadBytes = null,
            downloadBytes = null,
            totalBytes = null,
            expiresAtMillis = 0L,
            locale = Locale.US,
        )

        assertNull(presentation.usageText)
        assertNull(presentation.expiresText)
        assertNull(presentation.progressPermille)
    }

    @Test
    fun keepsUsageSeparatorWhenQuotaIsUnavailable() {
        val presentation = profileSubscriptionPresentation(
            uploadBytes = 3L,
            downloadBytes = 5L,
            totalBytes = null,
            expiresAtMillis = null,
            locale = Locale.US,
        )

        assertEquals("8 Bytes/", presentation.usageText)
        assertEquals("8 Bytes/", presentation.usageUsedText)
        assertNull(presentation.usageTotalText)
        assertNull(presentation.progressPermille)
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
        const val TIB = 1024L * GIB
    }
}
