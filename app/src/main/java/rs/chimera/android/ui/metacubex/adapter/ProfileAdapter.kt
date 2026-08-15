package rs.chimera.android.ui.metacubex.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.databinding.MetaAdapterProfileBinding
import rs.chimera.android.ui.ProfileAutoUpdateStatus
import rs.chimera.android.ui.format
import rs.chimera.android.ui.resolveProfileAutoUpdatePresentation

class ProfileAdapter(
    private val onItemClick: (ProfileSummary) -> Unit,
    private val onMenuClick: (ProfileSummary) -> Unit,
) : ListAdapter<ProfileSummary, ProfileAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MetaAdapterProfileBinding.inflate(
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
        private val binding: MetaAdapterProfileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(profile: ProfileSummary) {
            binding.profileName = profile.name
            binding.profileType = binding.root.context.getString(
                if (profile.isRemote) R.string.profile_type_remote else R.string.profile_type_local,
            )
            binding.isActive = profile.isActive
            val autoUpdatePresentation = resolveProfileAutoUpdatePresentation(
                autoUpdate = profile.autoUpdate,
                lastAttempt = profile.lastAutoUpdateAttempt,
                failureCount = profile.autoUpdateFailures,
                nextAttemptAt = profile.nextAutoUpdateAt,
                error = profile.lastAutoUpdateError,
            )
            binding.textProfileStatus.apply {
                text = autoUpdatePresentation?.format(context).orEmpty()
                visibility = if (autoUpdatePresentation == null) View.GONE else View.VISIBLE
                if (autoUpdatePresentation != null) {
                    setTextColor(
                        MaterialColors.getColor(
                            this,
                            if (autoUpdatePresentation.status == ProfileAutoUpdateStatus.RETRY) {
                                com.google.android.material.R.attr.colorError
                            } else {
                                com.google.android.material.R.attr.colorTertiary
                            },
                        ),
                    )
                }
            }
            binding.clicked = android.view.View.OnClickListener { onItemClick(profile) }
            binding.menu = android.view.View.OnClickListener { onMenuClick(profile) }
            binding.executePendingBindings()
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProfileSummary>() {
        override fun areItemsTheSame(oldItem: ProfileSummary, newItem: ProfileSummary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProfileSummary, newItem: ProfileSummary): Boolean {
            return oldItem == newItem
        }
    }
}
