package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyDelayTextStateTest {
    @Test
    fun unknownDelayOnFirstBindingRemainsBlankLikeCmfa() {
        val state = ProxyDelayTextState()

        assertEquals("", state.update(entry(delayMillis = null)))
    }

    @Test
    fun changedDelayToUnknownClearsTextLikeCmfa() {
        val state = ProxyDelayTextState()

        assertEquals("42", state.update(entry(delayMillis = 42)))
        assertEquals("", state.update(entry(delayMillis = null)))
    }

    @Test
    fun aDifferentProxyStartsWithFreshDelayState() {
        val state = ProxyDelayTextState()

        assertEquals("42", state.update(entry(name = "node-a", delayMillis = 42)))
        assertEquals("", state.update(entry(name = "node-b", delayMillis = null)))
    }

    @Test
    fun sameProxyNameInDifferentGroupStartsWithFreshDelayState() {
        val state = ProxyDelayTextState()

        assertEquals("42", state.update(entry(delayMillis = 42, groupName = "group-a")))
        assertEquals("", state.update(entry(delayMillis = null, groupName = "group-b")))
    }

    @Test
    fun outOfRangeDelayRemainsBlank() {
        val state = ProxyDelayTextState()

        assertEquals("42", state.update(entry(delayMillis = 42)))
        assertEquals(
            "",
            state.update(
                entry(delayMillis = null, delayValue = Short.MAX_VALUE + 1),
            ),
        )
    }

    private fun entry(
        name: String = "node",
        delayMillis: Int?,
        delayValue: Int = delayMillis ?: UNKNOWN_PROXY_DELAY,
        groupName: String = "group",
    ) = ProxyDisplayEntry(
        name = name,
        type = "Vless",
        delayText = delayMillis?.toString().orEmpty(),
        delayMillis = delayMillis,
        isSelected = false,
        selectionGroupName = groupName,
        delayValue = delayValue,
    )
}
