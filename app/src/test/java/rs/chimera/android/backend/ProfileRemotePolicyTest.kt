package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
