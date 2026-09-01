package rs.chimera.android.ui.metacubex.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.R
import rs.chimera.android.backend.model.ConnectionSnapshot
import rs.chimera.android.databinding.MetaAdapterConnectionBinding
import rs.chimera.android.formatSize
import rs.chimera.android.ui.formatConnectionEndpoint

class ConnectionsAdapter(
    private val onClose: (String) -> Unit,
) : ListAdapter<ConnectionsAdapter.Item, ConnectionsAdapter.ViewHolder>(DiffCallback) {
    data class Item(
        val connection: ConnectionSnapshot,
        val closing: Boolean,
    )

    fun submitConnections(
        connections: List<ConnectionSnapshot>,
        closingIds: Set<String>,
    ) {
        submitList(connections.map { Item(it, it.id in closingIds) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MetaAdapterConnectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: MetaAdapterConnectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            val connection = item.connection
            val context = binding.root.context
            val destinationIp = connection.destinationIp.ifBlank { context.getString(R.string.not_available) }
            val destinationEndpoint = formatConnectionEndpoint(destinationIp, connection.destinationPort)
            val sourceEndpoint = formatConnectionEndpoint(connection.sourceIp, connection.sourcePort)

            binding.host.text = connection.host.ifBlank { destinationIp }
            binding.network.text = connection.network.ifBlank { "?" }.uppercase()
            binding.destination.text = destinationEndpoint
            binding.source.text = context.getString(R.string.connections_source, sourceEndpoint)
            binding.chain.apply {
                text = context.getString(
                    R.string.connections_chain,
                    connection.chains.reversed().joinToString(" -> "),
                )
                visibility = if (connection.chains.isEmpty()) View.GONE else View.VISIBLE
            }
            binding.rule.apply {
                text = context.getString(R.string.connections_rule, connection.rule.orEmpty())
                visibility = if (connection.rule.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            binding.traffic.text = context.getString(
                R.string.connections_traffic_pair,
                context.getString(R.string.connections_download, formatSize(connection.download)),
                context.getString(R.string.connections_upload, formatSize(connection.upload)),
            )
            binding.close.isEnabled = !item.closing
            binding.close.setOnClickListener { onClose(connection.id) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
            oldItem.connection.id == newItem.connection.id

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem == newItem
    }
}
