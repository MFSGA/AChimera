package rs.chimera.android.backend

import java.io.File
import java.io.InputStream
import java.io.OutputStream

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
}
