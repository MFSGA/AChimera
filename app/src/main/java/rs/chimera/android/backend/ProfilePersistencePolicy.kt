package rs.chimera.android.backend

internal object ProfilePersistencePolicy {
    fun commit(persist: () -> Boolean) {
        check(persist()) { "Failed to persist profile catalog" }
    }
}
