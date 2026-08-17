package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.backend.model.RemoteProfileSettings

class ProfileRemotePolicyTest {
    @Test
    fun acceptsHttpAndHttpsUrls() {
        assertTrue(ProfileRemotePolicy.isValidUrl("http://example.com/config.yaml"))
        assertTrue(ProfileRemotePolicy.isValidUrl("HTTPS://example.com/config.yaml"))
    }

    @Test
    fun trimsWhitespaceBeforeValidation() {
        assertTrue(ProfileRemotePolicy.isValidUrl("  https://example.com/config.yaml  "))
    }

    @Test
    fun rejectsUnsupportedOrHostlessUrls() {
        assertFalse(ProfileRemotePolicy.isValidUrl("ftp://example.com/config.yaml"))
        assertFalse(ProfileRemotePolicy.isValidUrl("https:///config.yaml"))
        assertFalse(ProfileRemotePolicy.isValidUrl("not a url"))
    }

    @Test
    fun requireValidUrlReportsInvalidInput() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileRemotePolicy.requireValidUrl("file:///tmp/config.yaml")
        }
    }

    @Test
    fun remoteFileNameUsesPathExtensionWithoutQuery() {
        assertEquals(
            "profile.yaml",
            ProfileRemotePolicy.storageFileNameForUrl(
                "profile",
                "https://example.com/config.YAML?token=secret",
            ),
        )
    }

    @Test
    fun remoteFileNameDefaultsWhenPathHasNoExtension() {
        assertEquals(
            "profile.yaml",
            ProfileRemotePolicy.storageFileNameForUrl(
                "profile",
                "https://example.com/subscription",
            ),
        )
    }

    @Test
    fun storageFileNameSanitizesExtension() {
        assertEquals(
            "profile.yaml",
            ProfileRemotePolicy.storageFileName("profile", "config.y-a_m l"),
        )
    }

    @Test
    fun storageFileNameDefaultsForBlankExtension() {
        assertEquals(
            "profile.yaml",
            ProfileRemotePolicy.storageFileName("profile", "config."),
        )
    }

    @Test
    fun acceptsOnlyHttpAndHttpsProxyUrlsSupportedByCurrentDownloader() {
        assertTrue(ProfileRemotePolicy.isValidProxyUrl("http://127.0.0.1:7890"))
        assertTrue(ProfileRemotePolicy.isValidProxyUrl("https://user:pass@example.com:8443"))
        assertFalse(ProfileRemotePolicy.isValidProxyUrl("socks5://127.0.0.1:1080"))
        assertFalse(ProfileRemotePolicy.isValidProxyUrl("file:///tmp/proxy"))
        assertFalse(ProfileRemotePolicy.isValidProxyUrl("http:///missing-host"))
    }

    @Test
    fun normalizeRequestTrimsOptionalFieldsAndValidatesProxy() {
        assertEquals(
            RemoteProfileRequest(
                name = "Example",
                url = "https://example.com/config.yaml",
                autoUpdate = true,
                userAgent = "Chimera",
                proxyUrl = "http://user:pass@127.0.0.1:7890",
            ),
            ProfileRemotePolicy.normalizeRequest(
                RemoteProfileRequest(
                    name = " Example ",
                    url = " https://example.com/config.yaml ",
                    autoUpdate = true,
                    userAgent = " Chimera ",
                    proxyUrl = " http://user:pass@127.0.0.1:7890 ",
                ),
            ),
        )
    }

    @Test
    fun normalizeRequestRejectsUnsupportedProxyScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileRemotePolicy.normalizeRequest(
                RemoteProfileRequest(
                    name = null,
                    url = "https://example.com/config.yaml",
                    proxyUrl = "socks5://127.0.0.1:1080",
                ),
            )
        }
    }

    @Test
    fun normalizeSettingsTrimsEditableFields() {
        assertEquals(
            RemoteProfileRequest(
                name = "Example",
                url = "https://example.com/config.yaml",
                autoUpdate = true,
                userAgent = "Chimera",
                proxyUrl = "http://user:pass@127.0.0.1:7890",
            ),
            ProfileRemotePolicy.normalizeRequest(
                RemoteProfileRequest(
                    name = " Example ",
                    url = " https://example.com/config.yaml ",
                    autoUpdate = true,
                    userAgent = " Chimera ",
                    proxyUrl = " http://user:pass@127.0.0.1:7890 ",
                ),
            ),
        )
    }

    @Test
    fun normalizeSettingsRejectsUnsupportedProxyScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileRemotePolicy.normalizeSettings(
                RemoteProfileSettings(
                    name = "Example",
                    url = "https://example.com/config.yaml",
                    autoUpdate = false,
                    proxyUrl = "socks5://127.0.0.1:1080",
                ),
            )
        }
    }

    @Test
    fun normalizeSettingsRejectsBlankName() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileRemotePolicy.normalizeRequest(
                RemoteProfileRequest(
                    name = null,
                    url = "https://example.com/config.yaml",
                    proxyUrl = "socks5://127.0.0.1:1080",
                ),
            )
        }
    }

    @Test
    fun retryStateIsInvalidatedByRemoteUpdateInputsButNotNameOnlyChanges() {
        val current = RemoteProfileCatalogEntry(
            type = "REMOTE",
            url = "https://example.com/config.yaml",
            autoUpdate = true,
            userAgent = "Chimera",
            proxyUrl = "http://127.0.0.1:7890",
            filePath = "/tmp/profile.yaml",
        )
        val unchangedInputs = RemoteProfileSettings(
            name = "Renamed",
            url = current.url!!,
            autoUpdate = current.autoUpdate,
            userAgent = current.userAgent,
            proxyUrl = current.proxyUrl,
        )

        assertFalse(ProfileRemotePolicy.invalidatesAutoUpdateState(current, unchangedInputs))
        assertTrue(
            ProfileRemotePolicy.invalidatesAutoUpdateState(
                current,
                unchangedInputs.copy(autoUpdate = false),
            ),
        )
        assertTrue(
            ProfileRemotePolicy.invalidatesAutoUpdateState(
                current,
                unchangedInputs.copy(url = "https://example.com/new.yaml"),
            ),
        )
        assertTrue(
            ProfileRemotePolicy.invalidatesAutoUpdateState(
                current,
                unchangedInputs.copy(userAgent = null),
            ),
        )
        assertTrue(
            ProfileRemotePolicy.invalidatesAutoUpdateState(
                current,
                unchangedInputs.copy(proxyUrl = null),
            ),
        )
    }
}
