package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.os.SystemClock
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
    private var sourceGroups = emptyList<ProxyGroupSnapshot>()
    private val displayPreferences = ProxyDisplayPreferences(context)
    private var displayOptions = displayPreferences.read()
    private var lastGroupName = displayPreferences.readLastGroupName()
    private val selectionAnimationTracker = ProxySelectionAnimationTracker()
    private val pagerAdapter = ProxyPagerAdapter()
    private val proxyMenu by lazy {
        PopupMenu(context, binding.proxyOverflowButton).apply {
            menuInflater.inflate(R.menu.meta_proxy_menu, menu)
            setOnMenuItemClickListener(::onProxyMenuItemClick)
        }
    }
    private var currentGroupIndex = 0
    private var renderingGroups = false
    private var interactionEnabled = true
    private var testing = false
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
                    updateDelayTestFabVisibility()
                    if (!renderingGroups) {
                        groups.getOrNull(position)?.name?.let { name ->
                            lastGroupName = name
                            displayPreferences.updateLastGroupName(name)
                        }
                    }
                }

                override fun onPageScrollStateChanged(state: Int) {
                    horizontalScrolling = state != ViewPager2.SCROLL_STATE_IDLE
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

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.proxy_mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    fun setGroups(snapshots: List<ProxyGroupSnapshot>) {
        val previousGroups = sourceGroups.associateBy(ProxyGroupSnapshot::name)
        val selectorGroupNames = snapshots.asSequence()
            .filter { snapshot ->
                val group = snapshot.proxyDetails[snapshot.name]
                group?.isGroup == true && group.type == SELECTOR_TYPE
            }
            .map(ProxyGroupSnapshot::name)
            .toSet()
        selectionAnimationTracker.retainGroups(selectorGroupNames)
        snapshots.forEach { snapshot ->
            val previous = previousGroups[snapshot.name] ?: return@forEach
            val selector = snapshot.proxyDetails[snapshot.name]
            if (selector?.isGroup != true || selector.type != SELECTOR_TYPE) return@forEach
            selectionAnimationTracker.updateSelection(
                snapshot.name,
                previous.selected,
                snapshot.selected,
                SystemClock.uptimeMillis(),
            )
        }
        sourceGroups = snapshots
        val lastGroupStillExists = snapshots.any { snapshot ->
            snapshot.name == lastGroupName && snapshot.proxyDetails[snapshot.name]?.isGroup == true
        }
        if (lastGroupName != null && !lastGroupStillExists) {
            lastGroupName = null
            displayPreferences.updateLastGroupName(null)
        }
        renderGroups(
            selectionAnimationExpirations = selectionAnimationTracker.snapshot(SystemClock.uptimeMillis()),
        )
    }

    fun applyProxySelection(groupName: String, proxyName: String): Boolean {
        val group = sourceGroups.firstOrNull { it.name == groupName } ?: return false
        if (group.proxyDetails[groupName]
                ?.let { it.isGroup && it.type == SELECTOR_TYPE } != true
        ) {
            return false
        }
        if (proxyName !in group.proxies) return false

        val selectionAnimationExpirations = selectionAnimationTracker.record(
            groupName,
            group.selected,
            proxyName,
            SystemClock.uptimeMillis(),
        )
        sourceGroups = sourceGroups.map { snapshot ->
            val linkedSelector = snapshot.proxyDetails[groupName]
                ?.takeIf { it.isGroup }
                ?: return@map snapshot
            snapshot.copy(
                selected = if (snapshot.name == groupName) proxyName else snapshot.selected,
                proxyDetails = snapshot.proxyDetails +
                    (groupName to linkedSelector.copy(now = proxyName)),
            )
        }
        renderGroups(selectionAnimationExpirations = selectionAnimationExpirations)
        return true
    }

    private fun renderGroups(
        selectionAnimationExpirations: Map<String, Map<String, Long>> = emptyMap(),
    ) {
        val selectedGroupName = lastGroupName ?: groups.getOrNull(currentGroupIndex)?.name
        groups.clear()
        groups += ProxyDisplayPolicy.groupPages(
            sourceGroups,
            unavailableType = context.getString(R.string.not_available),
            excludeNotSelectable = displayOptions.excludeNotSelectable,
            selectionAnimationExpirations = selectionAnimationExpirations,
        )
        bottomGroups.retainAll(groups.map { it.name }.toSet())
        updateModeSelection(sourceGroups.firstOrNull()?.mode)
        updateSelectableFilterSelection()

        if (groups.isEmpty()) {
            bottomStateRefreshPending = false
            showState(
                title = context.getString(R.string.no_proxy_groups),
                message = context.getString(R.string.proxy_empty_message),
                showProgress = false,
                showRetry = true,
            )
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
            content.visibility = View.VISIBLE
            stateContainer.visibility = View.GONE
            setDelayTestActionVisible(true)
            binding.viewPager.setCurrentItem(currentGroupIndex, false)
            setInteractionEnabled(!testing)
            updateDelayTestFabVisibility()
        } finally {
            renderingGroups = false
        }
    }

    fun setDelayTestProgress(current: Int, total: Int) {
        testing = true
        updateDelayTestFabVisibility()
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
        updateDelayTestFabVisibility()
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

    private fun updateSelectableFilter(item: android.view.MenuItem) {
        item.isChecked = !item.isChecked
        displayOptions = displayPreferences.updateExcludeNotSelectable(item.isChecked)
        renderGroups()
    }

    private fun onProxyMenuItemClick(item: MenuItem): Boolean {
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
        binding.fabDelayTest.hide()
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
        binding.proxyDelayTestIcon.isEnabled = enabled
        binding.proxyOverflowButton.isEnabled = enabled
        MENU_ACTION_IDS.forEach { id -> proxyMenu.menu.findItem(id)?.isEnabled = enabled }
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
            !testing
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

    private fun updateModeSelection(mode: ProxyMode?) {
        val selectedId = when (mode) {
            ProxyMode.RULE -> R.id.action_mode_rule
            ProxyMode.GLOBAL -> R.id.action_mode_global
            ProxyMode.DIRECT -> R.id.action_mode_direct
            null -> null
        }
        selectedId?.let { proxyMenu.menu.findItem(it)?.isChecked = true }
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

    private fun applyProxySystemBarInsets() {
        val defaultFabBottomMargin =
            (binding.fabDelayTest.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isRtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
            view.setPaddingRelative(
                if (isRtl) bars.right else bars.left,
                bars.top,
                if (isRtl) bars.left else bars.right,
                0,
            )
            pagerAdapter.setBottomInset(bars.bottom)
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
        private var bottomInset = 0

        fun setBottomInset(inset: Int) {
            if (bottomInset == inset) return
            bottomInset = inset

            val pagerRecycler = binding.viewPager.getChildAt(0) as? RecyclerView ?: return
            repeat(itemCount) { position ->
                (pagerRecycler.findViewHolderForAdapterPosition(position) as? PageHolder)
                    ?.recyclerView
                    ?.setPaddingRelative(0, 0, 0, bottomInset)
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
                setPaddingRelative(0, 0, 0, bottomInset)
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
            val groupName = group.name
            bottomGroups.remove(groupName)
            if (position == currentGroupIndex) bottomStateRefreshPending = true
            val proxyAdapter = pageAdapters.getOrPut(groupName) {
                ProxyAdapter { proxyName ->
                    if (interactionEnabled) request(Request.SelectProxy(groupName, proxyName))
                }
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

    private companion object {
        const val SELECTOR_TYPE = "Selector"

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
        val MENU_ACTION_IDS = MODE_MENU_IDS + LAYOUT_MENU_IDS + SORT_MENU_IDS +
            R.id.action_filter_not_selectable
    }
}
