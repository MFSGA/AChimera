package rs.chimera.android.ui.metacubex.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.ui.metacubex.design.ProxyCardView
import rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry
import rs.chimera.android.ui.metacubex.design.ProxySelectionColorTransition

class ProxyAdapter(
    private val onItemClick: (String) -> Unit,
) : RecyclerView.Adapter<ProxyAdapter.ViewHolder>() {
    private var entries: List<ProxyDisplayEntry> = emptyList()

    // Position-based refreshes can rebind a holder to another proxy while a color transition is running.
    private val selectionTransitions = mutableMapOf<Pair<String, String>, ProxySelectionColorTransition>()

    val currentList: List<ProxyDisplayEntry>
        get() = entries

    var selectable: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    var columns: Int = 2
        set(value) {
            if (field == value) return
            field = value
            selectionTransitions.values.forEach(ProxySelectionColorTransition::reset)
            notifyItemRangeChanged(0, itemCount)
        }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ProxyCardView(parent.context))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.bind(
            entry,
            columns,
            selectable,
            onItemClick,
            selectionTransition = selectionTransitionFor(entry),
        )
    }

    /** Mirrors CMFA's proxy adapter: refresh positions instead of diffing/repositioning holders. */
    fun submitList(
        newEntries: List<ProxyDisplayEntry>,
        commitCallback: (() -> Unit)? = null,
    ) {
        val identities = newEntries.mapTo(mutableSetOf()) { it.name to it.selectionGroupName }
        selectionTransitions.keys.retainAll(identities)
        val oldSize = entries.size
        if (oldSize == newEntries.size) {
            entries = newEntries
            if (newEntries.isNotEmpty()) notifyItemRangeChanged(0, newEntries.size)
        } else {
            if (oldSize > 0) notifyItemRangeRemoved(0, oldSize)
            entries = newEntries
            if (newEntries.isNotEmpty()) notifyItemRangeInserted(0, newEntries.size)
        }
        commitCallback?.invoke()
    }

    /** Immediately updates attached cards before RecyclerView processes the range change. */
    fun rebindVisibleItems(
        recyclerView: RecyclerView,
        entries: List<ProxyDisplayEntry>,
        forceSnapSelectionColor: Boolean = false,
    ) {
        val entriesByIdentity = entries.associateBy { it.name to it.selectionGroupName }
        repeat(recyclerView.childCount) { childIndex ->
            val child = recyclerView.getChildAt(childIndex) ?: return@repeat
            val holder = recyclerView.getChildViewHolder(child) as? ViewHolder ?: return@repeat
            val boundEntry = (child as? ProxyCardView)?.entry
            val identity = boundEntry?.let { it.name to it.selectionGroupName }
            val entry = identity?.let(entriesByIdentity::get)
            if (entry != null) {
                holder.bind(
                    entry,
                    columns,
                    selectable,
                    onItemClick,
                    forceSnapSelectionColor,
                    selectionTransitionFor(entry),
                )
            }
            // CMFA invalidates every visible child after proxy state changes.
            child.postInvalidate()
        }
    }

    fun resetSelectionTransitions() {
        selectionTransitions.values.forEach(ProxySelectionColorTransition::reset)
    }

    private fun selectionTransitionFor(entry: ProxyDisplayEntry): ProxySelectionColorTransition =
        selectionTransitions.getOrPut(entry.name to entry.selectionGroupName) {
            ProxySelectionColorTransition()
        }

    class ViewHolder(
        private val view: ProxyCardView,
    ) : RecyclerView.ViewHolder(view) {
        internal fun bind(
            entry: ProxyDisplayEntry,
            columnCount: Int,
            selectable: Boolean,
            onItemClick: (String) -> Unit,
            forceSnapSelectionColor: Boolean = false,
            selectionTransition: ProxySelectionColorTransition,
        ) {
            view.columns = columnCount
            view.bindEntry(
                entry,
                forceSnapSelectionColor,
                transition = selectionTransition,
                preserveExistingTransition = true,
            )
            view.setOnClickListener(
                if (selectable) View.OnClickListener { onItemClick(entry.name) } else null,
            )
            view.isClickable = selectable
            view.isFocusable = selectable
        }
    }
}
