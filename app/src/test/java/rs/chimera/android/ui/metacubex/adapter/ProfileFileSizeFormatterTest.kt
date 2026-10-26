package rs.chimera.android.ui.metacubex.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFileSizeFormatterTest {
    @Test
    fun formatsBytesWithCmfaBinaryUnitsAndPrecision() {
        assertEquals("1024 Bytes", formatProfileFileSize(1024L, java.util.Locale.US))
        assertEquals("1.00 KiB", formatProfileFileSize(1025L, java.util.Locale.US))
        assertEquals("1.00 MiB", formatProfileFileSize(1024L * 1024L + 1L, java.util.Locale.US))
    }
}
