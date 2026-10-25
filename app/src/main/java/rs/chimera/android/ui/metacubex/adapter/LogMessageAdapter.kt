package rs.chimera.android.ui.metacubex.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.databinding.MetaAdapterLogMessageBinding
import rs.chimera.android.ui.metacubex.design.RuntimeLogEntry

class LogMessageAdapter(
    private val copy: (RuntimeLogEntry) -> Unit,
) : ListAdapter<RuntimeLogEntry, LogMessageAdapter.Holder>(EntryDiff) {
    class Holder(val binding: MetaAdapterLogMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        MetaAdapterLogMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = getItem(position)
        holder.binding.message = current
        holder.binding.executePendingBindings()
        holder.binding.root.setOnLongClickListener {
            copy(current)
            true
        }
    }

    private object EntryDiff : DiffUtil.ItemCallback<RuntimeLogEntry>() {
        override fun areItemsTheSame(oldItem: RuntimeLogEntry, newItem: RuntimeLogEntry): Boolean = oldItem == newItem

        override fun areContentsTheSame(oldItem: RuntimeLogEntry, newItem: RuntimeLogEntry): Boolean = oldItem == newItem
    }
}
