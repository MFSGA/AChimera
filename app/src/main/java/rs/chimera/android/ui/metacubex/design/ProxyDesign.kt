package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayoutMediator
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.databinding.MetaDesignProxyBinding
import rs.chimera.android.ui.metacubex.adapter.ProxyAdapter
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class ProxyDesign(context: Context) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        data class SelectProxy(val groupName: String, val proxyName: String) : Request()
        data class DelayTest(val groupName: String, val proxyNames: List<String>) : Request()
        data class SwitchMode(val mode: ProxyMode) : Request()
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
    private val groups = mutableListOf<ProxyDisplayGroup>()
    private val displayPreferences = ProxyDisplayPreferences(context)
    private var displayOptions = displayPreferences.read()
    private val pagerAdapter = ProxyPagerAdapter()
    private var currentGroupIndex = 0
    private var interactionEnabled = true
    private var testing = false

    init {
        root.applySystemBarInsets()
        binding.toolbar.title = context.getString(R.string.proxy_screen)
        binding.toolbar.setNavigationOnClickListener {
            request(Request.NavigateBack)
        }
        binding.toolbar.inflateMenu(R.menu.meta_proxy_menu)
        setDelayTestActionVisible(false)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_mode_rule -> requestMode(ProxyMode.RULE)
                R.id.action_mode_global -> requestMode(ProxyMode.GLOBAL)
                R.id.action_mode_direct -> requestMode(ProxyMode.DIRECT)
                R.id.action_layout_single -> updateColumns(1)
                R.id.action_layout_double -> updateColumns(2)
                R.id.action_layout_multiple -> updateColumns(3)
                R.id.action_sort_default -> updateSortOrder(ProxySortOrder.DEFAULT)
                R.id.action_sort_name -> updateSortOrder(ProxySortOrder.NAME)
                R.id.action_sort_delay -> updateSortOrder(ProxySortOrder.DELAY)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        binding.toolbar.menu.findItem(R.id.action_delay_test).actionView?.setOnClickListener {
            requestDelayTest()
        }
        binding.fabDelayTest.setOnClickListener { requestDelayTest() }
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
        updateLayoutSelection()
        updateSortSelection()
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
        groups += ProxyDisplayPolicy.groupPages(
            snapshots,
            unavailableType = context.getString(R.string.not_available),
        )
        updateModeSelection(snapshots.firstOrNull()?.mode)

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
        pagerAdapter.submitPages(sortedPages())
        content.visibility = View.VISIBLE
        stateContainer.visibility = View.GONE
        setDelayTestActionVisible(true)
        binding.fabDelayTest.visibility = View.VISIBLE
        binding.viewPager.setCurrentItem(currentGroupIndex, false)
        setInteractionEnabled(!testing)
    }

    fun setDelayTestProgress(current: Int, total: Int) {
        testing = true
        setInteractionEnabled(false)
        binding.fabDelayTest.contentDescription = context.getString(
            R.string.proxy_delay_testing_progress,
            current,
            total,
        )
        updateDelayTestActionProgress(true)
    }

    fun finishDelayTest() {
        testing = false
        binding.fabDelayTest.contentDescription = context.getString(R.string.proxy_delay_test)
        updateDelayTestActionProgress(false)
        setInteractionEnabled(groups.isNotEmpty())
    }

    fun setSelecting(selecting: Boolean) {
        setInteractionEnabled(!selecting && !testing)
    }

    fun setMode(mode: ProxyMode) {
        updateModeSelection(mode)
    }

    private fun requestMode(mode: ProxyMode) {
        if (interactionEnabled) request(Request.SwitchMode(mode))
    }

    private fun requestDelayTest() {
        if (!interactionEnabled) return
        val group = groups.getOrNull(currentGroupIndex) ?: return
        request(Request.DelayTest(group.name, group.proxies.map { it.name }))
    }

    private fun updateColumns(columns: Int) {
        displayOptions = displayPreferences.updateColumns(columns)
        updateLayoutSelection()
        pagerAdapter.refreshPages()
    }

    private fun updateSortOrder(sortOrder: ProxySortOrder) {
        displayOptions = displayPreferences.updateSortOrder(sortOrder)
        updateSortSelection()
        pagerAdapter.submitPages(sortedPages())
    }

    private fun sortedPages(): List<List<ProxyDisplayEntry>> =
        groups.map { page -> ProxyDisplayPolicy.sort(page.proxies, displayOptions.sortOrder) }

    private fun showState(
        title: String,
        message: String,
        showProgress: Boolean,
        showRetry: Boolean,
    ) {
        content.visibility = View.GONE
        binding.fabDelayTest.visibility = View.GONE
        setDelayTestActionVisible(false)
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
        MENU_ACTION_IDS.forEach { id -> binding.toolbar.menu.findItem(id)?.isEnabled = enabled }
        binding.toolbar.menu.findItem(R.id.action_delay_test)?.isEnabled = enabled
        content.alpha = if (enabled) 1f else 0.65f
    }

    private fun setDelayTestActionVisible(visible: Boolean) {
        binding.toolbar.menu.findItem(R.id.action_delay_test)?.isVisible = visible
    }

    private fun updateDelayTestActionProgress(isTesting: Boolean) {
        val action = binding.toolbar.menu.findItem(R.id.action_delay_test)?.actionView ?: return
        action.findViewById<View>(R.id.proxy_delay_test_icon).visibility =
            if (isTesting) View.GONE else View.VISIBLE
        action.findViewById<View>(R.id.proxy_delay_test_progress).visibility =
            if (isTesting) View.VISIBLE else View.GONE
    }

    private fun updateModeSelection(mode: ProxyMode?) {
        val selectedId = when (mode) {
            ProxyMode.RULE -> R.id.action_mode_rule
            ProxyMode.GLOBAL -> R.id.action_mode_global
            ProxyMode.DIRECT -> R.id.action_mode_direct
            null -> null
        }
        MODE_MENU_IDS.forEach { id ->
            binding.toolbar.menu.findItem(id)?.isChecked = id == selectedId
        }
    }

    private fun updateLayoutSelection() {
        val selectedId = when (displayOptions.columns) {
            1 -> R.id.action_layout_single
            2 -> R.id.action_layout_double
            else -> R.id.action_layout_multiple
        }
        LAYOUT_MENU_IDS.forEach { id ->
            binding.toolbar.menu.findItem(id)?.isChecked = id == selectedId
        }
    }

    private fun updateSortSelection() {
        val selectedId = when (displayOptions.sortOrder) {
            ProxySortOrder.DEFAULT -> R.id.action_sort_default
            ProxySortOrder.NAME -> R.id.action_sort_name
            ProxySortOrder.DELAY -> R.id.action_sort_delay
        }
        SORT_MENU_IDS.forEach { id ->
            binding.toolbar.menu.findItem(id)?.isChecked = id == selectedId
        }
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    private inner class ProxyPagerAdapter : RecyclerView.Adapter<ProxyPagerAdapter.PageHolder>() {
        private var pages = listOf<List<ProxyDisplayEntry>>()

        fun submitPages(pages: List<List<ProxyDisplayEntry>>) {
            val oldCount = this.pages.size
            val newCount = pages.size
            this.pages = pages

            val sharedCount = minOf(oldCount, newCount)
            if (sharedCount > 0) {
                notifyItemRangeChanged(0, sharedCount)
            }
            when {
                newCount > oldCount -> notifyItemRangeInserted(oldCount, newCount - oldCount)
                oldCount > newCount -> notifyItemRangeRemoved(newCount, oldCount - newCount)
            }
        }

        fun refreshPages() {
            if (pages.isNotEmpty()) notifyItemRangeChanged(0, pages.size)
        }

        override fun getItemCount(): Int = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val recycler = RecyclerView(context).apply {
                layoutManager = gridLayoutManager()
                isNestedScrollingEnabled = false
                clipToPadding = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            return PageHolder(recycler)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val items = pages.getOrNull(position).orEmpty()
            val groupName = groups.getOrNull(position)?.name.orEmpty()
            holder.recyclerView.layoutManager = gridLayoutManager()
            val proxyAdapter = ProxyAdapter { proxyName ->
                if (interactionEnabled) request(Request.SelectProxy(groupName, proxyName))
            }.apply {
                columns = displayOptions.columns
                selectable = groups.getOrNull(position)?.isSelectable == true
            }
            holder.recyclerView.adapter = proxyAdapter
            proxyAdapter.submitList(items)
        }

        private fun gridLayoutManager() = GridLayoutManager(
            context,
            ProxyDisplayOptions.GRID_SPAN_COUNT,
        ).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    ProxyDisplayPolicy.spanSize(displayOptions.columns)
            }
        }

        inner class PageHolder(val recyclerView: RecyclerView) :
            RecyclerView.ViewHolder(recyclerView)
    }

    private companion object {
        val MODE_MENU_IDS = listOf(
            R.id.action_mode_rule,
            R.id.action_mode_global,
            R.id.action_mode_direct,
        )
        val LAYOUT_MENU_IDS = listOf(
            R.id.action_layout_single,
            R.id.action_layout_double,
            R.id.action_layout_multiple,
        )
        val SORT_MENU_IDS = listOf(
            R.id.action_sort_default,
            R.id.action_sort_name,
            R.id.action_sort_delay,
        )
        val MENU_ACTION_IDS = MODE_MENU_IDS + LAYOUT_MENU_IDS + SORT_MENU_IDS
    }
}
