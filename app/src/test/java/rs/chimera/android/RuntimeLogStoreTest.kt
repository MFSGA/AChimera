package rs.chimera.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeLogStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun missingLogReadsAsEmpty() {
        val file = temporaryFolder.root.resolve("missing.log")

        assertEquals("", readRuntimeLogTail(file, 10))
    }

    @Test
    fun readReturnsOnlyRequestedTail() {
        val file = temporaryFolder.newFile("runtime.log")
        file.writeText("one\ntwo\nthree\nfour")

        assertEquals("three\nfour", readRuntimeLogTail(file, 2))
    }

    @Test
    fun negativeLineLimitIsRejected() {
        val file = temporaryFolder.newFile("runtime.log")

        assertThrows(IllegalArgumentException::class.java) {
            readRuntimeLogTail(file, -1)
        }
    }

    @Test
    fun directoryCannotBeReadAsLog() {
        val directory = temporaryFolder.newFolder("runtime.log")

        assertThrows(IllegalStateException::class.java) {
            readRuntimeLogTail(directory, 10)
        }
    }

    @Test
    fun clearTruncatesExistingLog() {
        val file = temporaryFolder.newFile("runtime.log")
        file.writeText("content")

        clearRuntimeLog(file)

        assertEquals("", file.readText())
    }

    @Test
    fun directoryCannotBeClearedAsLog() {
        val directory = temporaryFolder.newFolder("runtime.log")

        assertThrows(IllegalStateException::class.java) {
            clearRuntimeLog(directory)
        }
    }
}
