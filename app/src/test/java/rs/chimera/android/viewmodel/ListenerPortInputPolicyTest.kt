package rs.chimera.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerPortInputPolicyTest {
    @Test
    fun requiredPortAcceptsValidRangeOnly() {
        assertEquals(1u.toUShort(), ListenerPortInputPolicy.parseRequired("1"))
        assertEquals(65535u.toUShort(), ListenerPortInputPolicy.parseRequired("65535"))
        assertNull(ListenerPortInputPolicy.parseRequired("0"))
        assertNull(ListenerPortInputPolicy.parseRequired("65536"))
        assertNull(ListenerPortInputPolicy.parseRequired("invalid"))
    }

    @Test
    fun optionalPortAllowsBlankValue() {
        assertNull(ListenerPortInputPolicy.parseOptional("  "))
        assertTrue(ListenerPortInputPolicy.isOptionalValid(""))
        assertTrue(ListenerPortInputPolicy.isOptionalValid("8080"))
        assertFalse(ListenerPortInputPolicy.isOptionalValid("70000"))
    }
}
