package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class ProfilePendingMarkerPolicyTest {
    @Test
    fun falseAndWrongTypedImportMarkersAreRejected() {
        val markers = profileImportPendingMarkers(
            mapOf(
                profileImportPendingKey("pending.yaml") to true,
                profileImportPendingKey("false.yaml") to false,
                profileImportPendingKey("typed.yaml") to "true",
                "other" to true,
            ),
        )

        assertEquals(setOf("pending.yaml"), markers.names)
        assertEquals(
            setOf(
                profileImportPendingKey("false.yaml"),
                profileImportPendingKey("typed.yaml"),
            ),
            markers.invalidKeys,
        )
    }

    @Test
    fun committedTargetClearsAllMatchingBackupMarkers() {
        val first = ".profile.yaml.${UUID.randomUUID()}.backup"
        val second = ".profile.yaml.${UUID.randomUUID()}.backup"
        val other = ".other.yaml.${UUID.randomUUID()}.backup"
        val entries = mapOf(
            profileUpdatePendingKey(first) to true,
            profileUpdatePendingKey(second) to false,
            profileUpdatePendingKey(other) to true,
            profileUpdatePendingKey("../$first") to true,
        )

        assertEquals(
            setOf(
                profileUpdatePendingKey(first),
                profileUpdatePendingKey(second),
            ),
            profileUpdatePendingKeysForTarget(entries, "profile.yaml"),
        )
    }

    @Test
    fun falseAndWrongTypedUpdateMarkersAreRejected() {
        val markers = profileUpdatePendingMarkers(
            mapOf(
                profileUpdatePendingKey(".profile.yaml.123.backup") to true,
                profileUpdatePendingKey(".false.backup") to false,
                profileUpdatePendingKey(".typed.backup") to 1,
            ),
        )

        assertEquals(setOf(".profile.yaml.123.backup"), markers.names)
        assertEquals(
            setOf(
                profileUpdatePendingKey(".false.backup"),
                profileUpdatePendingKey(".typed.backup"),
            ),
            markers.invalidKeys,
        )
    }
}
