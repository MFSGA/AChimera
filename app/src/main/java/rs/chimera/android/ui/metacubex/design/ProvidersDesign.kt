package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProviderSnapshot
import rs.chimera.android.ui.metacubex.adapter.ProviderRow
import rs.chimera.android.ui.metacubex.adapter.ProxyProviderAdapter
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class ProvidersDesign(context: Context) : Design<ProvidersDesign.Request>(context) {
    sealed interface Request {
        data class Update(val index: Int, val provider: ProviderSnapshot) : Request
        data object NavigateBack : Request
    }

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_providers,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<View>(R.id.providers_toolbar)
    private val list = root.findViewById<RecyclerView>(R.id.providers_list)
    private val rows = mutableListOf<ProviderRow>()
    private var appBarElevated = false
    private var elevationAnimator: ValueAnimator? = null
    private val adapter = ProxyProviderAdapter(rows, ::requestUpdate)

    init {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val horizontalInsets = view.landscapeSystemBarInsets(bars)
            view.setPaddingRelative(
                horizontalInsets.start,
                0,
                horizontalInsets.end,
                0,
            )
            toolbar.setPadding(0, bars.top, 0, 0)
            list.setPadding(
                list.paddingLeft,
                bars.top + context.resources.getDimensionPixelSize(R.dimen.metacubex_toolbar_height),
                list.paddingRight,
                bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)

        root.findViewById<View>(R.id.providers_back).setOnClickListener {
            requests.trySend(Request.NavigateBack)
        }
        root.findViewById<View>(R.id.providers_update_all).setOnClickListener {
            requestUpdateAll()
        }
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateToolbarElevation()
                }
            },
        )
    }

    fun submitProviders(providers: List<ProviderSnapshot>) {
        rows.clear()
        rows.addAll(providers.map { ProviderRow(it) })
        adapter.notifyDataSetChanged()
        list.post { updateToolbarElevation() }
    }

    fun refreshElapsed() {
        adapter.refreshElapsed()
    }

    fun notifyUpdated(index: Int, succeeded: Boolean) {
        val row = rows.getOrNull(index) ?: return
        row.updating = false
        if (succeeded) row.updatedAtMillis = System.currentTimeMillis()
        adapter.notifyItemChanged(index)
    }

    private fun requestUpdate(index: Int, provider: ProviderSnapshot) {
        val row = rows.getOrNull(index) ?: return
        if (row.updating || provider.vehicleType.equals("inline", ignoreCase = true)) return
        row.updating = true
        adapter.notifyItemChanged(index)
        requests.trySend(Request.Update(index, provider))
    }

    private fun requestUpdateAll() {
        val pendingIndexes = rows.indices.filter { index ->
            val row = rows[index]
            !row.updating && !row.provider.vehicleType.equals("inline", ignoreCase = true)
        }
        if (pendingIndexes.isEmpty()) return

        pendingIndexes.forEach { index ->
            val row = rows[index]
            requestUpdate(index, row.provider)
        }
    }

    private fun updateToolbarElevation() {
        val elevated = list.canScrollVertically(-1)
        if (appBarElevated == elevated) return
        appBarElevated = elevated
        elevationAnimator?.end()
        val targetElevation = if (elevated) {
            5 * context.resources.displayMetrics.density
        } else {
            0f
        }
        elevationAnimator = ValueAnimator.ofFloat(toolbar.elevation, targetElevation).apply {
            duration = 300L
            addUpdateListener { toolbar.elevation = it.animatedValue as Float }
            start()
        }
    }
}
