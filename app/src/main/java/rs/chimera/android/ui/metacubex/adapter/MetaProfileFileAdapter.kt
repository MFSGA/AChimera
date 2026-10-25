package rs.chimera.android.ui.metacubex.adapter

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.R

internal data class MetaProfileFileEntry(
    val name: String,
    val size: Long,
    val modifiedAt: Long,
)

internal class MetaProfileFileAdapter(
    private val onClick: () -> Unit,
) : ListAdapter<MetaProfileFileEntry, MetaProfileFileAdapter.Holder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.meta_item_profile_file, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        holder.name.text = entry.name
        holder.size.text = Formatter.formatShortFileSize(holder.itemView.context, entry.size)
        holder.modified.text = if (entry.modifiedAt > 0L) {
            DateUtils.getRelativeTimeSpanString(
                entry.modifiedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
        } else {
            ""
        }
        holder.modified.visibility = if (entry.modifiedAt > 0L) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick() }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.profile_file_name)
        val size: TextView = view.findViewById(R.id.profile_file_size)
        val modified: TextView = view.findViewById(R.id.profile_file_modified)
    }

    private object DiffCallback : DiffUtil.ItemCallback<MetaProfileFileEntry>() {
        override fun areItemsTheSame(oldItem: MetaProfileFileEntry, newItem: MetaProfileFileEntry): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: MetaProfileFileEntry, newItem: MetaProfileFileEntry): Boolean =
            oldItem == newItem
    }
}
