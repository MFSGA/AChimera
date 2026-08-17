package rs.chimera.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionEndpointPresentationTest {
    @Test
    fun `formats ipv4 and host endpoints`() {
        assertEquals("192.0.2.1:443", formatConnectionEndpoint("192.0.2.1", "443"))
        assertEquals("example.com:80", formatConnectionEndpoint("example.com", "80"))
    }

    @Test
    fun `brackets ipv6 endpoints`() {
        assertEquals("[2001:db8::1]:443", formatConnectionEndpoint("2001:db8::1", "443"))
        assertEquals("[2001:db8::1]:443", formatConnectionEndpoint("[2001:db8::1]", "443"))
    }

    @Test
    fun `uses placeholder for missing endpoint parts`() {
        assertEquals("?:?", formatConnectionEndpoint("", ""))
    }
}
