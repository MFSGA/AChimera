package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileActionMenuViewportTest {
    @Test
    fun viewportFitsAllReferenceActions() {
        val actions = listOf(
            MetaProfileAction.Update,
            MetaProfileAction.Edit,
            MetaProfileAction.Duplicate,
            MetaProfileAction.ShareQrCode,
            MetaProfileAction.Delete,
        )
        val rowHeights = MetaProfileAction.entries.associateWith { action ->
            if (action == MetaProfileAction.Details) 96 else 70
        }

        assertEquals(
            350,
            profileActionMenuViewportHeight(actions, rowHeights, Int.MAX_VALUE),
        )
    }

    @Test
    fun viewportUsesOnlyAvailableReferenceActions() {
        val actions = listOf(
            MetaProfileAction.Update,
            MetaProfileAction.Edit,
            MetaProfileAction.Delete,
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
        val actions = listOf(
            MetaProfileAction.Update,
            MetaProfileAction.Edit,
            MetaProfileAction.Duplicate,
            MetaProfileAction.ShareQrCode,
            MetaProfileAction.Delete,
        )
        val rowHeights = actions.associateWith { 70 }

        assertEquals(
            280,
            profileActionMenuViewportHeight(actions, rowHeights, 280),
        )
    }
}
