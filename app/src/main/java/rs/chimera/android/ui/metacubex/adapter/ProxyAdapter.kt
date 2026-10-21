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

    class ViewHolder(
        private val view: ProxyCardView,
    ) : RecyclerView.ViewHolder(view) {
        fun bind(
            entry: ProxyDisplayEntry,
            columnCount: Int,
            selectable: Boolean,
            onItemClick: (String) -> Unit,
        ) {
            view.columns = columnCount
            view.entry = entry
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
