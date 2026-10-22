package rs.chimera.android.ui.metacubex.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.databinding.MetaAdapterAppBinding
import rs.chimera.android.ui.metacubex.design.AccessControlDesign

class AccessControlAdapter(
    private val onToggle: (AccessControlDesign.InstalledApp, Boolean) -> Unit,
) : RecyclerView.Adapter<AccessControlAdapter.ViewHolder>() {
    private var apps: List<AccessControlDesign.InstalledApp> = emptyList()
    private var selected: Set<String> = emptySet()
    private var selectionEnabled = true

    fun submitState(
        apps: List<AccessControlDesign.InstalledApp>,
        selected: Set<String>,
        selectionEnabled: Boolean,
    ) {
        this.apps = apps
        this.selected = selected
        this.selectionEnabled = selectionEnabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            MetaAdapterAppBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val isSelected = app.packageName in selected
        holder.binding.app = app
        holder.binding.selected = isSelected
        holder.binding.root.setOnClickListener {
            if (selectionEnabled) onToggle(app, !isSelected)
        }
        holder.binding.root.isClickable = selectionEnabled
        holder.binding.root.isFocusable = selectionEnabled
        holder.binding.appSelected.isEnabled = selectionEnabled
        holder.binding.executePendingBindings()
    }

    override fun getItemCount(): Int = apps.size

    class ViewHolder(
        val binding: MetaAdapterAppBinding,
    ) : RecyclerView.ViewHolder(binding.root)
}
