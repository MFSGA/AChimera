package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.viewmodel.AppFilterMode

class AccessControlSelectionPolicyTest {
    @Test
    fun allModeIgnoresStoredSelections() {
        val selection = AccessControlSelectionPolicy.selection(
            AppFilterMode.ALL,
            allowedApps = setOf("allowed.app"),
            disallowedApps = setOf("blocked.app"),
        )

        assertEquals(emptySet<String>(), AccessControlSelectionPolicy.selectedApps(
            AppFilterMode.ALL,
            allowedApps = setOf("allowed.app"),
            disallowedApps = setOf("blocked.app"),
        ))
        assertEquals(AccessControlSelectionPolicy.Selection(AppFilterMode.ALL, emptySet(), emptySet()), selection)
        assertEquals(false, AccessControlSelectionPolicy.canSelectApps(AppFilterMode.ALL))
    }

    @Test
    fun filteredModesUseOnlyTheirApplicableSelection() {
        assertEquals(
            setOf("allowed.app"),
            AccessControlSelectionPolicy.selectedApps(
                AppFilterMode.ALLOWED,
                allowedApps = setOf("allowed.app"),
                disallowedApps = setOf("blocked.app"),
            ),
        )
        assertEquals(
            setOf("blocked.app"),
            AccessControlSelectionPolicy.selectedApps(
                AppFilterMode.DISALLOWED,
                allowedApps = setOf("allowed.app"),
                disallowedApps = setOf("blocked.app"),
            ),
        )
        assertEquals(true, AccessControlSelectionPolicy.canSelectApps(AppFilterMode.ALLOWED))
        assertEquals(true, AccessControlSelectionPolicy.canSelectApps(AppFilterMode.DISALLOWED))
    }
}
