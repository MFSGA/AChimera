package rs.chimera.android.backend

internal object ProfilePersistencePolicy {
    fun commit(persist: () -> Boolean) {
        check(persist()) { "Failed to persist profile catalog" }
    }

    fun commitWithRetry(persist: () -> Boolean) {
        check(tryCommitWithRetry(persist)) { "Failed to persist profile catalog" }
    }

    fun tryCommitWithRetry(persist: () -> Boolean): Boolean = persist() || persist()
}
