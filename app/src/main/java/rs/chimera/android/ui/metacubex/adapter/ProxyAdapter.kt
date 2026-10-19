package rs.chimera.android.ui.metacubex.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.ui.metacubex.design.ProxyCardView
import rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry

class ProxyAdapter(
    private val onItemClick: (String) -> Unit,
) : ListAdapter<ProxyDisplayEntry, ProxyAdapter.ViewHolder>(DiffCallback) {
    var columns: Int = 2
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ProxyCardView(parent.context))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), columns)
    }

    inner class ViewHolder(
        private val view: ProxyCardView,
    ) : RecyclerView.ViewHolder(view) {
        fun bind(entry: ProxyDisplayEntry, columnCount: Int) {
            view.columns = columnCount
            view.entry = entry
            view.setOnClickListener { onItemClick(entry.name) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProxyDisplayEntry>() {
        override fun areItemsTheSame(oldItem: ProxyDisplayEntry, newItem: ProxyDisplayEntry): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: ProxyDisplayEntry, newItem: ProxyDisplayEntry): Boolean =
            oldItem == newItem
    }
}
