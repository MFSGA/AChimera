package rs.chimera.android.backend

internal object ProfilePersistencePolicy {
    fun commit(
        persist: () -> Boolean,
        afterCommit: () -> Unit = {},
    ) {
        check(persist()) { "Failed to persist profile catalog" }
        afterCommit()
    }
}
