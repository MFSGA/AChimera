package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayoutMediator
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.databinding.MetaDesignProxyBinding
import rs.chimera.android.ui.metacubex.adapter.ProxyAdapter
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class ProxyDesign(context: Context) : Design<ProxyDesign.Request>(context) {
    data class GroupPage(
        val name: String,
        val proxies: List<ProxyAdapter.Item>,
    )

    sealed class Request {
        data class SelectProxy(val groupName: String, val proxyName: String) : Request()
        data class DelayTest(val groupName: String, val proxyNames: List<String>) : Request()
        data object Refresh : Request()
        data object NavigateBack : Request()
    }

    val binding = MetaDesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val content = root.findViewById<LinearLayout>(R.id.proxy_content)
    private val stateContainer = root.findViewById<LinearLayout>(R.id.proxy_state)
    private val stateProgress = root.findViewById<ProgressBar>(R.id.proxy_state_progress)
    private val stateTitle = root.findViewById<TextView>(R.id.proxy_state_title)
    private val stateMessage = root.findViewById<TextView>(R.id.proxy_state_message)
    private val retryButton = root.findViewById<MaterialButton>(R.id.proxy_state_retry)
    private val groups = mutableListOf<GroupPage>()
    private val pagerAdapter = ProxyPagerAdapter()
    private var currentGroupIndex = 0
    private var interactionEnabled = true
    private var testing = false

    init {
        binding.toolbar.setNavigationOnClickListener {
            request(Request.NavigateBack)
        }
        binding.fabDelayTest.setOnClickListener {
            val group = groups.getOrNull(currentGroupIndex) ?: return@setOnClickListener
            request(Request.DelayTest(group.name, group.proxies.map { it.name }))
        }
        retryButton.setOnClickListener { request(Request.Refresh) }
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(
            binding.tabLayout,
            binding.viewPager,
        ) { tab, position ->
            tab.text = groups.getOrNull(position)?.name.orEmpty()
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentGroupIndex = position
                }
            },
        )
    }

    fun showLoading() {
        showState(
            title = context.getString(R.string.proxy_loading_title),
            message = context.getString(R.string.proxy_loading_message),
            showProgress = true,
            showRetry = false,
        )
    }

    fun showWaiting(title: String) {
        showState(
            title = title,
            message = context.getString(R.string.cmfa_service_wait),
            showProgress = true,
            showRetry = false,
        )
    }

    fun showNotRunning() {
        showState(
            title = context.getString(R.string.panel_not_running_title),
            message = context.getString(R.string.panel_not_running_message),
            showProgress = false,
            showRetry = false,
        )
    }

    fun showError(message: String, showRetry: Boolean = true) {
        showState(
            title = context.getString(
                if (showRetry) R.string.panel_error_title else R.string.cmfa_service_error,
            ),
            message = message,
            showProgress = false,
            showRetry = showRetry,
        )
    }

    fun setGroups(snapshots: List<ProxyGroupSnapshot>) {
        groups.clear()
        groups += snapshots.map { snapshot ->
            GroupPage(
                name = snapshot.name,
                proxies = snapshot.proxies.map { name ->
                    val detail = snapshot.proxyDetails[name]
                    ProxyAdapter.Item(
                        name = name,
                        type = detail?.type ?: context.getString(R.string.not_available),
                        delay = detail?.history?.lastOrNull()?.let { "${it.delay}ms" } ?: "-",
                        isSelected = name == snapshot.selected,
                    )
                },
            )
        }

        if (groups.isEmpty()) {
            showState(
                title = context.getString(R.string.no_proxy_groups),
                message = context.getString(R.string.proxy_empty_message),
                showProgress = false,
                showRetry = true,
            )
            return
        }

        currentGroupIndex = currentGroupIndex.coerceIn(0, groups.lastIndex)
        pagerAdapter.submitPages(groups.map { it.proxies })
        content.visibility = View.VISIBLE
        stateContainer.visibility = View.GONE
        binding.fabDelayTest.visibility = View.VISIBLE
        binding.viewPager.setCurrentItem(currentGroupIndex, false)
        setInteractionEnabled(!testing)
    }

    fun setDelayTestProgress(current: Int, total: Int) {
        testing = true
        setInteractionEnabled(false)
        binding.fabDelayTest.text = context.getString(
            R.string.proxy_delay_testing_progress,
            current,
            total,
        )
    }

    fun finishDelayTest() {
        testing = false
        binding.fabDelayTest.text = context.getString(R.string.proxy_delay_test)
        setInteractionEnabled(groups.isNotEmpty())
    }

    fun setSelecting(selecting: Boolean) {
        setInteractionEnabled(!selecting && !testing)
    }

    private fun showState(
        title: String,
        message: String,
        showProgress: Boolean,
        showRetry: Boolean,
    ) {
        content.visibility = View.GONE
        binding.fabDelayTest.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
        stateTitle.text = title
        stateMessage.text = message
        retryButton.visibility = if (showRetry) View.VISIBLE else View.GONE
    }

    private fun setInteractionEnabled(enabled: Boolean) {
        interactionEnabled = enabled
        binding.viewPager.isUserInputEnabled = enabled
        binding.fabDelayTest.isEnabled = enabled
        content.alpha = if (enabled) 1f else 0.65f
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    private inner class ProxyPagerAdapter : RecyclerView.Adapter<ProxyPagerAdapter.PageHolder>() {
        private var pages = listOf<List<ProxyAdapter.Item>>()

        fun submitPages(pages: List<List<ProxyAdapter.Item>>) {
            this.pages = pages
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val recycler = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                isNestedScrollingEnabled = false
            }
            return PageHolder(recycler)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val items = pages.getOrNull(position).orEmpty()
            val groupName = groups.getOrNull(position)?.name.orEmpty()
            holder.recyclerView.adapter = ProxyAdapter(null) { proxyName ->
                if (interactionEnabled) {
                    request(Request.SelectProxy(groupName, proxyName))
                }
            }.also { it.submitList(items) }
        }

        inner class PageHolder(val recyclerView: RecyclerView) :
            RecyclerView.ViewHolder(recyclerView)
    }
}
