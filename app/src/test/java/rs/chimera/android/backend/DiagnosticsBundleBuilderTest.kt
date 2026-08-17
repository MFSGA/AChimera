package rs.chimera.android.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.TrafficSnapshot
import rs.chimera.android.backend.model.VpnSystemStatus

class DiagnosticsBundleBuilderTest {
    @Test
    fun `bundle includes useful runtime state without profile identity fields`() {
        val bundle = DiagnosticsBundleBuilder.build(
            input(
                activeProfile = ProfileSummary(
                    id = "private-profile-id",
                    name = "Alice private subscription",
                    filePath = "/data/user/0/rs.chimera.android/files/profiles/private.yaml",
                    createdAt = 100L,
                    type = ProfileType.REMOTE,
                    isActive = true,
                    isRemote = true,
                    lastUpdated = 200L,
                    fileSize = 4096L,
                    url = "https://alice:secret@example.com/config?token=private-token",
                    autoUpdate = true,
                    userAgent = "private-user-agent",
                    proxyUrl = "http://proxy-user:proxy-pass@127.0.0.1:7890",
                    lastAutoUpdateAttempt = 300L,
                    autoUpdateFailures = 2,
                    nextAutoUpdateAt = 400L,
                    lastAutoUpdateError = "download failed",
                ),
            ),
        )

        assertTrue(bundle.contains("state=RUNNING"))
        assertTrue(bundle.contains("type=REMOTE"))
        assertTrue(bundle.contains("file_size_bytes=4096"))
        assertTrue(bundle.contains("auto_update_failures=2"))
        assertTrue(bundle.contains("download_total_bytes=1234"))
        assertTrue(bundle.contains("upload_total_bytes=567"))
        assertTrue(bundle.contains("connection_count=3"))
        assertTrue(bundle.contains("memory_in_use_bytes=8192"))
        assertTrue(bundle.contains("memory_os_limit_bytes=16384"))
        assertTrue(bundle.contains("proxy_group_count=4"))
        assertFalse(bundle.contains("private-profile-id"))
        assertFalse(bundle.contains("Alice private subscription"))
        assertFalse(bundle.contains("private.yaml"))
        assertFalse(bundle.contains("example.com/config"))
        assertFalse(bundle.contains("private-token"))
        assertFalse(bundle.contains("private-user-agent"))
        assertFalse(bundle.contains("proxy-user"))
        assertFalse(bundle.contains("proxy-pass"))
    }

    @Test
    fun `bundle sanitizes credentials and app private paths in errors and logs`() {
        val privateRoot = "/data/user/0/rs.chimera.android"
        val bundle = DiagnosticsBundleBuilder.build(
            input(
                serviceError =
                    "failed $privateRoot/cache/chimera.log token=service-secret\nsecond line",
                runtimeError = BackendRuntimeError(
                    source = BackendRuntimeErrorSource.TRAFFIC,
                    message = "Authorization: Bearer runtime-secret",
                ),
                runtimeLogs = """
                    profile=$privateRoot/files/profiles/private.yaml
                    proxy=http://alice:password@127.0.0.1:7890
                    download=https://example.com/config?access_token=abc123&safe=yes
                    api_key=private-api-key
                """.trimIndent(),
                privatePathPrefixes = listOf(privateRoot, "$privateRoot/files"),
            ),
        )

        assertTrue(bundle.contains("<app-private>/cache/chimera.log"))
        assertTrue(bundle.contains("<app-private>/profiles/private.yaml"))
        assertTrue(bundle.contains("Authorization: ***"))
        assertTrue(bundle.contains("http://***:***@127.0.0.1:7890"))
        assertTrue(bundle.contains("access_token=***&safe=yes"))
        assertTrue(bundle.contains("api_key=***"))
        assertFalse(bundle.contains(privateRoot))
        assertFalse(bundle.contains("service-secret"))
        assertFalse(bundle.contains("runtime-secret"))
        assertFalse(bundle.contains("password"))
        assertFalse(bundle.contains("abc123"))
        assertFalse(bundle.contains("private-api-key"))
    }

    @Test
    fun `bundle records absent optional state explicitly`() {
        val bundle = DiagnosticsBundleBuilder.build(
            input(
                activeProfile = null,
                runtimeError = null,
                runtimeLogs = "",
            ),
        )

        assertTrue(bundle.contains("[active_profile]\npresent=false"))
        assertTrue(bundle.contains("[backend_runtime_error]\npresent=false"))
        assertTrue(bundle.contains("[runtime_logs]\nnone"))
    }

    private fun input(
        serviceError: String? = null,
        activeProfile: ProfileSummary? = null,
        runtimeError: BackendRuntimeError? = null,
        runtimeLogs: String = "service healthy",
        privatePathPrefixes: List<String> = emptyList(),
    ) = DiagnosticsBundleInput(
        generatedAtEpochMillis = 1_700_000_000_000L,
        appVersion = "0.8.0.dev.test",
        appVersionCode = 321L,
        androidSdk = 36,
        serviceState = ServiceState.RUNNING,
        serviceError = serviceError,
        vpnSystemStatus = VpnSystemStatus(
            observed = true,
            serviceActive = true,
            alwaysOn = false,
            lockdown = false,
            observedAt = 1_699_999_999_000L,
        ),
        activeProfile = activeProfile,
        traffic = TrafficSnapshot(
            downloadTotal = 1234L,
            uploadTotal = 567L,
            connectionCount = 3,
        ),
        memoryInfo = MemoryInfo(
            inUse = 8192L,
            osLimit = 16384L,
        ),
        proxyGroupCount = 4,
        runtimeError = runtimeError,
        runtimeLogs = runtimeLogs,
        privatePathPrefixes = privatePathPrefixes,
    )
}
