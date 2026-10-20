package rs.chimera.android.ui.metacubex.design

import android.content.Context
import androidx.core.content.edit
import rs.chimera.android.backend.model.ProxyGroupSnapshot

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
    /** Set only for a successful selector change that should animate like CMFA's redraw path. */
    val animateSelectionChange: Boolean = false,
)

data class ProxyDisplayOptions(
    val columns: Int = DEFAULT_COLUMNS,
    val sortOrder: ProxySortOrder = ProxySortOrder.DEFAULT,
    val excludeNotSelectable: Boolean = false,
) {
    companion object {
        const val DEFAULT_COLUMNS = 2
        const val GRID_SPAN_COUNT = 6
    }
}

data class ProxyDisplayGroup(
    val name: String,
    val isSelectable: Boolean,
    val proxies: List<ProxyDisplayEntry>,
)

object ProxyDisplayPolicy {
    fun normalizeColumns(columns: Int): Int = columns.coerceIn(1, 3)

    fun spanSize(columns: Int): Int =
        ProxyDisplayOptions.GRID_SPAN_COUNT / normalizeColumns(columns)

    fun sort(
        entries: List<ProxyDisplayEntry>,
        order: ProxySortOrder,
    ): List<ProxyDisplayEntry> = when (order) {
        ProxySortOrder.DEFAULT -> entries
        ProxySortOrder.NAME -> entries.sortedWith { left, right ->
            compareCodePointStrings(left.name, right.name)
        }
        ProxySortOrder.DELAY -> entries.sortedWith(
            compareBy<ProxyDisplayEntry> { it.delayMillis == null }
                .thenBy { it.delayMillis },
        )
    }

    private fun compareCodePointStrings(left: String, right: String): Int {
        var leftOffset = 0
        var rightOffset = 0
        while (leftOffset < left.length && rightOffset < right.length) {
            val leftCodePoint = Character.codePointAt(left, leftOffset)
            val rightCodePoint = Character.codePointAt(right, rightOffset)
            if (leftCodePoint != rightCodePoint) return leftCodePoint.compareTo(rightCodePoint)
            leftOffset += Character.charCount(leftCodePoint)
            rightOffset += Character.charCount(rightCodePoint)
        }
        return when {
            leftOffset == left.length && rightOffset == right.length -> 0
            leftOffset == left.length -> -1
            else -> 1
        }
    }

    fun groupPages(
        snapshots: List<ProxyGroupSnapshot>,
        unavailableType: String,
        excludeNotSelectable: Boolean = false,
        animateSelectionChanges: Boolean = false,
    ): List<ProxyDisplayGroup> {
        val visibleGroupNames = snapshots.asSequence()
            .filter { snapshot ->
                val group = snapshot.proxyDetails[snapshot.name]
                group?.isGroup == true && (!excludeNotSelectable || group.type == SELECTOR_TYPE)
            }
            .map(ProxyGroupSnapshot::name)
            .toSet()

        return snapshots.mapNotNull { snapshot ->
            val group = snapshot.proxyDetails[snapshot.name]
                ?.takeIf { it.isGroup }
                ?: return@mapNotNull null
            if (excludeNotSelectable && group.type != SELECTOR_TYPE) return@mapNotNull null

            ProxyDisplayGroup(
                name = snapshot.name,
                isSelectable = group.type == SELECTOR_TYPE,
                proxies = snapshot.proxies.map { name ->
                    val proxy = snapshot.proxyDetails[name]
                    val type = when {
                        proxy == null -> unavailableType
                        proxy.isGroup && proxy.name in visibleGroupNames ->
                            "${proxy.type}(${proxy.now?.takeIf(String::isNotEmpty) ?: "*"})"
                        else -> proxy.type
                    }
                    val delay = proxy?.history?.lastOrNull()?.delay
                        ?.takeIf { it in 1..Short.MAX_VALUE }
                    ProxyDisplayEntry(
                        name = name,
                        type = type,
                        delayText = delay?.toString().orEmpty(),
                        delayMillis = delay,
                        isSelected = name == snapshot.selected,
                        animateSelectionChange = animateSelectionChanges,
                    )
                },
            )
        }
    }

    private const val SELECTOR_TYPE = "Selector"
}

class ProxyDisplayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): ProxyDisplayOptions {
        val columns = preferences.getInt(KEY_COLUMNS, ProxyDisplayOptions.DEFAULT_COLUMNS)
        val sortOrder = preferences.getString(KEY_SORT_ORDER, null)
            ?.let { stored -> ProxySortOrder.entries.firstOrNull { it.name == stored } }
            ?: ProxySortOrder.DEFAULT
        val excludeNotSelectable = preferences.getBoolean(KEY_EXCLUDE_NOT_SELECTABLE, false)
        return ProxyDisplayOptions(
            columns = ProxyDisplayPolicy.normalizeColumns(columns),
            sortOrder = sortOrder,
            excludeNotSelectable = excludeNotSelectable,
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

    fun updateExcludeNotSelectable(exclude: Boolean): ProxyDisplayOptions {
        preferences.edit { putBoolean(KEY_EXCLUDE_NOT_SELECTABLE, exclude) }
        return read()
    }

    fun readLastGroupName(): String? = preferences.getString(KEY_LAST_GROUP_NAME, null)

    fun updateLastGroupName(name: String?) {
        preferences.edit {
            if (name == null) {
                remove(KEY_LAST_GROUP_NAME)
            } else {
                putString(KEY_LAST_GROUP_NAME, name)
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "metacubex_ui"
        const val KEY_COLUMNS = "proxy_columns"
        const val KEY_SORT_ORDER = "proxy_sort_order"
        const val KEY_EXCLUDE_NOT_SELECTABLE = "proxy_exclude_not_selectable"
        const val KEY_LAST_GROUP_NAME = "proxy_last_group"
    }
}
