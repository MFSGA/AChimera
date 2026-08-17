package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DiagnosticsBundleStoreTest {
    @Test
    fun `writes bundle into private diagnostics directory`() {
        val cacheDir = Files.createTempDirectory("chimera-diagnostics").toFile()

        val file = DiagnosticsBundleStore.write(
            cacheDir = cacheDir,
            content = "support bundle",
            generatedAtEpochMillis = 1234L,
        )

        assertEquals("diagnostics", file.parentFile?.name)
        assertEquals("chimera-diagnostics-1234.txt", file.name)
        assertEquals("support bundle", file.readText())
        assertTrue(file.isFile)
        assertFalse(file.parentFile!!.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun `same timestamp replaces existing bundle without leftovers`() {
        val cacheDir = Files.createTempDirectory("chimera-diagnostics").toFile()

        DiagnosticsBundleStore.write(cacheDir, "old", 100L)
        val replacement = DiagnosticsBundleStore.write(cacheDir, "new", 100L)

        assertEquals("new", replacement.readText())
        assertEquals(
            listOf("chimera-diagnostics-100.txt"),
            replacement.parentFile!!.listFiles().orEmpty().map { it.name }.sorted(),
        )
    }

    @Test
    fun `keeps only three newest bundles and preserves unrelated files`() {
        val cacheDir = Files.createTempDirectory("chimera-diagnostics").toFile()
        val unrelated = cacheDir.resolve("diagnostics/keep-me.txt")
        unrelated.parentFile!!.mkdirs()
        unrelated.writeText("unrelated")

        DiagnosticsBundleStore.write(cacheDir, "one", 100L)
        DiagnosticsBundleStore.write(cacheDir, "two", 200L)
        DiagnosticsBundleStore.write(cacheDir, "three", 300L)
        DiagnosticsBundleStore.write(cacheDir, "four", 400L)

        val names = unrelated.parentFile!!.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(
            listOf(
                "chimera-diagnostics-200.txt",
                "chimera-diagnostics-300.txt",
                "chimera-diagnostics-400.txt",
                "keep-me.txt",
            ),
            names,
        )
        assertTrue(unrelated.isFile)
    }

    @Test
    fun `negative timestamp is normalized without exposing input data`() {
        val cacheDir = Files.createTempDirectory("chimera-diagnostics").toFile()

        val file = DiagnosticsBundleStore.write(cacheDir, "bundle", -99L)

        assertEquals("chimera-diagnostics-0.txt", file.name)
    }
}
