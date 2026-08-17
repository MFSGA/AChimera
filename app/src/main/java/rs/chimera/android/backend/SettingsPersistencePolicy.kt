package rs.chimera.android.backend

internal object SettingsPersistencePolicy {
    fun commit(persist: () -> Boolean) {
        check(persist()) { "Failed to persist runtime settings" }
    }
}
