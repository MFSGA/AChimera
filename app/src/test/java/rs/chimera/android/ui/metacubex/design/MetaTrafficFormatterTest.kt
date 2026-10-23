package rs.chimera.android.ui.metacubex.design

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MetaTrafficFormatterTest {
    @Test
    fun usesCmfaUnitsAtTheSameByteThresholds() {
        withLocale(Locale.US) {
            assertEquals("228 Bytes", formatMetaTrafficTotal(228L))
            assertEquals("102400 Bytes", formatMetaTrafficTotal(100L * KIB))
            assertEquals("100.00 KiB", formatMetaTrafficTotal(100L * KIB + 1L))
            assertEquals("102400.00 KiB", formatMetaTrafficTotal(100L * MIB))
            assertEquals("100.00 MiB", formatMetaTrafficTotal(100L * MIB + 1L))
            assertEquals("102400.00 MiB", formatMetaTrafficTotal(100L * GIB))
            assertEquals("100.00 GiB", formatMetaTrafficTotal(100L * GIB + 1L))
        }
    }

    @Test
    fun negativeTotalsRenderAsZeroLikeAnEmptyRuntime() {
        assertEquals("0 Bytes", formatMetaTrafficTotal(-1L))
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    private companion object {
        const val KIB = 1024L
        const val MIB = KIB * 1024L
        const val GIB = MIB * 1024L
    }
}
