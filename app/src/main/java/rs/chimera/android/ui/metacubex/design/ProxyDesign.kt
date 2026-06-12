package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
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

    val binding = MetaDesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val groups = mutableListOf<GroupPage>()
    private val pagerAdapter = ProxyPagerAdapter()
    private var currentGroupIndex = 0

    init {
        binding.toolbar.setNavigationOnClickListener {
            request(Request.NavigateBack)
        }
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(
            binding.tabLayout,
            binding.viewPager,
        ) { tab, position ->
            tab.text = groups.getOrNull(position)?.name ?: ""
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentGroupIndex = position
            }
        })
    }

    fun setGroups(snapshots: List<ProxyGroupSnapshot>) {
        groups.clear()
        for (snapshot in snapshots) {
            val items = snapshot.proxies.map { name ->
                val detail = snapshot.proxyDetails[name]
                ProxyAdapter.Item(
                    name = name,
                    type = detail?.type ?: "Unknown",
                    delay = detail?.history?.lastOrNull()?.let { "${it.delay}ms" } ?: "-",
                    isSelected = name == snapshot.selected,
                )
            }
            groups.add(GroupPage(snapshot.name, items))
        }
        pagerAdapter.submitPages(groups.map { it.proxies })
        // Force TabLayout refresh
        for (i in 0 until binding.tabLayout.tabCount.coerceAtMost(groups.size)) {
            binding.tabLayout.getTabAt(i)?.text = groups[i].name
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    sealed class Request {
        data class SelectProxy(val proxyName: String) : Request()
        data object NavigateBack : Request()
    }

    private inner class ProxyPagerAdapter : RecyclerView.Adapter<ProxyPagerAdapter.PageHolder>() {
        private var pages = listOf<List<ProxyAdapter.Item>>()

        fun submitPages(pages: List<List<ProxyAdapter.Item>>) {
            this.pages = pages
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val ctx = this@ProxyDesign.context
            val recycler = RecyclerView(ctx).apply {
                layoutManager = LinearLayoutManager(ctx)
                isNestedScrollingEnabled = false
            }
            return PageHolder(recycler)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val items = pages.getOrNull(position) ?: emptyList()
            val selected = items.firstOrNull { it.isSelected }?.name
            val adapter = ProxyAdapter(selected) { proxyName ->
                request(Request.SelectProxy(proxyName))
            }
            adapter.submitList(items)
            holder.recyclerView.adapter = adapter
        }

        inner class PageHolder(val recyclerView: RecyclerView) :
            RecyclerView.ViewHolder(recyclerView)
    }
}
