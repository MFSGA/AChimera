package rs.chimera.android.backend

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal object ProfileImportPolicy {
    const val MAX_PROFILE_BYTES: Long = 5L * 1024L * 1024L

    fun copyWithLimit(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long = MAX_PROFILE_BYTES,
    ): Long {
        require(maxBytes > 0) { "Profile size limit must be positive" }

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            check(total <= maxBytes) {
                "Profile exceeds maximum size of $maxBytes bytes"
            }
            output.write(buffer, 0, read)
        }
        return total
    }

    fun requireWithinLimit(
        file: File,
        maxBytes: Long = MAX_PROFILE_BYTES,
    ) {
        check(file.length() <= maxBytes) {
            "Profile exceeds maximum size of $maxBytes bytes"
        }
    }

    fun requireUsableDownloadedProfile(
        file: File,
        maxBytes: Long = MAX_PROFILE_BYTES,
    ) {
        requireWithinLimit(file, maxBytes)
        check(file.length() > 0) { "Downloaded profile is empty" }

        val prefix = file.inputStream().buffered().use { input ->
            val buffer = ByteArray(CONTENT_SNIFF_BYTES)
            val read = input.read(buffer)
            if (read <= 0) "" else String(buffer, 0, read, StandardCharsets.UTF_8)
        }.trimStart().lowercase()
        check(
            !prefix.startsWith("<!doctype html") &&
                !prefix.startsWith("<html") &&
                !prefix.contains("<head"),
        ) {
            "Downloaded content is HTML, not a profile configuration"
        }
    }

    private const val CONTENT_SNIFF_BYTES = 4096
}
