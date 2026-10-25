package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.regex.Pattern
import uniffi.chimera_ffi.Connection
import uniffi.chimera_ffi.ConnectionsResponse
import uniffi.chimera_ffi.DelayHistory
import uniffi.chimera_ffi.Metadata
import uniffi.chimera_ffi.Mode
import uniffi.chimera_ffi.Proxy

class ControllerSnapshotMappingTest {
    @Test
    fun proxyMappingPreservesSelectionHistoryAndSharedDetails() {
        val proxies = listOf(
            Proxy(
                name = "AUTO",
                proxyType = "Selector",
                all = listOf("A", "B"),
                now = "A",
                history = listOf(DelayHistory(time = "123", delay = 45)),
            ),
            Proxy(
                name = "A",
                proxyType = "VLESS",
                all = emptyList(),
                now = null,
                history = listOf(DelayHistory(time = "not-a-number", delay = 67)),
            ),
        )

        val mapped = proxies.toProxyGroupSnapshots(Mode.GLOBAL)

        assertEquals(listOf("A", "AUTO"), mapped.map { it.name })
        val auto = mapped.first { it.name == "AUTO" }
        assertEquals(listOf("A", "B"), auto.proxies)
        assertEquals("A", auto.selected)
        assertEquals(rs.chimera.android.backend.model.ProxyMode.GLOBAL, auto.mode)
        assertEquals("VLESS", auto.proxyDetails.getValue("A").type)
        assertEquals(45, auto.proxyDetails.getValue("AUTO").history.single().delay)
        assertEquals(123L, auto.proxyDetails.getValue("AUTO").history.single().time)
        assertEquals(0L, auto.proxyDetails.getValue("A").history.single().time)
        assertEquals(mapped[0].proxyDetails, mapped[1].proxyDetails)
        assertEquals(true, auto.proxyDetails.getValue("A").alive)
    }

    @Test
    fun proxyMappingPreservesFailedHealthcheckWithoutDiscardingHistory() {
        val mapped = listOf(
            Proxy("selector", "Selector", listOf("node-a"), "node-a", emptyList()),
            Proxy(
                name = "node-a",
                proxyType = "Vless",
                all = emptyList(),
                now = null,
                history = listOf(DelayHistory("123", 42)),
                alive = false,
            ),
        ).toProxyGroupSnapshots(Mode.RULE).first { it.name == "selector" }

        assertEquals("node-a", mapped.selected)
        assertEquals(false, mapped.proxyDetails.getValue("node-a").alive)
        assertEquals(42, mapped.proxyDetails.getValue("node-a").history.single().delay)
    }

    @Test
    fun directModeUsesSyntheticDirectGroup() {
        val mapped = listOf(
            Proxy(
                name = "GLOBAL",
                proxyType = "Selector",
                all = listOf("A"),
                now = "A",
                history = emptyList(),
            ),
        ).toProxyGroupSnapshots(Mode.DIRECT)

        assertEquals(listOf("DIRECT"), mapped.map { it.name })
        assertEquals(emptyList<String>(), mapped.single().proxies)
        assertNull(mapped.single().selected)
        assertEquals("Direct", mapped.single().proxyDetails.getValue("DIRECT").type)
    }

    @Test
    fun globalAndRuleModesFollowGlobalMemberOrderWithoutDuplicates() {
        val proxies = listOf(
            Proxy(
                name = "GLOBAL",
                proxyType = "Selector",
                all = listOf("B", "A", "B"),
                now = "B",
                history = emptyList(),
            ),
            Proxy("Z", "VLESS", emptyList(), null, emptyList()),
            Proxy("A", "VLESS", emptyList(), null, emptyList()),
            Proxy("B", "Trojan", emptyList(), null, emptyList()),
        )

        val global = proxies.toProxyGroupSnapshots(Mode.GLOBAL)
        val rule = proxies.toProxyGroupSnapshots(Mode.RULE)

        assertEquals(listOf("GLOBAL", "B", "A", "Z"), global.map { it.name })
        assertEquals(listOf("B", "A", "Z"), rule.map { it.name })
        assertEquals(setOf("GLOBAL", "B", "A", "Z"), global.first().proxyDetails.keys)
        assertEquals(setOf("B", "A", "Z"), rule.first().proxyDetails.keys)
    }

