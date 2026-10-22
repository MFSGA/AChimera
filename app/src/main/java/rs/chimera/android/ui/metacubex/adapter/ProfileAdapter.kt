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

    fun refreshElapsed() {
        if (itemCount > 0 && currentList.any { it.lastUpdated != null }) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    inner class ViewHolder(
        private val binding: MetaAdapterProfileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(profile: ProfileSummary) {
            binding.profileName = profile.name
            binding.profileType = binding.root.context.getString(
                if (profile.isRemote) R.string.cmfa_profile_type_url else R.string.cmfa_profile_type_file,
            )
            binding.lastUpdatedText = profileElapsedTime(profile.lastUpdated)?.let { elapsed ->
                val context = binding.root.context
                when (elapsed.unit) {
                    ProfileElapsedTime.Unit.DAYS -> context.resources.getQuantityString(
                        R.plurals.profile_updated_days_ago,
                        elapsed.amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        elapsed.amount,
                    )
                    ProfileElapsedTime.Unit.HOURS -> context.resources.getQuantityString(
                        R.plurals.profile_updated_hours_ago,
                        elapsed.amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        elapsed.amount,
                    )
                    ProfileElapsedTime.Unit.MINUTES -> context.getString(
                        R.string.profile_updated_minutes_ago,
                        elapsed.amount,
                    )
                    ProfileElapsedTime.Unit.RECENTLY -> context.getString(R.string.profile_updated_recently)
                }
            }
            val subscription = profileSubscriptionPresentation(
                uploadBytes = profile.subscriptionUploadBytes,
                downloadBytes = profile.subscriptionDownloadBytes,
                totalBytes = profile.subscriptionTotalBytes,
                expiresAtMillis = profile.subscriptionExpiresAtMillis,
            )
            binding.subscriptionUsageText = subscription.usageUsedText
            binding.subscriptionUsageTotalText = subscription.usageTotalText
            binding.subscriptionExpireText = subscription.expiresText
            binding.subscriptionProgressVisible = subscription.progressPermille != null
            binding.subscriptionProgress = subscription.progressPermille ?: 0
            binding.profileSubscriptionUsageRow.visibility =
                if (subscription.usageText == null) View.GONE else View.VISIBLE
            binding.textProfileSubscriptionTotal.visibility =
                if (subscription.usageTotalText == null) View.GONE else View.VISIBLE
            binding.textProfileSubscriptionExpire.visibility =
                if (subscription.expiresText == null) View.GONE else View.VISIBLE
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
