package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileUrlFromQrTest {
    @Test
    fun prefersDecodedValueOverRawBytes() {
        val result = profileUrlFromQr("https://example.com/sub", "other".toByteArray())

        assertEquals("https://example.com/sub", result)
    }

    @Test
    fun fallsBackToRawBytes() {
        val result = profileUrlFromQr(null, "https://example.com/sub".toByteArray())

        assertEquals("https://example.com/sub", result)
    }

    @Test
    fun rejectsMissingOrBlankPayloads() {
        assertNull(profileUrlFromQr(null, null))
        assertNull(profileUrlFromQr("  ", byteArrayOf()))
    }
}
