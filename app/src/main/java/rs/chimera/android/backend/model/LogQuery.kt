package rs.chimera.android.backend.model

data class LogQuery(
    val maxLines: Int = 160,
    val filter: String? = null,
)

data class LogLine(
    val timestamp: String,
    val level: String,
    val message: String,
)
