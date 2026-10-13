package rs.chimera.android.backend

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCatalogStoreCommitUpdateTest {
    @Test
    fun `successful metadata commit clears only matching backup markers atomically`() {
        val firstBackup = File(".profile.yaml.11111111-1111-4111-8111-111111111111.100.backup")
        val secondBackup = File(".profile.yaml.22222222-2222-4222-8222-222222222222.101.backup")
        val otherBackup = File(".other.yaml.33333333-3333-4333-8333-333333333333.102.backup")
        val firstKey = profileUpdatePendingKey(firstBackup.name)
        val secondKey = profileUpdatePendingKey(secondBackup.name)
        val unrelatedKey = profileUpdatePendingKey(otherBackup.name)
        val invalidKey = profileUpdatePendingKey("not-a-managed-backup")
        val prefs = RecordingPreferences(
            mapOf(
                "profiles_list" to "old-catalog",
                firstKey to true,
                secondKey to false,
                unrelatedKey to true,
                invalidKey to true,
            ),
        )
        val catalog = ProfileCatalogStore(prefs.sharedPreferences, ProfileCatalogCoordinator())

        catalog.commitUpdate(backup = secondBackup, targetName = "profile.yaml") {
            putString("profiles_list", "new-catalog")
        }

        assertEquals(1, prefs.commitCount)
        assertEquals("new-catalog", prefs.values["profiles_list"])
        assertFalse(prefs.values.containsKey(firstKey))
        assertFalse(prefs.values.containsKey(secondKey))
        assertEquals(true, prefs.values[unrelatedKey])
        assertEquals(true, prefs.values[invalidKey])
    }

    @Test
    fun `failed metadata commit retains old catalog and pending markers`() {
        val backup = File(".profile.yaml.44444444-4444-4444-8444-444444444444.100.backup")
        val key = profileUpdatePendingKey(backup.name)
        val prefs = RecordingPreferences(mapOf("profiles_list" to "old-catalog", key to true))
        prefs.allowCommit = false
        val catalog = ProfileCatalogStore(prefs.sharedPreferences, ProfileCatalogCoordinator())

        val error = runCatching {
            catalog.commitUpdate(backup = backup, targetName = "profile.yaml") {
                putString("profiles_list", "new-catalog")
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(1, prefs.commitCount)
        assertEquals("old-catalog", prefs.values["profiles_list"])
        assertEquals(true, prefs.values[key])
    }

    /**
     * Only the SharedPreferences API actually used by commitUpdate is supported,
     * so accidental new dependencies in this transaction fail the unit test.
     */
    private class RecordingPreferences(initial: Map<String, Any?>) {
        val values = initial.toMutableMap()
        var commitCount = 0
        var allowCommit = true

        val sharedPreferences: SharedPreferences =
            Proxy.newProxyInstance(
                SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getAll" -> values.toMap()
                    "edit" -> editor()
                    else -> error("Unexpected SharedPreferences method: ${method.name}")
                }
            } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            val removals = mutableSetOf<String>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { proxy, method, arguments ->
                when (method.name) {
                    "putString" -> {
                        pending[arguments!![0] as String] = arguments[1]
                        proxy
                    }

                    "remove" -> {
                        removals += arguments!![0] as String
                        proxy
                    }

                    "commit" -> {
                        commitCount += 1
                        if (allowCommit) {
                            values.putAll(pending)
                            removals.forEach(values::remove)
                        }
                        allowCommit
                    }

                    else -> error("Unexpected SharedPreferences.Editor method: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}
