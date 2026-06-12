package rs.chimera.android.ui.metacubex.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import rs.chimera.android.databinding.MetaAdapterProxyBinding

class ProxyAdapter(
    private val selectedProxy: String?,
    private val onItemClick: (String) -> Unit,
) : ListAdapter<ProxyAdapter.Item, ProxyAdapter.ViewHolder>(DiffCallback) {

    data class Item(
        val name: String,
        val type: String,
        val delay: String,
        val isSelected: Boolean,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MetaAdapterProxyBinding.inflate(
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
        private val binding: MetaAdapterProxyBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.proxyName = item.name
            binding.proxyType = item.type
            binding.delay = item.delay
            binding.isSelected = item.isSelected
            binding.clicked = android.view.View.OnClickListener { onItemClick(item.name) }
            binding.executePendingBindings()
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem == newItem
        }
    }
}
