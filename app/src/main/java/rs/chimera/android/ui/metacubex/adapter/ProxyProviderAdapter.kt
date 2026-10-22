package rs.chimera.android.ui.metacubex.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProviderSnapshot
import java.util.concurrent.TimeUnit

class ProxyProviderAdapter(
    private val providers: MutableList<ProviderRow>,
    private val onUpdate: (Int, ProviderSnapshot) -> Unit,
) : RecyclerView.Adapter<ProxyProviderAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.meta_adapter_provider, parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position, providers[position])
    }

    override fun getItemCount(): Int = providers.size

    fun refreshElapsed() {
        if (providers.isNotEmpty()) notifyItemRangeChanged(0, providers.size)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.provider_name)
        private val type = itemView.findViewById<TextView>(R.id.provider_type)
        private val elapsed = itemView.findViewById<TextView>(R.id.provider_elapsed)
        private val divider = itemView.findViewById<View>(R.id.provider_divider)
        private val action = itemView.findViewById<View>(R.id.provider_update_container)
        private val updateIcon = itemView.findViewById<ImageView>(R.id.provider_update_icon)
        private val progress = itemView.findViewById<ProgressBar>(R.id.provider_update_progress)

        fun bind(position: Int, row: ProviderRow) {
            val context = itemView.context
            val provider = row.provider
            name.text = provider.name
            type.text = context.getString(
                R.string.meta_provider_type,
                providerTypeLabel(context, provider.type),
                vehicleTypeLabel(context, provider.vehicleType),
            )
            elapsed.text = row.updatedAtMillis?.let { updatedAt ->
                val age = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0L)
                val days = TimeUnit.MILLISECONDS.toDays(age)
                val hours = TimeUnit.MILLISECONDS.toHours(age)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(age)
                when {
                    days > 0 -> context.getString(R.string.meta_provider_days_ago, days)
                    hours > 0 -> context.getString(R.string.meta_provider_hours_ago, hours)
                    minutes > 0 -> context.getString(R.string.meta_provider_minutes_ago, minutes)
                    else -> context.getString(R.string.meta_provider_recently)
                }
            } ?: context.getString(R.string.meta_provider_time_unknown)

            val inline = provider.vehicleType.equals("inline", ignoreCase = true)
            elapsed.visibility = if (inline) View.GONE else View.VISIBLE
            divider.visibility = if (inline) View.GONE else View.VISIBLE
            action.visibility = if (inline) View.GONE else View.VISIBLE
            updateIcon.visibility = if (row.updating) View.GONE else View.VISIBLE
            progress.visibility = if (row.updating) View.VISIBLE else View.GONE
            action.setOnClickListener {
                if (!row.updating) onUpdate(position, provider)
            }
            action.isClickable = !row.updating
            action.isFocusable = !row.updating
        }
    }

    private fun providerTypeLabel(context: android.content.Context, type: String): String = when (type.lowercase()) {
        "proxy" -> context.getString(R.string.meta_provider_type_proxy)
        "rule" -> context.getString(R.string.meta_provider_type_rule)
        else -> type
    }

    private fun vehicleTypeLabel(context: android.content.Context, type: String): String = when (type.lowercase()) {
        "http" -> context.getString(R.string.meta_provider_vehicle_http)
        "file" -> context.getString(R.string.meta_provider_vehicle_file)
        "inline" -> context.getString(R.string.meta_provider_vehicle_inline)
        "compatible" -> context.getString(R.string.meta_provider_vehicle_compatible)
        else -> type
    }
}

data class ProviderRow(
    val provider: ProviderSnapshot,
    var updatedAtMillis: Long? = provider.updatedAtMillis,
    var updating: Boolean = false,
)
