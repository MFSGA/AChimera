package rs.chimera.android.service

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import rs.chimera.android.backend.model.RuntimeLogArchive

internal class RuntimeLogStore(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val backupCount: Int = DEFAULT_BACKUP_COUNT,
) {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
        require(backupCount >= 0) { "backupCount must not be negative" }
    }

    fun append(file: File, line: String) {
        val bytes = "${RuntimeLogSanitizer.sanitizeText(line)}\n".toByteArray()
        synchronized(FILE_LOCK) {
            validateFilePath(file)
            file.parentFile?.mkdirs()
            rotateIfNeededLocked(file, bytes.size.toLong())
            FileOutputStream(file, true).use { output -> output.write(bytes) }
            rotateIfNeededLocked(file, incomingBytes = 0)
        }
    }

    fun readTail(file: File, maxLines: Int): String {
        require(maxLines >= 0) { "maxLines must not be negative" }
        if (maxLines == 0) return ""

        return synchronized(FILE_LOCK) {
            validateFilePath(file)
            rotateIfNeededLocked(file, incomingBytes = 0)
            val lines = buildList {
                for (index in backupCount downTo 1) {
                    addAll(readLines(backupFile(file, index)))
                }
                addAll(readLines(file))
            }
            RuntimeLogSanitizer.sanitizeText(lines.takeLast(maxLines).joinToString("\n"))
        }
    }

    fun listArchives(file: File): List<RuntimeLogArchive> = synchronized(FILE_LOCK) {
        validateFilePath(file)
        (1..backupCount).mapNotNull { index ->
            val backup = backupFile(file, index)
            if (!backup.exists()) return@mapNotNull null
            check(backup.isFile) { "Runtime log path is not a file: ${backup.name}" }
            val contents = backup.readBytes()
            val sizeBytes = contents.size.toLong()
            if (sizeBytes == 0L) return@mapNotNull null
            RuntimeLogArchive(
                index = index,
                fileName = backup.name,
                modifiedAtEpochMillis = backup.lastModified(),
                sizeBytes = sizeBytes,
                contentSha256 = sha256(contents),
            )
        }.sortedWith(
            compareByDescending<RuntimeLogArchive> { it.modifiedAtEpochMillis }
                .thenBy { it.index },
        )
    }

    fun readArchive(file: File, archive: RuntimeLogArchive): String = synchronized(FILE_LOCK) {
        validateFilePath(file)
        val backup = backupFile(file, archive.index)
        check(backup.isFile) { "Runtime log archive changed; refresh the list" }
        val contents = backup.readBytes()
        check(
            backup.name == archive.fileName &&
                backup.lastModified() == archive.modifiedAtEpochMillis &&
                contents.size.toLong() == archive.sizeBytes &&
                sha256(contents) == archive.contentSha256,
        ) { "Runtime log archive changed; refresh the list" }
        RuntimeLogSanitizer.sanitizeText(String(contents, Charsets.UTF_8))
    }

    fun clear(file: File) {
        synchronized(FILE_LOCK) {
            validateFilePath(file)
            if (file.exists()) file.writeText("")
            for (index in 1..backupCount) {
                val backup = backupFile(file, index)
                if (backup.exists()) {
                    check(backup.delete()) {
                        "Unable to delete runtime log backup: ${backup.absolutePath}"
                    }
                }
            }
        }
    }

    internal fun backupFile(file: File, index: Int): File {
        require(index in 1..backupCount) { "backup index is out of range: $index" }
        return File("${file.absolutePath}.$index")
    }

    private fun rotateIfNeededLocked(file: File, incomingBytes: Long) {
        if (!file.exists() || file.length() + incomingBytes <= maxBytes) return

        for (index in backupCount downTo 2) {
            moveReplacing(
                source = backupFile(file, index - 1),
                target = backupFile(file, index),
            )
        }
        if (backupCount > 0) {
            copyTail(file, backupFile(file, 1))
        }
        RandomAccessFile(file, "rw").use { it.setLength(0) }
    }

    private fun copyTail(source: File, target: File) {
        target.parentFile?.mkdirs()
        RandomAccessFile(source, "r").use { input ->
            val length = input.length()
            val start = (length - maxBytes).coerceAtLeast(0)
            input.seek(start)
            FileOutputStream(target, false).use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var remaining = length - start
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    private fun moveReplacing(source: File, target: File) {
        if (!source.exists()) return
        if (target.exists()) {
            check(target.delete()) {
                "Unable to replace runtime log backup: ${target.absolutePath}"
            }
        }
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            check(source.delete()) {
                "Unable to remove old runtime log backup: ${source.absolutePath}"
            }
        }
    }

    private fun readLines(file: File): List<String> {
        if (!file.exists()) return emptyList()
        check(file.isFile) { "Runtime log path is not a file: ${file.name}" }
        return file.useLines { it.toList() }
    }

    private fun validateFilePath(file: File) {
        if (file.exists()) {
            check(file.isFile) { "Runtime log path is not a file: ${file.name}" }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { it.toUByte().toString(16).padStart(2, '0') }

    companion object {
        const val DEFAULT_MAX_BYTES = 1024L * 1024L
        const val DEFAULT_BACKUP_COUNT = 2
        val shared = RuntimeLogStore()

        private const val COPY_BUFFER_BYTES = 16 * 1024
        private val FILE_LOCK = Any()
    }
}
