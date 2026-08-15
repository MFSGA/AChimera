package rs.chimera.android.backend

import android.content.Context
import androidx.core.content.edit

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
        prefs.edit(commit = true) {
            if (state.lastAttempt == null) remove(key(id, LAST_ATTEMPT))
            else putLong(key(id, LAST_ATTEMPT), state.lastAttempt)
            putInt(key(id, FAILURE_COUNT), state.failureCount.coerceAtLeast(0))
            if (state.nextAttemptAt == null) remove(key(id, NEXT_ATTEMPT))
            else putLong(key(id, NEXT_ATTEMPT), state.nextAttemptAt)
            if (state.lastError == null) remove(key(id, LAST_ERROR))
            else putString(key(id, LAST_ERROR), state.lastError)
        }
    }

    fun clear(id: String) {
        prefs.edit(commit = true) {
            listOf(LAST_ATTEMPT, FAILURE_COUNT, NEXT_ATTEMPT, LAST_ERROR).forEach { suffix ->
                remove(key(id, suffix))
            }
        }
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
