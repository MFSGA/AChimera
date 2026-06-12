package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.databinding.MetaDesignProfilesBinding
import rs.chimera.android.ui.metacubex.adapter.ProfileAdapter
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class ProfilesDesign(context: Context) : Design<ProfilesDesign.Request>(context) {

    val binding = MetaDesignProfilesBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val adapter = ProfileAdapter(
        onItemClick = { profile ->
            requests.trySend(Request.SelectProfile(profile.id))
        },
        onMenuClick = { profile ->
            requests.trySend(Request.ProfileMenu(profile.id))
        },
    )

    init {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                rs.chimera.android.R.id.action_add -> {
                    request(Request.AddProfile)
                    true
                }
                rs.chimera.android.R.id.action_refresh -> {
                    request(Request.RefreshProfile)
                    true
                }
                else -> false
            }
        }
        binding.profileList.layoutManager = LinearLayoutManager(context)
        binding.profileList.adapter = adapter
    }

    fun submitList(profiles: List<ProfileSummary>) {
        adapter.submitList(profiles)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    sealed class Request {
        data class SelectProfile(val profileId: String) : Request()
        data class ProfileMenu(val profileId: String) : Request()
        data object AddProfile : Request()
        data object RefreshProfile : Request()
    }
}
