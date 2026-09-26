package rs.chimera.android.backend

import android.content.Context

internal class ProfileAutoUpdateStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(id: String): ProfileAutoUpdateState = synchronized(PROCESS_LOCK) {
        readState(id)
    }

    private fun readState(id: String): ProfileAutoUpdateState =
        ProfileAutoUpdateState(
            lastAttempt = readLong(key(id, LAST_ATTEMPT)).takeIf { it > 0L },
            failureCount = readInt(key(id, FAILURE_COUNT)).coerceAtLeast(0),
            nextAttemptAt = readLong(key(id, NEXT_ATTEMPT)).takeIf { it > 0L },
            lastError = readString(key(id, LAST_ERROR)),
            runtimeApplyPending = readBoolean(key(id, RUNTIME_APPLY_PENDING)),
            sourceFingerprint = readString(key(id, SOURCE_FINGERPRINT)),
            profileRevision = readOptionalLong(key(id, PROFILE_REVISION)),
        )

    private fun readLong(key: String): Long =
        try {
            prefs.getLong(key, 0L)
        } catch (_: ClassCastException) {
            0L
        }

    private fun readOptionalLong(key: String): Long? =
        if (!prefs.contains(key)) {
            null
        } else {
            try {
                prefs.getLong(key, 0L)
            } catch (_: ClassCastException) {
                null
            }
        }

    private fun readInt(key: String): Int =
        try {
            prefs.getInt(key, 0)
        } catch (_: ClassCastException) {
            0
        }

    private fun readString(key: String): String? =
        try {
            prefs.getString(key, null)
        } catch (_: ClassCastException) {
            null
        }

    private fun readBoolean(key: String): Boolean =
        try {
            prefs.getBoolean(key, false)
        } catch (_: ClassCastException) {
            false
        }

    fun write(id: String, state: ProfileAutoUpdateState) = synchronized(PROCESS_LOCK) {
        writeState(id, state)
    }

    private fun writeState(id: String, state: ProfileAutoUpdateState) {
        val editor = prefs.edit()
        if (state.lastAttempt == null) editor.remove(key(id, LAST_ATTEMPT))
        else editor.putLong(key(id, LAST_ATTEMPT), state.lastAttempt)
        editor.putInt(key(id, FAILURE_COUNT), state.failureCount.coerceAtLeast(0))
        if (state.nextAttemptAt == null) editor.remove(key(id, NEXT_ATTEMPT))
        else editor.putLong(key(id, NEXT_ATTEMPT), state.nextAttemptAt)
        if (state.lastError == null) editor.remove(key(id, LAST_ERROR))
        else editor.putString(key(id, LAST_ERROR), state.lastError)
        editor.putBoolean(key(id, RUNTIME_APPLY_PENDING), state.runtimeApplyPending)
        if (state.sourceFingerprint == null) editor.remove(key(id, SOURCE_FINGERPRINT))
        else editor.putString(key(id, SOURCE_FINGERPRINT), state.sourceFingerprint)
        if (state.profileRevision == null) editor.remove(key(id, PROFILE_REVISION))
        else editor.putLong(key(id, PROFILE_REVISION), state.profileRevision)
        ProfilePersistencePolicy.commitWithRetry(persist = editor::commit)
    }

    fun writeBoundStateIfCurrent(
        id: String,
        state: ProfileAutoUpdateState,
    ): ProfileAutoUpdateState = synchronized(PROCESS_LOCK) {
        val current = readState(id)
        if (!ProfileAutoUpdateStateWritePolicy.canReplace(current, state)) return@synchronized current
        writeState(id, state)
        state
    }

    fun bindLegacyStateIfCurrent(
        id: String,
        expectedLegacyState: ProfileAutoUpdateState,
        boundState: ProfileAutoUpdateState,
    ): ProfileAutoUpdateState? = synchronized(PROCESS_LOCK) {
        val current = readState(id)
        if (current == boundState) return@synchronized boundState
        if (current != expectedLegacyState) return@synchronized null
        writeState(id, boundState)
        boundState
    }

    fun markRuntimeApplyPending(
        id: String,
        state: ProfileAutoUpdateState,
    ): Boolean = synchronized(PROCESS_LOCK) {
        val expectedRevision = state.profileRevision ?: return@synchronized false
        val expectedFingerprint = state.sourceFingerprint ?: return@synchronized true
        val current = readState(id)
        val currentFingerprint = current.sourceFingerprint
        if (currentFingerprint != null && currentFingerprint != expectedFingerprint) {
            return@synchronized true
        }
        val currentRevision = current.profileRevision
        if (currentRevision != null && currentRevision > expectedRevision) return@synchronized true

        if (currentRevision == expectedRevision) {
            val pendingState = state.copy(runtimeApplyPending = true)
            if (!ProfileAutoUpdateStateWritePolicy.canReplace(current, pendingState)) {
                return@synchronized true
            }
            return@synchronized ProfilePersistencePolicy.tryCommitWithRetry {
                prefs.edit()
                    .putBoolean(key(id, RUNTIME_APPLY_PENDING), true)
                    .putString(key(id, SOURCE_FINGERPRINT), expectedFingerprint)
                    .commit()
            }
        }
        if (currentFingerprint == null && current != EMPTY_STATE) return@synchronized true

        writeState(id, state.copy(runtimeApplyPending = true))
        true
    }

    fun clearRuntimeApplyPending(
        id: String,
        expectedProfileRevision: Long,
        expectedSourceFingerprint: String,
    ): Boolean = synchronized(PROCESS_LOCK) {
        val current = readState(id)
        if (
            current.profileRevision != expectedProfileRevision ||
            current.sourceFingerprint != expectedSourceFingerprint
        ) {
            return@synchronized true
        }
        ProfilePersistencePolicy.tryCommitWithRetry {
            prefs.edit().putBoolean(key(id, RUNTIME_APPLY_PENDING), false).commit()
        }
    }

    fun clear(id: String): Boolean = synchronized(PROCESS_LOCK) {
        ProfilePersistencePolicy.tryCommitWithRetry {
            val editor = prefs.edit()
            listOf(
                LAST_ATTEMPT,
                FAILURE_COUNT,
                NEXT_ATTEMPT,
                LAST_ERROR,
                RUNTIME_APPLY_PENDING,
                SOURCE_FINGERPRINT,
                PROFILE_REVISION,
            ).forEach { suffix -> editor.remove(key(id, suffix)) }
            editor.commit()
        }
    }

    private fun key(id: String, suffix: String): String = "$id:$suffix"

    private companion object {
        const val PREFS_NAME = "profile_auto_update"
        const val LAST_ATTEMPT = "last_attempt"
        const val FAILURE_COUNT = "failure_count"
        const val NEXT_ATTEMPT = "next_attempt"
        const val LAST_ERROR = "last_error"
        const val RUNTIME_APPLY_PENDING = "runtime_apply_pending"
        const val SOURCE_FINGERPRINT = "source_fingerprint"
        const val PROFILE_REVISION = "profile_revision"
        val EMPTY_STATE = ProfileAutoUpdateState(
            lastAttempt = null,
            failureCount = 0,
            nextAttemptAt = null,
            lastError = null,
        )
        val PROCESS_LOCK = Any()
    }
}
