package rs.chimera.android.backend

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySelectionStoreTest {
    @Test
    fun `selections are scoped by profile and can be removed independently`() {
        val preferences = RecordingPreferences()
        val store = ProxySelectionStore(preferences.sharedPreferences)

        assertTrue(store.save(PROFILE_A, 10L, "group:one", "node-a"))
        assertTrue(store.save(PROFILE_A, 10L, "group-two", "node-b"))
        assertTrue(store.save(PROFILE_B, 3L, "group:one", "node-c"))
        assertTrue(store.save("profile-a:child", null, "group:one", "node-d"))

        assertEquals(
            mapOf("group:one" to "node-a", "group-two" to "node-b"),
            store.read(PROFILE_A, 10L).selections,
        )
        assertEquals(mapOf("group:one" to "node-c"), store.read(PROFILE_B, 3L).selections)

        assertTrue(store.removeGroups(PROFILE_A, listOf("group:one")))
        assertEquals(mapOf("group-two" to "node-b"), store.read(PROFILE_A, 10L).selections)
        assertEquals(mapOf("group:one" to "node-c"), store.read(PROFILE_B, 3L).selections)

        assertTrue(store.removeProfile(PROFILE_A))
        assertTrue(store.read(PROFILE_A, 10L).selections.isEmpty())
        assertEquals(mapOf("group:one" to "node-c"), store.read(PROFILE_B, 3L).selections)
        assertEquals(mapOf("group:one" to "node-d"), store.read("profile-a:child", null).selections)
    }

    @Test
    fun `profile revision changes invalidate old selections before saving a new choice`() {
        val store = ProxySelectionStore(RecordingPreferences().sharedPreferences)
        assertTrue(store.save(PROFILE_A, 10L, "selector", "old-node"))

        val changedRevision = store.read(PROFILE_A, 11L)
        assertFalse(changedRevision.revisionMatches)
        assertTrue(changedRevision.hasSelections)
        assertTrue(changedRevision.selections.isEmpty())

        assertTrue(store.save(PROFILE_A, 11L, "selector", "new-node"))
        assertEquals(mapOf("selector" to "new-node"), store.read(PROFILE_A, 11L).selections)
        assertTrue(store.read(PROFILE_A, 10L).selections.isEmpty())
    }

    @Test
    fun `failed persistence is reported without changing the stored selection`() {
        val preferences = RecordingPreferences()
        val store = ProxySelectionStore(preferences.sharedPreferences)
        assertTrue(store.save(PROFILE_A, 10L, "selector", "node-a"))
        preferences.allowCommit = false

        assertFalse(store.save(PROFILE_A, 10L, "selector", "node-b"))
        assertEquals(mapOf("selector" to "node-a"), store.read(PROFILE_A, 10L).selections)
    }

    private class RecordingPreferences {
        val values = mutableMapOf<String, Any?>()
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

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
    }
}
