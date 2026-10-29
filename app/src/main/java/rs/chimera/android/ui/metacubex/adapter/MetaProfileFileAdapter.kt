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
    val isDirectory: Boolean = false,
    val relativePath: List<String> = emptyList(),
    val currentInBaseDirectory: Boolean = true,
    val action: Action = Action.NONE,
) {
    enum class Action {
        OPEN_CONFIGURATION,
        OPEN_DIRECTORY,
        NONE,
    }
}

internal class MetaProfileFileAdapter(
    private val onClick: (MetaProfileFileEntry) -> Unit,
    private val onMoreClick: (MetaProfileFileEntry) -> Unit,
) : ListAdapter<MetaProfileFileEntry, MetaProfileFileAdapter.Holder>(DiffCallback) {
    fun updateElapsed() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.meta_item_profile_file, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        holder.name.text = entry.name
        holder.size.text = formatProfileFileSize(entry.size)
        holder.size.visibility = if (entry.isDirectory) View.GONE else View.VISIBLE
        holder.icon.setImageResource(
            if (entry.isDirectory) R.drawable.ic_outline_folder else R.drawable.ic_outline_article,
        )
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
        holder.modified.visibility = if (elapsed == null || entry.isDirectory) View.GONE else View.VISIBLE
        holder.itemView.isClickable = entry.action != MetaProfileFileEntry.Action.NONE
        holder.itemView.isFocusable = entry.action != MetaProfileFileEntry.Action.NONE
        holder.itemView.setOnClickListener {
            if (entry.action != MetaProfileFileEntry.Action.NONE) onClick(entry)
        }
        holder.more.setOnClickListener { onMoreClick(entry) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.profile_file_name)
        val size: TextView = view.findViewById(R.id.profile_file_size)
        val modified: TextView = view.findViewById(R.id.profile_file_modified)
        val icon: android.widget.ImageView = view.findViewById(R.id.profile_file_icon)
        val more: View = view.findViewById(R.id.profile_file_more)
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
