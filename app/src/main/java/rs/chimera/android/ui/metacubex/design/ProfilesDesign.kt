package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.ui.metacubex.adapter.ProfileAdapter
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class ProfilesDesign(context: Context) : Design<ProfilesDesign.Request>(context) {
    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_profiles,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<View>(R.id.toolbar)
    private val toolbarTitle = root.findViewById<TextView>(R.id.toolbar_title)
    private val toolbarNavigation = root.findViewById<View>(R.id.toolbar_navigation)
    private val addAction = root.findViewById<View>(R.id.action_add)
    private val refreshAction = root.findViewById<View>(R.id.action_refresh)
    private val profileList = root.findViewById<RecyclerView>(R.id.profile_list)
    private val stateContainer = root.findViewById<View>(R.id.state_container)
    private val stateProgress = root.findViewById<ProgressBar>(R.id.state_progress)
    private val stateMessage = root.findViewById<TextView>(R.id.state_message)
    private val stateAction = root.findViewById<MaterialButton>(R.id.state_action)
    private var appBarElevated = false
    private var appBarElevationAnimator: ValueAnimator? = null

    private val adapter = ProfileAdapter(
        onItemClick = { profile -> request(Request.SelectProfile(profile.id)) },
        onMenuClick = { profile -> request(Request.ProfileMenu(profile.id)) },
    )

    init {
        root.applySystemBarInsets()
        toolbarTitle.text = context.getString(R.string.profile_screen)
        toolbarNavigation.setOnClickListener { request(Request.NavigateBack) }
        addAction.setOnClickListener { request(Request.AddProfile) }
        refreshAction.setOnClickListener { request(Request.RefreshProfile) }
        stateAction.setOnClickListener { request(Request.Retry) }
        profileList.layoutManager = LinearLayoutManager(context)
        profileList.adapter = adapter
        profileList.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateToolbarElevation()
                }
            },
        )
    }

    fun showLoading() {
        showOperation(context.getString(R.string.profile_loading))
    }

    fun showOperation(message: String) {
        profileList.visibility = View.GONE
        updateToolbarElevation()
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = View.VISIBLE
        stateAction.visibility = View.GONE
        stateMessage.text = message
    }

    fun submitList(profiles: List<ProfileSummary>) {
        adapter.submitList(profiles) {
            profileList.post { updateToolbarElevation() }
        }
        refreshAction.visibility = if (profiles.any { it.isRemote }) View.VISIBLE else View.GONE
        if (profiles.isEmpty()) {
            stateContainer.visibility = View.GONE
            profileList.visibility = View.VISIBLE
        } else {
            stateContainer.visibility = View.GONE
            profileList.visibility = View.VISIBLE
        }
    }

    fun refreshElapsed() {
        adapter.refreshElapsed()
    }

    fun showError(message: String) {
        profileList.visibility = View.GONE
        updateToolbarElevation()
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

    private fun updateToolbarElevation() {
        val elevated = profileList.visibility == View.VISIBLE && profileList.canScrollVertically(-1)
        if (appBarElevated == elevated) return

        appBarElevated = elevated
        appBarElevationAnimator?.cancel()
        val targetElevation = if (elevated) {
            5 * context.resources.displayMetrics.density
        } else {
            0f
        }
        appBarElevationAnimator = ValueAnimator.ofFloat(toolbar.elevation, targetElevation).apply {
            duration = 300L
            addUpdateListener { toolbar.elevation = it.animatedValue as Float }
            start()
        }
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