    @Test
    fun proxyMappingPreservesGroupClassificationAndSelectedNestedGroup() {
        val mapped = listOf(
            Proxy("manual", "Selector", listOf("node-a"), "node-a", emptyList()),
            Proxy("automatic", "URLTest", emptyList(), null, emptyList()),
            Proxy("fallback", "Fallback", emptyList(), "node-a", emptyList()),
            Proxy("node-a", "Shadowsocks", emptyList(), null, listOf(DelayHistory("10", 10))),
        ).toProxyGroupSnapshots(Mode.RULE).associateBy { it.name }

        assertEquals(true, mapped.getValue("manual").proxyDetails.getValue("manual").isGroup)
        assertEquals("node-a", mapped.getValue("manual").proxyDetails.getValue("manual").now)
        assertEquals(true, mapped.getValue("automatic").proxyDetails.getValue("automatic").isGroup)
        assertEquals(true, mapped.getValue("fallback").proxyDetails.getValue("fallback").isGroup)
        assertEquals(false, mapped.getValue("node-a").proxyDetails.getValue("node-a").isGroup)
    }

    @Test
    fun subtitlePatternFormatsOnlyLeafProxyAndPreservesSelectionIdentity() {
        val proxies = listOf(
            Proxy("AUTO 香港", "Selector", listOf("香港-节点 01"), "香港-节点 01", emptyList()),
            Proxy("香港-节点 01", "VLESS", emptyList(), null, emptyList()),
        )

        val mapped = proxies.toProxyGroupSnapshots(
            Mode.RULE,
            Pattern.compile("香港", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
        )
        val snapshot = mapped.first { it.name == "AUTO 香港" }
        val group = snapshot.proxyDetails.getValue("AUTO 香港")
        val node = snapshot.proxyDetails.getValue("香港-节点 01")

        assertEquals("AUTO 香港", group.title)
        assertEquals("Selector", group.subtitle)
        assertEquals("香港-节点 01", snapshot.proxies.single())
        assertEquals("香港-节点 01", node.name)
        assertEquals("-节点 01", node.title)
        assertEquals("香港", node.subtitle)
    }

    @Test
    fun connectionMappingPreservesControllerMetadataAndTotals() {
        val response = ConnectionsResponse(
            downloadTotal = 100,
            uploadTotal = 200,
            memory = 300,
            connections = listOf(
                Connection(
                    id = "connection-1",
                    metadata = Metadata(
                        network = "tcp",
                        metadataType = "HTTP",
                        sourceIp = "10.0.0.2",
                        destinationIp = null,
                        sourcePort = 1234u,
                        destinationPort = 443u,
                        host = "example.com",
                    ),
                    upload = 11,
                    download = 22,
                    start = "456",
                    chains = listOf("AUTO", "A"),
                    rule = "MATCH",
                ),
            ),
        )

        val mapped = response.toConnectionsSnapshot()
        val connection = mapped.connections.single()

        assertEquals(100L, mapped.downloadTotal)
        assertEquals(200L, mapped.uploadTotal)
        assertEquals("connection-1", connection.id)
        assertEquals("example.com", connection.host)
        assertEquals(11L, connection.upload)
        assertEquals(22L, connection.download)
        assertEquals(listOf("AUTO", "A"), connection.chains)
        assertEquals("MATCH", connection.rule)
        assertEquals("tcp", connection.network)
        assertEquals("10.0.0.2", connection.sourceIp)
        assertEquals("", connection.destinationIp)
        assertEquals("1234", connection.sourcePort)
        assertEquals("443", connection.destinationPort)
    }

    @Test
    fun missingOptionalSourceMetadataMapsToEmptyStrings() {
        val response = ConnectionsResponse(
            downloadTotal = 0,
            uploadTotal = 0,
            memory = null,
            connections = listOf(
                Connection(
                    id = "connection-1",
                    metadata = Metadata(
                        network = "udp",
                        metadataType = "",
                        sourceIp = "",
                        destinationIp = "1.1.1.1",
                        sourcePort = null,
                        destinationPort = 53u,
                        host = "",
                    ),
                    upload = 0,
                    download = 0,
                    start = "invalid",
                    chains = emptyList(),
                    rule = null,
                ),
            ),
        )

        val mapped = response.toConnectionsSnapshot().connections.single()

        assertEquals("", mapped.sourcePort)
    }
}
