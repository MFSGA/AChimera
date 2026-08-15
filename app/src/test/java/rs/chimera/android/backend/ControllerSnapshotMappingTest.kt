package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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

        assertEquals(2, mapped.size)
        assertEquals("AUTO", mapped[0].name)
        assertEquals(listOf("A", "B"), mapped[0].proxies)
        assertEquals("A", mapped[0].selected)
        assertEquals(Mode.GLOBAL, mapped[0].mode)
        assertEquals("VLESS", mapped[0].proxyDetails.getValue("A").type)
        assertEquals(45, mapped[0].proxyDetails.getValue("AUTO").history.single().delay)
        assertEquals(123L, mapped[0].proxyDetails.getValue("AUTO").history.single().time)
        assertEquals(0L, mapped[0].proxyDetails.getValue("A").history.single().time)
        assertEquals(mapped[0].proxyDetails, mapped[1].proxyDetails)
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
        assertNull(connection.process)
        assertEquals(11L, connection.upload)
        assertEquals(22L, connection.download)
        assertEquals(456L, connection.startTime)
        assertEquals(listOf("AUTO", "A"), connection.chains)
        assertEquals("MATCH", connection.rule)
        assertEquals("tcp", connection.metadata["network"])
        assertEquals("HTTP", connection.metadata["type"])
        assertEquals("10.0.0.2", connection.metadata["sourceIp"])
        assertEquals("", connection.metadata["destinationIp"])
        assertEquals("1234", connection.metadata["sourcePort"])
        assertEquals("443", connection.metadata["destinationPort"])
    }

    @Test
    fun invalidConnectionStartFallsBackToZero() {
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

        assertEquals(0L, mapped.startTime)
        assertEquals("", mapped.metadata["sourcePort"])
    }
}
