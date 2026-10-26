package rs.chimera.android.backend.model

/** Metadata for one immutable view of a rotated runtime-log backup. */
data class RuntimeLogArchive(
    val index: Int,
    val fileName: String,
    val modifiedAtEpochMillis: Long,
    val sizeBytes: Long,
    val contentSha256: String,
)
