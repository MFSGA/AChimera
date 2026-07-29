package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortPreferenceTest {
    @Test
    fun parsesSupportedPreferenceTypes() {
        assertEquals(7890u.toUShort(), PortPreference.parse(7890))
        assertEquals(7891u.toUShort(), PortPreference.parse(7891L))
        assertEquals(7892u.toUShort(), PortPreference.parse(" 7892 "))
    }

    @Test
    fun acceptsPortRangeBoundaries() {
        assertEquals(1u.toUShort(), PortPreference.parse(1))
        assertEquals(65_535u.toUShort(), PortPreference.parse(65_535L))
    }

    @Test
    fun rejectsZeroNegativeAndOutOfRangeValues() {
        assertNull(PortPreference.parse(0))
        assertNull(PortPreference.parse(-1L))
        assertNull(PortPreference.parse(65_536))
    }

    @Test
    fun rejectsLongValuesWithoutIntegerOverflow() {
        assertNull(PortPreference.parse(4_294_967_297L))
        assertNull(PortPreference.parse(Long.MAX_VALUE))
    }

    @Test
    fun rejectsUnsupportedAndMalformedValues() {
        assertNull(PortPreference.parse(null))
        assertNull(PortPreference.parse(true))
        assertNull(PortPreference.parse(7890.0f))
        assertNull(PortPreference.parse("not-a-port"))
    }
}
