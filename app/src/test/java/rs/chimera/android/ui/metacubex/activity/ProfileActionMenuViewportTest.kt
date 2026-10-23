package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileActionMenuViewportTest {
    @Test
    fun localOnlyActionsDoNotIncreaseTheCmfaMenuViewport() {
        val rowHeights = MetaProfileAction.entries.associateWith { action ->
            if (action == MetaProfileAction.Details) 96 else 70
        }

        assertEquals(
            350,
            profileActionMenuViewportHeight(MetaProfileAction.entries, rowHeights, Int.MAX_VALUE),
        )
    }

    @Test
    fun viewportUsesOnlyAvailableReferenceActions() {
        val actions = listOf(
            MetaProfileAction.Update,
            MetaProfileAction.Edit,
            MetaProfileAction.Delete,
            MetaProfileAction.Details,
        )

        assertEquals(
            210,
            profileActionMenuViewportHeight(
                actions,
                mapOf(
                    MetaProfileAction.Update to 70,
                    MetaProfileAction.Edit to 70,
                    MetaProfileAction.Delete to 70,
                    MetaProfileAction.Details to 96,
                ),
                Int.MAX_VALUE,
            ),
        )
    }

    @Test
    fun viewportIsCappedByAvailableWindowHeight() {
        val rowHeights = MetaProfileAction.entries.associateWith { 70 }

        assertEquals(
            280,
            profileActionMenuViewportHeight(MetaProfileAction.entries, rowHeights, 280),
        )
    }
}
