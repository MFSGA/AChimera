package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFileMenuPolicyTest {
    @Test
    fun remoteConfigurationAtProfileRootOnlyShowsExport() {
        assertEquals(
            listOf(ProfileFileMenuAction.EXPORT),
            ProfileFileMenuPolicy.visibleActions(
                isDirectory = false,
                size = 128L,
                currentInBaseDirectory = true,
                configurationEditable = false,
            ),
        )
    }

    @Test
    fun editableConfigurationAtProfileRootShowsImportThenExport() {
        assertEquals(
            listOf(ProfileFileMenuAction.IMPORT, ProfileFileMenuAction.EXPORT),
            ProfileFileMenuPolicy.visibleActions(
                isDirectory = false,
                size = 128L,
                currentInBaseDirectory = true,
                configurationEditable = true,
            ),
        )
    }

    @Test
    fun providerFileShowsAllReferenceActionsInReferenceOrder() {
        assertEquals(
            listOf(
                ProfileFileMenuAction.IMPORT,
                ProfileFileMenuAction.EXPORT,
                ProfileFileMenuAction.RENAME,
                ProfileFileMenuAction.DELETE,
            ),
            ProfileFileMenuPolicy.visibleActions(
                isDirectory = false,
                size = 128L,
                currentInBaseDirectory = false,
                configurationEditable = false,
            ),
        )
    }

    @Test
    fun providerDirectoryOnlyShowsRenameAndDelete() {
        assertEquals(
            listOf(ProfileFileMenuAction.RENAME, ProfileFileMenuAction.DELETE),
            ProfileFileMenuPolicy.visibleActions(
                isDirectory = true,
                size = 0L,
                currentInBaseDirectory = false,
                configurationEditable = false,
            ),
        )
    }

    @Test
    fun profileRootDirectoryHasNoReferenceActions() {
        assertEquals(
            emptyList<ProfileFileMenuAction>(),
            ProfileFileMenuPolicy.visibleActions(
                isDirectory = true,
                size = 0L,
                currentInBaseDirectory = true,
                configurationEditable = false,
            ),
        )
    }

    @Test
    fun emptyFilesAreNotExportable() {
        assertEquals(
            listOf(
                ProfileFileMenuAction.IMPORT,
                ProfileFileMenuAction.RENAME,
                ProfileFileMenuAction.DELETE,
            ),
            ProfileFileMenuPolicy.visibleActions(
                isDirectory = false,
                size = 0L,
                currentInBaseDirectory = false,
                configurationEditable = false,
            ),
        )
    }
}
