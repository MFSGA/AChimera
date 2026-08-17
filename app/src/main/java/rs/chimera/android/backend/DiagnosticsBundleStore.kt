package rs.chimera.android.backend

import java.io.File

internal object DiagnosticsBundleStore {
    private const val DIRECTORY_NAME = "diagnostics"
    private const val FILE_PREFIX = "chimera-diagnostics-"
    private const val FILE_SUFFIX = ".txt"
    private const val MAX_BUNDLES = 3

    @Synchronized
    fun write(
        cacheDir: File,
        content: String,
        generatedAtEpochMillis: Long,
    ): File {
        val directory = File(cacheDir, DIRECTORY_NAME)
        check(directory.isDirectory || directory.mkdirs()) {
            "Failed to create diagnostics directory"
        }

        val timestamp = generatedAtEpochMillis.coerceAtLeast(0L)
        val target = File(directory, "$FILE_PREFIX$timestamp$FILE_SUFFIX")
        val staged = File(directory, ".${target.name}.tmp")

        try {
            staged.writeText(content, Charsets.UTF_8)
            replace(staged, target)
        } catch (error: Throwable) {
            runCatching { staged.delete() }.onFailure(error::addSuppressed)
            throw error
        }

        pruneOldBundles(directory, keep = target)
        return target
    }

    private fun replace(source: File, destination: File) {
        if (source.renameTo(destination)) return
        source.copyTo(destination, overwrite = true)
        check(source.delete()) { "Failed to remove staged diagnostics bundle" }
    }

    private fun pruneOldBundles(
        directory: File,
        keep: File,
    ) {
        directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter(::isBundle)
            .sortedByDescending(::bundleTimestamp)
            .filterNot { it == keep }
            .drop(MAX_BUNDLES - 1)
            .forEach { old -> runCatching { old.delete() } }
    }

    private fun isBundle(file: File): Boolean =
        file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)

    private fun bundleTimestamp(file: File): Long =
        file.name
            .removePrefix(FILE_PREFIX)
            .removeSuffix(FILE_SUFFIX)
            .toLongOrNull()
            ?: Long.MIN_VALUE
}
