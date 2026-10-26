package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.R
import rs.chimera.android.backend.model.RuntimeLogArchive
import rs.chimera.android.ui.metacubex.adapter.RuntimeLogArchiveAdapter
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class LogsHomeDesign(context: Context) : Design<LogsHomeDesign.Request>(context) {
    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_logs_home,
        context.root,
        false,
    )
    private val archiveList = root.findViewById<RecyclerView>(R.id.log_archive_list)
    private val archiveAdapter = RuntimeLogArchiveAdapter { request(Request.OpenArchive(it)) }
    private var archiveRefreshGeneration = 0L

    init {
        applySystemBarInsets()
        archiveList.layoutManager = LinearLayoutManager(context)
        archiveList.adapter = archiveAdapter
        root.findViewById<View>(R.id.toolbar_navigation)
            .setOnClickListener { request(Request.NavigateBack) }
        root.findViewById<View>(R.id.start_logcat)
            .setOnClickListener { request(Request.OpenRuntimeLogs) }
        root.findViewById<View>(R.id.delete_all)
            .setOnClickListener { request(Request.DeleteAll) }
    }

    fun showArchives(archives: List<RuntimeLogArchive>, generation: Long) {
        if (generation != archiveRefreshGeneration) return
        archiveList.visibility = View.INVISIBLE
        archiveAdapter.submitList(archives.toList()) {
            if (generation == archiveRefreshGeneration) archiveList.visibility = View.VISIBLE
        }
    }

    fun showArchivesLoading(generation: Long) {
        archiveRefreshGeneration = generation
        archiveList.visibility = View.INVISIBLE
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isRtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val horizontalInsets = landscapeContentInsets(
                width = context.resources.displayMetrics.widthPixels,
                height = context.resources.displayMetrics.heightPixels,
                minWidth = context.resources.getDimensionPixelSize(
                    R.dimen.metacubex_surface_landscape_min_width,
                ),
                start = if (isRtl) bars.right else bars.left,
                end = if (isRtl) bars.left else bars.right,
            )
            view.setPaddingRelative(
                horizontalInsets.start,
                bars.top,
                horizontalInsets.end,
                bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    sealed class Request {
        data object NavigateBack : Request()
        data object OpenRuntimeLogs : Request()
        data class OpenArchive(val archive: RuntimeLogArchive) : Request()
        data object DeleteAll : Request()
    }
}
