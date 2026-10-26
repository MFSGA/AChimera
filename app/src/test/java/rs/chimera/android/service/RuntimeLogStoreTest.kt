package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rs.chimera.android.backend.model.RuntimeLogArchive

class RuntimeLogStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun appendRotatesAndReadPreservesChronologicalOrder() {
        val store = RuntimeLogStore(maxBytes = 24, backupCount = 2)
        val file = temporaryFolder.newFile("runtime.log")

        listOf("first-line", "second-line", "third-line", "fourth-line", "fifth-line")
            .forEach { store.append(file, it) }

        assertTrue(store.backupFile(file, 1).exists())
        assertTrue(store.backupFile(file, 2).exists())
        assertTrue(file.length() <= 24)
        assertEquals(
            listOf("first-line", "second-line", "third-line", "fourth-line", "fifth-line"),
            store.readTail(file, 5).lines(),
        )
    }

    @Test
    fun readRotatesOversizedExternalWriterLog() {
        val store = RuntimeLogStore(maxBytes = 40, backupCount = 1)
        val file = temporaryFolder.newFile("runtime.log")
        file.writeText((0..19).joinToString("\n") { "line-$it" })

        val tail = store.readTail(file, 3)

        assertEquals(listOf("line-17", "line-18", "line-19"), tail.lines())
        assertEquals(0, file.length())
        assertTrue(store.backupFile(file, 1).length() <= 40)
    }

    @Test
    fun clearTruncatesCurrentLogAndDeletesBackups() {
        val store = RuntimeLogStore(maxBytes = 16, backupCount = 2)
        val file = temporaryFolder.newFile("runtime.log")
        repeat(8) { store.append(file, "line-$it") }
        assertTrue(store.backupFile(file, 1).exists())

        store.clear(file)

        assertEquals("", file.readText())
        assertFalse(store.backupFile(file, 1).exists())
        assertFalse(store.backupFile(file, 2).exists())
    }

    @Test
    fun listArchivesReturnsOnlyNonEmptyRotatedBackupsNewestFirst() {
        val store = RuntimeLogStore(maxBytes = 24, backupCount = 3)
        val file = temporaryFolder.newFile("runtime.log")
        val older = store.backupFile(file, 2).apply { writeText("older\n") }
        val newer = store.backupFile(file, 1).apply { writeText("newer\n") }
        val empty = store.backupFile(file, 3)
        empty.writeText("")
        older.setLastModified(1_000L)
        newer.setLastModified(2_000L)

        assertEquals(
            listOf(
                RuntimeLogArchive(1, newer.name, 2_000L, newer.length(), newer.readBytes().sha256()),
                RuntimeLogArchive(2, older.name, 1_000L, older.length(), older.readBytes().sha256()),
            ),
            store.listArchives(file),
        )
    }

    @Test
    fun readArchiveValidatesSnapshotAndSanitizesContents() {
        val store = RuntimeLogStore(maxBytes = 256, backupCount = 2)
        val file = temporaryFolder.newFile("runtime.log")
        val backup = store.backupFile(file, 1).apply {
            writeText("token=private-value\n")
            setLastModified(3_000L)
        }
        val archive = store.listArchives(file).single()

        assertEquals("token=***\n", store.readArchive(file, archive))
        assertThrows(IllegalStateException::class.java) {
            store.readArchive(file, archive.copy(sizeBytes = backup.length() + 1))
        }
        backup.writeText("token=changed-value\n")
        backup.setLastModified(archive.modifiedAtEpochMillis)
        assertEquals(archive.sizeBytes, backup.length())
        assertEquals(archive.modifiedAtEpochMillis, backup.lastModified())
        assertThrows(IllegalStateException::class.java) {
            store.readArchive(file, archive)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.readArchive(file, archive.copy(index = 0))
        }
    }

    @Test
    fun appendSanitizesCredentialsBeforePersistence() {
        val store = RuntimeLogStore(maxBytes = 256, backupCount = 1)
        val file = temporaryFolder.newFile("runtime.log")

        store.append(file, "token=secret https://user:pass@example.com/profile")

        val persisted = file.readText()
        assertTrue(persisted.contains("token=***"))
        assertTrue(persisted.contains("https://***:***@example.com/profile"))
        assertFalse(persisted.contains("secret"))
        assertFalse(persisted.contains("user:pass"))
    }

    @Test
    fun oversizedSingleEntryIsBoundedInBackup() {
        val store = RuntimeLogStore(maxBytes = 32, backupCount = 1)
        val file = temporaryFolder.newFile("runtime.log")

        store.append(file, "x".repeat(100))

        assertEquals(0, file.length())
        assertEquals(32, store.backupFile(file, 1).length())
    }

    @Test
    fun invalidLogPathDoesNotExposeParentDirectory() {
        val store = RuntimeLogStore()
        val directory = temporaryFolder.newFolder("runtime.log")

        val error = assertThrows(IllegalStateException::class.java) {
            store.readTail(directory, 1)
        }

        assertTrue(error.message.orEmpty().contains("runtime.log"))
        assertFalse(error.message.orEmpty().contains(temporaryFolder.root.absolutePath))
    }

    private fun ByteArray.sha256(): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { it.toUByte().toString(16).padStart(2, '0') }
}
