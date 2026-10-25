package rs.chimera.android.ui.metacubex.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.ui.metacubex.design.ProxyCardView
import rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry

class ProxyAdapter(
    private val onItemClick: (String) -> Unit,
) : ListAdapter<ProxyDisplayEntry, ProxyAdapter.ViewHolder>(DiffCallback) {
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
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ProxyCardView(parent.context))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), columns, selectable, onItemClick)
    }

    /** Immediately updates attached rows while the submitted list diff is still pending. */
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
                )
            }
            // CMFA invalidates every visible child after proxy state changes. Keep this
            // independent of adapter positions, which may be NO_POSITION during a diff.
            child.postInvalidate()
        }
    }

    class ViewHolder(
        private val view: ProxyCardView,
    ) : RecyclerView.ViewHolder(view) {
        fun bind(
            entry: ProxyDisplayEntry,
            columnCount: Int,
            selectable: Boolean,
            onItemClick: (String) -> Unit,
            forceSnapSelectionColor: Boolean = false,
        ) {
            view.columns = columnCount
            view.bindEntry(entry, forceSnapSelectionColor)
            view.setOnClickListener(
                if (selectable) View.OnClickListener { onItemClick(entry.name) } else null,
            )
            view.isClickable = selectable
            view.isFocusable = selectable
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProxyDisplayEntry>() {
        override fun areItemsTheSame(oldItem: ProxyDisplayEntry, newItem: ProxyDisplayEntry): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: ProxyDisplayEntry, newItem: ProxyDisplayEntry): Boolean =
            oldItem == newItem
    }
}
