package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogEntryTest {
    @Test
    fun serviceTimestampIsDisplayedWithoutInventingMillisecondsOrSeverity() {
        assertEquals(
            listOf(RuntimeLogEntry(message = "service ready", time = "12:34:56")),
            RuntimeLogEntry.parse("[2026-10-24 12:34:56] service ready"),
        )
    }

    @Test
    fun unstructuredNativeLineIsPreservedWithoutGuessingItsLevelOrTime() {
        val line = "[error connecting to a test endpoint]"
        assertEquals(listOf(RuntimeLogEntry(message = line)), RuntimeLogEntry.parse(line))
    }

    @Test
    fun invalidTimestampRemainsPartOfTheOriginalMessage() {
        val line = "[2026-02-30 25:34:56] invalid timestamp"
        assertEquals(listOf(RuntimeLogEntry(message = line)), RuntimeLogEntry.parse(line))
    }

    @Test
    fun repeatedLinesAndBlankContinuationRowsAreNotDiscarded() {
        assertEquals(
            listOf(RuntimeLogEntry("first"), RuntimeLogEntry(""), RuntimeLogEntry("first")),
            RuntimeLogEntry.parse("first\r\n\r\nfirst"),
        )
    }

    @Test
    fun trailingFileSeparatorDoesNotCreateAnExtraLogRow() {
        assertEquals(
            listOf(RuntimeLogEntry("first"), RuntimeLogEntry("")),
            RuntimeLogEntry.parse("first\n\n"),
        )
        assertEquals(listOf(RuntimeLogEntry("first")), RuntimeLogEntry.parse("first\r\n"))
    }

    @Test
    fun emptyLogHasNoSyntheticRows() {
        assertTrue(RuntimeLogEntry.parse("").isEmpty())
        assertTrue(RuntimeLogEntry.parse("\n ").isEmpty())
    }
}
