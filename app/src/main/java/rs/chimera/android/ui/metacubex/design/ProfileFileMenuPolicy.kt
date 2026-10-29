package rs.chimera.android.ui.metacubex.design

internal enum class ProfileFileMenuAction {
    IMPORT,
    EXPORT,
    RENAME,
    DELETE,
}

internal object ProfileFileMenuPolicy {
    fun visibleActions(
        isDirectory: Boolean,
        size: Long,
        currentInBaseDirectory: Boolean,
        configurationEditable: Boolean,
    ): List<ProfileFileMenuAction> = buildList {
        if (!isDirectory && (!currentInBaseDirectory || configurationEditable)) {
            add(ProfileFileMenuAction.IMPORT)
        }
        if (!isDirectory && size > 0L) {
            add(ProfileFileMenuAction.EXPORT)
        }
        if (!currentInBaseDirectory) {
            add(ProfileFileMenuAction.RENAME)
            add(ProfileFileMenuAction.DELETE)
        }
    }
}
