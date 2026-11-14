package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TunRoutePolicyTest {
    private val publicIpv4 = listOf("1.0.0.0/8", "11.0.0.0/8")
    private val publicIpv6 = listOf("::/1", "8000::/2")

    @Test
    fun privateNetworkBypassUsesOnlyPublicRoutes() {
        assertEquals(
            publicIpv4 + "10.0.0.2/32",
            TunRoutePolicy.ipv4Routes(true, publicIpv4, "10.0.0.2"),
        )
        assertEquals(
            publicIpv6 + "fdfe:dcba:9876::2/128",
            TunRoutePolicy.ipv6Routes(true, true, publicIpv6, "fdfe:dcba:9876::2"),
        )
    }

    @Test
    fun disabledPrivateNetworkBypassUsesDefaultRoutes() {
        assertEquals(
            listOf("0.0.0.0/0"),
            TunRoutePolicy.ipv4Routes(false, publicIpv4, "10.0.0.2"),
        )
        assertEquals(
            listOf("::/0"),
            TunRoutePolicy.ipv6Routes(true, false, publicIpv6, "fdfe:dcba:9876::2"),
        )
    }

    @Test
    fun ipv6RoutesAreOmittedWhenIpv6IsDisabled() {
        assertEquals(
            emptyList<String>(),
            TunRoutePolicy.ipv6Routes(false, true, publicIpv6, "fdfe:dcba:9876::2"),
        )
        assertEquals(
            emptyList<String>(),
            TunRoutePolicy.ipv6Routes(false, false, publicIpv6, "fdfe:dcba:9876::2"),
        )
    }
}
