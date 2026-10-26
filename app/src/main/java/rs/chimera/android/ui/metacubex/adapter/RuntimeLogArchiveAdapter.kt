package rs.chimera.android.ui.metacubex.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import rs.chimera.android.R
import rs.chimera.android.backend.model.RuntimeLogArchive

internal class RuntimeLogArchiveAdapter(
    private val onClick: (RuntimeLogArchive) -> Unit,
) : ListAdapter<RuntimeLogArchive, RuntimeLogArchiveAdapter.Holder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.meta_adapter_log_archive, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val archive = getItem(position)
        holder.name.text = archive.fileName
        holder.date.text = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())
            .format(Date(archive.modifiedAtEpochMillis))
        holder.itemView.setOnClickListener { onClick(archive) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.log_archive_name)
        val date: TextView = view.findViewById(R.id.log_archive_date)
    }

    private object DiffCallback : DiffUtil.ItemCallback<RuntimeLogArchive>() {
        override fun areItemsTheSame(oldItem: RuntimeLogArchive, newItem: RuntimeLogArchive): Boolean =
            oldItem.index == newItem.index

        override fun areContentsTheSame(oldItem: RuntimeLogArchive, newItem: RuntimeLogArchive): Boolean =
            oldItem == newItem
    }

    private companion object {
        const val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"
    }
}
