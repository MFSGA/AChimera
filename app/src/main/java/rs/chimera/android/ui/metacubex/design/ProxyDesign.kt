package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.databinding.MetaDesignProxyBinding
import rs.chimera.android.ui.metacubex.adapter.ProxyAdapter
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class ProxyDesign(context: Context) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        data class SelectProxy(
            val groupName: String,
            val proxyName: String,
            val selectionProjected: Boolean = false,
        ) : Request()
        data class DelayTest(val groupName: String, val proxyNames: List<String>) : Request()
        data class SwitchMode(val mode: ProxyMode?) : Request()
        data object Refresh : Request()
        data object NavigateBack : Request()
    }

    val binding = MetaDesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val activityBar = root.findViewById<View>(R.id.proxy_activity_bar)
    private val content = root.findViewById<LinearLayout>(R.id.proxy_content)
    private val emptyView = root.findViewById<TextView>(R.id.proxy_empty_view)
    private val stateContainer = root.findViewById<LinearLayout>(R.id.proxy_state)
    private val stateProgress = root.findViewById<ProgressBar>(R.id.proxy_state_progress)
    private val stateTitle = root.findViewById<TextView>(R.id.proxy_state_title)
    private val stateMessage = root.findViewById<TextView>(R.id.proxy_state_message)
    private val retryButton = root.findViewById<MaterialButton>(R.id.proxy_state_retry)
    private val groups = mutableListOf<ProxyDisplayGroup>()
    private val proxySelectionState = ProxySelectionState()
    private val sourceGroups: List<ProxyGroupSnapshot>
        get() = proxySelectionState.displayedGroups
    internal var onProxySelectionQueueChanged: ((Boolean) -> Unit)? = null
    private val displayPreferences = ProxyDisplayPreferences(context)
    private var displayOptions = displayPreferences.read()
    private var lastGroupName = displayPreferences.readLastGroupName()
    private val pagerAdapter = ProxyPagerAdapter()
    private val proxyMenu by lazy {
        PopupMenu(context, binding.proxyOverflowButton).apply {
            menuInflater.inflate(R.menu.meta_proxy_menu, menu)
            setOnMenuItemClickListener(::onProxyMenuItemClick)
        }
    }
    private var currentGroupIndex = 0
    private var modeOverride: ProxyMode? = null
    private var renderingGroups = false
    private val delayTestProgressByGroup = mutableMapOf<String, DelayTestProgress>()
    private var horizontalScrolling = false
    private val bottomGroups = mutableSetOf<String>()
    private var bottomStateRefreshPending = false

    init {
        binding.proxyBackButton.setOnClickListener {
            request(Request.NavigateBack)
        }
        binding.proxyOverflowButton.setOnClickListener { proxyMenu.show() }
        binding.proxyDelayTestIcon.setOnClickListener { requestDelayTest() }
        setDelayTestActionVisible(false)
        binding.fabDelayTest.setOnClickListener { requestDelayTest() }
        retryButton.setOnClickListener { request(Request.Refresh) }
        binding.viewPager.adapter = pagerAdapter
        applyProxySystemBarInsets()

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
                    pagerAdapter.refreshPageBottomState(position)
                    updateDelayTestActionState()
                    if (!renderingGroups) {
                        groups.getOrNull(position)?.name?.let { name ->
                            lastGroupName = name
                            displayPreferences.updateLastGroupName(name)
                        }
                    }
                }

                override fun onPageScrollStateChanged(state: Int) {
                    horizontalScrolling = state != ViewPager2.SCROLL_STATE_IDLE
                    if (!horizontalScrolling) pagerAdapter.refreshPageBottomState(currentGroupIndex)
                    updateDelayTestFabVisibility()
                }
            },
        )
        updateLayoutSelection()
        updateSortSelection()
        updateSelectableFilterSelection()
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

    private fun showEmptyGroups() {
        content.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.fabDelayTest.hide()
        setDelayTestActionVisible(false)
        stateContainer.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.proxy_mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    fun setGroups(snapshots: List<ProxyGroupSnapshot>) {
        proxySelectionState.acceptBackendSnapshot(snapshots)
        val lastGroupStillExists = snapshots.any { snapshot ->
            snapshot.name == lastGroupName && snapshot.proxyDetails[snapshot.name]?.isGroup == true
        }
        if (lastGroupName != null && !lastGroupStillExists) {
            lastGroupName = null
            displayPreferences.updateLastGroupName(null)
        }
        renderGroups()
    }

    fun applyProxySelection(groupName: String, proxyName: String): Boolean {
        if (!proxySelectionState.projectSelection(groupName, proxyName)) return false
        renderGroups(
            rebindVisibleRowsImmediately = true,
        )
        return true
    }

    /** Advances rollback state without replacing a newer optimistic selection already on screen. */
    fun confirmProxySelection(groupName: String, proxyName: String): Boolean =
        proxySelectionState.confirmSelection(groupName, proxyName)

    fun restoreConfirmedProxyGroups() {
        proxySelectionState.restoreConfirmedSnapshot()
        renderGroups(rebindVisibleRowsImmediately = true)
    }

    private fun renderGroups(
        rebindVisibleRowsImmediately: Boolean = false,
    ) {
        val selectedGroupName = lastGroupName ?: groups.getOrNull(currentGroupIndex)?.name
        groups.clear()
        groups += ProxyDisplayPolicy.groupPages(
            sourceGroups,
            unavailableType = context.getString(R.string.not_available),
            excludeNotSelectable = displayOptions.excludeNotSelectable,
        )
        bottomGroups.retainAll(groups.map { it.name }.toSet())
        updateModeSelection(modeOverride)
        updateSelectableFilterSelection()

        if (groups.isEmpty()) {
            bottomStateRefreshPending = false
            showEmptyGroups()
            return
        }

        currentGroupIndex = groups.indexOfFirst { it.name == selectedGroupName }
            .takeIf { it >= 0 }
            ?: 0
        if (lastGroupName == null) {
            lastGroupName = groups.getOrNull(currentGroupIndex)?.name
            lastGroupName?.let(displayPreferences::updateLastGroupName)
        }
        bottomStateRefreshPending = true
        renderingGroups = true
        try {
            pagerAdapter.submitPages(sortedPages())
            if (rebindVisibleRowsImmediately) pagerAdapter.rebindVisibleRowsImmediately()
            emptyView.visibility = View.GONE
            binding.viewPager.visibility = View.VISIBLE
            content.visibility = View.VISIBLE
            stateContainer.visibility = View.GONE
            setDelayTestActionVisible(true)
            binding.viewPager.setCurrentItem(currentGroupIndex, false)
            updateDelayTestActionState()
        } finally {
            renderingGroups = false
        }
    }

    fun setDelayTestProgress(groupName: String, current: Int, total: Int) {
        delayTestProgressByGroup[groupName] = DelayTestProgress(current, total)
        updateDelayTestActionState()
    }

    fun finishDelayTest(groupName: String) {
        delayTestProgressByGroup.remove(groupName)
        updateDelayTestActionState()
    }

    fun setModeOverride(mode: ProxyMode?) {
        modeOverride = mode
        updateModeSelection(mode)
    }

    private fun requestMode(mode: ProxyMode?) {
        request(Request.SwitchMode(mode))
    }

    private fun requestDelayTest() {
        val group = groups.getOrNull(currentGroupIndex) ?: return
        if (group.name in delayTestProgressByGroup) return
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

    private fun updateSelectableFilter(item: android.view.MenuItem) {
        item.isChecked = !item.isChecked
        displayOptions = displayPreferences.updateExcludeNotSelectable(item.isChecked)
        renderGroups()
    }

    private fun onProxyMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_mode_dont_modify -> requestMode(null)
            R.id.action_mode_rule -> requestMode(ProxyMode.RULE)
            R.id.action_mode_global -> requestMode(ProxyMode.GLOBAL)
            R.id.action_mode_direct -> requestMode(ProxyMode.DIRECT)
            R.id.action_layout_single -> updateColumns(1)
            R.id.action_layout_double -> updateColumns(2)
            R.id.action_layout_multiple -> updateColumns(3)
            R.id.action_sort_default -> updateSortOrder(ProxySortOrder.DEFAULT)
            R.id.action_sort_name -> updateSortOrder(ProxySortOrder.NAME)
            R.id.action_sort_delay -> updateSortOrder(ProxySortOrder.DELAY)
            R.id.action_filter_not_selectable -> updateSelectableFilter(item)
            else -> return false
        }
        return true
    }

    private fun updateSelectableFilterSelection() {
        proxyMenu.menu.findItem(R.id.action_filter_not_selectable)?.isChecked =
            displayOptions.excludeNotSelectable
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
        binding.viewPager.visibility = View.GONE
        emptyView.visibility = View.GONE
        binding.fabDelayTest.hide()
        setDelayTestActionVisible(false)
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
        stateTitle.text = title
        stateMessage.text = message
        retryButton.visibility = if (showRetry) View.VISIBLE else View.GONE
    }

    private fun updateDelayTestControls() {
        val currentGroupName = groups.getOrNull(currentGroupIndex)?.name
        val currentGroupTesting = currentGroupName?.let(delayTestProgressByGroup::containsKey) == true
        val enabled = !currentGroupTesting && groups.isNotEmpty()
        binding.fabDelayTest.isEnabled = enabled
        binding.proxyDelayTestIcon.isEnabled = enabled
    }

    private fun setDelayTestActionVisible(visible: Boolean) {
        binding.proxyDelayTestAction.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateDelayTestFabVisibility() {
        val currentGroup = groups.getOrNull(currentGroupIndex)?.name
        val shouldShow = content.visibility == View.VISIBLE &&
            currentGroup != null &&
            !bottomStateRefreshPending &&
            currentGroup !in bottomGroups &&
            !horizontalScrolling &&
            currentGroup !in delayTestProgressByGroup
        if (shouldShow) {
            binding.fabDelayTest.show()
        } else {
            binding.fabDelayTest.hide()
        }
    }

    private fun updateDelayTestActionProgress(isTesting: Boolean) {
        binding.proxyDelayTestIcon.visibility =
            if (isTesting) View.GONE else View.VISIBLE
        binding.proxyDelayTestProgress.visibility =
            if (isTesting) View.VISIBLE else View.GONE
    }

    private fun updateDelayTestActionState() {
        val groupName = groups.getOrNull(currentGroupIndex)?.name
        val progress = groupName?.let(delayTestProgressByGroup::get)
        binding.fabDelayTest.contentDescription = if (progress == null) {
            context.getString(R.string.proxy_delay_test)
        } else {
            context.getString(
                R.string.proxy_delay_testing_progress,
                progress.current,
                progress.total,
            )
        }
        updateDelayTestActionProgress(progress != null)
        updateDelayTestControls()
        updateDelayTestFabVisibility()
    }

    private fun updateModeSelection(mode: ProxyMode?) {
        val selectedId = when (mode) {
            null -> R.id.action_mode_dont_modify
            ProxyMode.RULE -> R.id.action_mode_rule
            ProxyMode.GLOBAL -> R.id.action_mode_global
            ProxyMode.DIRECT -> R.id.action_mode_direct
        }
        proxyMenu.menu.findItem(selectedId)?.isChecked = true
    }

    private fun updateLayoutSelection() {
        val selectedId = when (displayOptions.columns) {
            1 -> R.id.action_layout_single
            2 -> R.id.action_layout_double
            else -> R.id.action_layout_multiple
        }
        proxyMenu.menu.findItem(selectedId)?.isChecked = true
    }

    private fun updateSortSelection() {
        val selectedId = when (displayOptions.sortOrder) {
            ProxySortOrder.DEFAULT -> R.id.action_sort_default
            ProxySortOrder.NAME -> R.id.action_sort_name
            ProxySortOrder.DELAY -> R.id.action_sort_delay
        }
        proxyMenu.menu.findItem(selectedId)?.isChecked = true
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    private fun requestProxySelection(groupName: String, proxyName: String) {
        val projected = applyProxySelection(groupName, proxyName)
        val request = Request.SelectProxy(groupName, proxyName, selectionProjected = projected)
        onProxySelectionQueueChanged?.invoke(true)
        if (requests.trySend(request).isFailure) {
            onProxySelectionQueueChanged?.invoke(false)
            if (projected) restoreConfirmedProxyGroups()
        }
    }

    private fun applyProxySystemBarInsets() {
        val defaultFabBottomMargin =
            (binding.fabDelayTest.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin
        val toolbarHeight = context.resources.getDimensionPixelSize(R.dimen.metacubex_toolbar_height)
        val tabHeight = context.resources.getDimensionPixelSize(R.dimen.metacubex_tab_layout_height)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isRtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
            view.setPaddingRelative(
                if (isRtl) bars.right else bars.left,
                0,
                if (isRtl) bars.left else bars.right,
                0,
            )
            activityBar.setPadding(0, bars.top, 0, 0)
            pagerAdapter.setTopInset(bars.top + toolbarHeight + tabHeight)
            pagerAdapter.setBottomInset(bars.bottom)
            (stateContainer.layoutParams as CoordinatorLayout.LayoutParams).apply {
                topMargin = bars.top + toolbarHeight
                stateContainer.layoutParams = this
            }
            (binding.fabDelayTest.layoutParams as CoordinatorLayout.LayoutParams).apply {
                bottomMargin = defaultFabBottomMargin + bars.bottom
                binding.fabDelayTest.layoutParams = this
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private inner class ProxyPagerAdapter : RecyclerView.Adapter<ProxyPagerAdapter.PageHolder>() {
        private var pages = listOf<List<ProxyDisplayEntry>>()
        private var groupNames = emptyList<String>()
        private val childrenPool = RecyclerView.RecycledViewPool()
        private val pageAdapters = mutableMapOf<String, ProxyAdapter>()
        private var topInset = 0
        private var bottomInset = 0

        fun setTopInset(inset: Int) {
            if (topInset == inset) return
            topInset = inset
            updateVisiblePageInsets()
        }

        fun setBottomInset(inset: Int) {
            if (bottomInset == inset) return
            bottomInset = inset

            updateVisiblePageInsets()
        }

        private fun updateVisiblePageInsets() {
            val pagerRecycler = binding.viewPager.getChildAt(0) as? RecyclerView ?: return
            repeat(itemCount) { position ->
                (pagerRecycler.findViewHolderForAdapterPosition(position) as? PageHolder)
                    ?.recyclerView
                    ?.setPaddingRelative(0, topInset, 0, bottomInset)
            }
        }

        fun submitPages(pages: List<List<ProxyDisplayEntry>>) {
            val oldCount = this.pages.size
            val newCount = pages.size
            this.pages = pages
            val newGroupNames = groups.map { it.name }

            if (groupNames == newGroupNames) {
                groups.indices.forEach(::updatePageAdapter)
                return
            }

            groupNames = newGroupNames
            pageAdapters.keys.retainAll(newGroupNames.toSet())

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
            if (pages.isNotEmpty()) groups.indices.forEach(::updatePageAdapter)
        }

        fun rebindVisibleRowsImmediately() {
            val pagerRecycler = binding.viewPager.getChildAt(0) as? RecyclerView ?: return
            repeat(itemCount) { position ->
                val pageHolder = pagerRecycler.findViewHolderForAdapterPosition(position) as? PageHolder
                    ?: return@repeat
                val group = groups.getOrNull(position) ?: return@repeat
                val pageAdapter = pageAdapters[group.name] ?: return@repeat
                if (pageHolder.recyclerView.adapter !== pageAdapter) return@repeat
                pageAdapter.rebindVisibleItems(
                    pageHolder.recyclerView,
                    pages.getOrNull(position).orEmpty(),
                )
            }
        }

        fun refreshPageBottomState(position: Int) {
            val group = groups.getOrNull(position) ?: return
            if (position == currentGroupIndex) bottomStateRefreshPending = true
            val adapter = pageAdapters[group.name] ?: return
            refreshBottomState(position, group.name, adapter)
        }

        private fun updatePageAdapter(position: Int) {
            val group = groups.getOrNull(position) ?: return
            val adapter = pageAdapters[group.name] ?: return
            if (position == currentGroupIndex) bottomStateRefreshPending = true
            adapter.columns = displayOptions.columns
            adapter.selectable = group.isSelectable
            adapter.submitList(pages.getOrNull(position).orEmpty()) {
                refreshBottomState(position, group.name, adapter)
            }
        }

        override fun getItemCount(): Int = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val host = VerticalScrollableHost(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            val recycler = RecyclerView(context).apply {
                layoutManager = gridLayoutManager()
                isNestedScrollingEnabled = false
                clipToPadding = false
                setRecycledViewPool(childrenPool)
                setPaddingRelative(0, topInset, 0, bottomInset)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            host.addView(recycler)
            val holder = PageHolder(host, recycler)
            recycler.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(
                        recyclerView: RecyclerView,
                        newState: Int,
                    ) {
                        if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                        val position = holder.bindingAdapterPosition
                        val groupName = groups.getOrNull(position)?.name ?: return
                        updateBottomState(groupName, recyclerView)
                    }
                },
            )
            return holder
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val items = pages.getOrNull(position).orEmpty()
            val group = groups.getOrNull(position) ?: return
            applyPageInsets(holder.recyclerView)
            val groupName = group.name
            bottomGroups.remove(groupName)
            if (position == currentGroupIndex) bottomStateRefreshPending = true
            val proxyAdapter = pageAdapters.getOrPut(groupName) {
                ProxyAdapter { proxyName -> requestProxySelection(groupName, proxyName) }
            }.apply {
                columns = displayOptions.columns
                selectable = group.isSelectable
                submitList(items) {
                    refreshBottomState(position, groupName, proxyAdapter = this)
                }
            }
            if (holder.recyclerView.adapter !== proxyAdapter) {
                holder.recyclerView.layoutManager = gridLayoutManager()
                holder.recyclerView.adapter = proxyAdapter
            }
            if (position == currentGroupIndex) updateDelayTestFabVisibility()
        }

        override fun onViewAttachedToWindow(holder: PageHolder) {
            super.onViewAttachedToWindow(holder)
            applyPageInsets(holder.recyclerView)
        }

        private fun applyPageInsets(recyclerView: RecyclerView) {
            recyclerView.setPaddingRelative(0, topInset, 0, bottomInset)
        }

        private fun refreshBottomState(
            position: Int,
            groupName: String,
            proxyAdapter: ProxyAdapter,
        ) {
            binding.viewPager.doOnPreDraw {
                val pagerRecyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return@doOnPreDraw
                val pageHolder = pagerRecyclerView.findViewHolderForAdapterPosition(position) as? PageHolder
                    ?: return@doOnPreDraw
                if (
                    groups.getOrNull(position)?.name != groupName ||
                    pageHolder.recyclerView.adapter !== proxyAdapter
                ) {
                    return@doOnPreDraw
                }
                if (position == currentGroupIndex) bottomStateRefreshPending = false
                updateBottomState(groupName, pageHolder.recyclerView)
            }
        }

        private fun updateBottomState(groupName: String, recyclerView: RecyclerView) {
            val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
            val itemCount = recyclerView.adapter?.itemCount ?: return
            val isAtScrolledBottom =
                itemCount > 0 &&
                    layoutManager.findFirstVisibleItemPosition() != 0 &&
                    layoutManager.findLastVisibleItemPosition() == itemCount - 1
            if (isAtScrolledBottom) {
                bottomGroups.add(groupName)
            } else {
                bottomGroups.remove(groupName)
            }
            if (groups.getOrNull(currentGroupIndex)?.name == groupName) {
                updateDelayTestFabVisibility()
            }
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

        inner class PageHolder(
            root: VerticalScrollableHost,
            val recyclerView: RecyclerView,
        ) : RecyclerView.ViewHolder(root)
    }

    private data class DelayTestProgress(
        val current: Int,
        val total: Int,
    )
}
