package rs.chimera.android.ui.metacubex.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.R
import java.util.Locale

internal data class MetaProfileFileEntry(
    val name: String,
    val size: Long,
    val modifiedAt: Long?,
)

internal class MetaProfileFileAdapter(
    private val onClick: () -> Unit,
) : ListAdapter<MetaProfileFileEntry, MetaProfileFileAdapter.Holder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.meta_item_profile_file, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        holder.name.text = entry.name
        holder.size.text = formatProfileFileSize(entry.size)
        val elapsed = profileElapsedTime(entry.modifiedAt)
        holder.modified.text = elapsed?.let { age ->
            val context = holder.itemView.context
            when (age.unit) {
                ProfileElapsedTime.Unit.DAYS -> context.getString(R.string.meta_provider_days_ago, age.amount)
                ProfileElapsedTime.Unit.HOURS -> context.getString(R.string.meta_provider_hours_ago, age.amount)
                ProfileElapsedTime.Unit.MINUTES -> context.getString(R.string.meta_provider_minutes_ago, age.amount)
                ProfileElapsedTime.Unit.RECENTLY -> context.getString(R.string.meta_provider_recently)
            }
        }
        holder.modified.visibility = if (elapsed == null) View.GONE else View.VISIBLE
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

internal fun formatProfileFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String = when {
    bytes > 1024L * 1024L * 1024L * 1024L * 1024L * 1024L ->
        String.format(locale, "%.2f EiB", bytes.toDouble() / 1024 / 1024 / 1024 / 1024 / 1024 / 1024)
    bytes > 1024L * 1024L * 1024L * 1024L * 1024L ->
        String.format(locale, "%.2f PiB", bytes.toDouble() / 1024 / 1024 / 1024 / 1024 / 1024)
    bytes > 1024L * 1024L * 1024L * 1024L ->
        String.format(locale, "%.2f TiB", bytes.toDouble() / 1024 / 1024 / 1024 / 1024)
    bytes > 1024L * 1024L * 1024L ->
        String.format(locale, "%.2f GiB", bytes.toDouble() / 1024 / 1024 / 1024)
    bytes > 1024L * 1024L ->
        String.format(locale, "%.2f MiB", bytes.toDouble() / 1024 / 1024)
    bytes > 1024L ->
        String.format(locale, "%.2f KiB", bytes.toDouble() / 1024)
    else -> "$bytes Bytes"
}
