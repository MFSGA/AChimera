package rs.chimera.android.ui.metacubex.design

import android.content.Context
import androidx.core.content.edit

enum class ProxySortOrder {
    DEFAULT,
    NAME,
    DELAY,
}

data class ProxyDisplayEntry(
    val name: String,
    val type: String,
    val delayText: String,
    val delayMillis: Int?,
    val isSelected: Boolean,
)

data class ProxyDisplayOptions(
    val columns: Int = DEFAULT_COLUMNS,
    val sortOrder: ProxySortOrder = ProxySortOrder.DEFAULT,
) {
    companion object {
        const val DEFAULT_COLUMNS = 2
        const val GRID_SPAN_COUNT = 6
    }
}

object ProxyDisplayPolicy {
    fun normalizeColumns(columns: Int): Int = columns.coerceIn(1, 3)

    fun spanSize(columns: Int): Int =
        ProxyDisplayOptions.GRID_SPAN_COUNT / normalizeColumns(columns)

    fun sort(
        entries: List<ProxyDisplayEntry>,
        order: ProxySortOrder,
    ): List<ProxyDisplayEntry> = when (order) {
        ProxySortOrder.DEFAULT -> entries
        ProxySortOrder.NAME -> entries.sortedBy { it.name.lowercase() }
        ProxySortOrder.DELAY -> entries.sortedWith(
            compareBy<ProxyDisplayEntry> { it.delayMillis == null }
                .thenBy { it.delayMillis },
        )
    }
}

class ProxyDisplayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): ProxyDisplayOptions {
        val columns = preferences.getInt(KEY_COLUMNS, ProxyDisplayOptions.DEFAULT_COLUMNS)
        val sortOrder = preferences.getString(KEY_SORT_ORDER, null)
            ?.let { stored -> ProxySortOrder.entries.firstOrNull { it.name == stored } }
            ?: ProxySortOrder.DEFAULT
        return ProxyDisplayOptions(
            columns = ProxyDisplayPolicy.normalizeColumns(columns),
            sortOrder = sortOrder,
        )
    }

    fun updateColumns(columns: Int): ProxyDisplayOptions {
        val normalized = ProxyDisplayPolicy.normalizeColumns(columns)
        preferences.edit { putInt(KEY_COLUMNS, normalized) }
        return read()
    }

    fun updateSortOrder(sortOrder: ProxySortOrder): ProxyDisplayOptions {
        preferences.edit { putString(KEY_SORT_ORDER, sortOrder.name) }
        return read()
    }

    private companion object {
        const val PREFERENCES_NAME = "metacubex_ui"
        const val KEY_COLUMNS = "proxy_columns"
        const val KEY_SORT_ORDER = "proxy_sort_order"
    }
}
