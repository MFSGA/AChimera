package rs.chimera.android.ui.metacubex.design

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** Presentation of the already-sanitized backend log; never guesses severity or missing time. */
data class RuntimeLogEntry(
    val message: String,
    val time: String = "",
    val level: String = "Unknown",
) {
    companion object {
        private val timestampedLine = Regex("^\\[(\\d{4}-\\d{2}-\\d{2} (\\d{2}:\\d{2}:\\d{2}))] (.*)$")

        fun parse(log: String): List<RuntimeLogEntry> {
            if (log.isBlank()) return emptyList()
            val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
            return log.lineSequence().map { line ->
                val match = timestampedLine.matchEntire(line) ?: return@map RuntimeLogEntry(message = line)
                val timestamp = match.groupValues[1]
                val position = ParsePosition(0)
                if (
                    timestampFormat.parse(timestamp, position) != null &&
                    position.index == timestamp.length
                ) {
                    RuntimeLogEntry(message = match.groupValues[3], time = match.groupValues[2])
                } else {
                    RuntimeLogEntry(message = line)
                }
            }.toList()
        }
    }
}
