package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.ui.metacubex.adapter.ProfileAdapter
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class ProfilesDesign(context: Context) : Design<ProfilesDesign.Request>(context) {
    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_profiles,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<MaterialToolbar>(R.id.toolbar)
    private val profileList = root.findViewById<RecyclerView>(R.id.profile_list)
    private val stateContainer = root.findViewById<View>(R.id.state_container)
    private val stateProgress = root.findViewById<ProgressBar>(R.id.state_progress)
    private val stateMessage = root.findViewById<TextView>(R.id.state_message)
    private val stateAction = root.findViewById<MaterialButton>(R.id.state_action)

    private val adapter = ProfileAdapter(
        onItemClick = { profile -> request(Request.SelectProfile(profile.id)) },
        onMenuClick = { profile -> request(Request.ProfileMenu(profile.id)) },
    )

    init {
        toolbar.setNavigationOnClickListener { request(Request.NavigateBack) }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add -> request(Request.AddProfile)
                R.id.action_refresh -> request(Request.RefreshProfile)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        stateAction.setOnClickListener { request(Request.Retry) }
        profileList.layoutManager = LinearLayoutManager(context)
        profileList.adapter = adapter
    }

    fun showLoading() {
        showOperation(context.getString(R.string.profile_loading))
    }

    fun showOperation(message: String) {
        profileList.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = View.VISIBLE
        stateAction.visibility = View.GONE
        stateMessage.text = message
    }

    fun submitList(profiles: List<ProfileSummary>) {
        adapter.submitList(profiles)
        if (profiles.isEmpty()) {
            profileList.visibility = View.GONE
            stateContainer.visibility = View.VISIBLE
            stateProgress.visibility = View.GONE
            stateAction.visibility = View.VISIBLE
            stateAction.setText(R.string.profile_import)
            stateAction.setOnClickListener { request(Request.AddProfile) }
            stateMessage.setText(R.string.profile_empty)
        } else {
            stateContainer.visibility = View.GONE
            profileList.visibility = View.VISIBLE
        }
    }

    fun showError(message: String) {
        profileList.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = View.GONE
        stateAction.visibility = View.VISIBLE
        stateAction.setText(R.string.retry)
        stateAction.setOnClickListener { request(Request.Retry) }
        stateMessage.text = message
    }

    fun showToast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    sealed class Request {
        data class SelectProfile(val profileId: String) : Request()
        data class ProfileMenu(val profileId: String) : Request()
        data object AddProfile : Request()
        data object RefreshProfile : Request()
        data object Retry : Request()
        data object NavigateBack : Request()
    }
}
