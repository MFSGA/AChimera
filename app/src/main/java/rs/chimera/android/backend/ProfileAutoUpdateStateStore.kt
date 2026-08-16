package rs.chimera.android.backend

import android.content.Context

internal class ProfileAutoUpdateStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(id: String): ProfileAutoUpdateState =
        ProfileAutoUpdateState(
            lastAttempt = prefs.getLong(key(id, LAST_ATTEMPT), 0L).takeIf { it > 0L },
            failureCount = prefs.getInt(key(id, FAILURE_COUNT), 0).coerceAtLeast(0),
            nextAttemptAt = prefs.getLong(key(id, NEXT_ATTEMPT), 0L).takeIf { it > 0L },
            lastError = prefs.getString(key(id, LAST_ERROR), null),
        )

    fun write(id: String, state: ProfileAutoUpdateState) {
        val editor = prefs.edit()
        if (state.lastAttempt == null) editor.remove(key(id, LAST_ATTEMPT))
        else editor.putLong(key(id, LAST_ATTEMPT), state.lastAttempt)
        editor.putInt(key(id, FAILURE_COUNT), state.failureCount.coerceAtLeast(0))
        if (state.nextAttemptAt == null) editor.remove(key(id, NEXT_ATTEMPT))
        else editor.putLong(key(id, NEXT_ATTEMPT), state.nextAttemptAt)
        if (state.lastError == null) editor.remove(key(id, LAST_ERROR))
        else editor.putString(key(id, LAST_ERROR), state.lastError)
        ProfilePersistencePolicy.commit(persist = editor::commit)
    }

    fun clear(id: String): Boolean {
        val editor = prefs.edit()
        listOf(LAST_ATTEMPT, FAILURE_COUNT, NEXT_ATTEMPT, LAST_ERROR).forEach { suffix ->
            editor.remove(key(id, suffix))
        }
        return editor.commit()
    }

    private fun key(id: String, suffix: String): String = "$id:$suffix"

    private companion object {
        const val PREFS_NAME = "profile_auto_update"
        const val LAST_ATTEMPT = "last_attempt"
        const val FAILURE_COUNT = "failure_count"
        const val NEXT_ATTEMPT = "next_attempt"
        const val LAST_ERROR = "last_error"
    }
}
