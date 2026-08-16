package rs.chimera.android.backend

internal object ProfileActivePathPolicy {
    fun persist(
        activePath: String?,
        put: (String) -> Unit,
        remove: () -> Unit,
    ) {
        if (activePath == null) {
            remove()
        } else {
            put(activePath)
        }
    }
}
