package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.adapter.MetaProfileFileAdapter
import rs.chimera.android.ui.metacubex.adapter.MetaProfileFileEntry
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

internal class MetaProfileFilesDesign(
    context: Context,
    fileSize: Long,
    modifiedAt: Long,
) : Design<MetaProfileFilesDesign.Request>(context) {
    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_profile_files,
        context.root,
        false,
    )

    private val activityBar = root.findViewById<View>(R.id.profile_files_activity_bar)
    private val recycler = root.findViewById<RecyclerView>(R.id.profile_files_recycler)
    private val adapter = MetaProfileFileAdapter {
        requests.trySend(Request.OpenConfiguration)
    }

    init {
        root.findViewById<View>(R.id.profile_files_back).setOnClickListener {
            requests.trySend(Request.NavigateBack)
        }
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        adapter.submitList(
            listOf(
                MetaProfileFileEntry(
                    name = context.getString(R.string.profile_files_configuration),
                    size = fileSize,
                    modifiedAt = modifiedAt,
                ),
            ),
        )
        applySystemBarInsets()
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
            view.setPaddingRelative(if (rtl) bars.right else bars.left, 0, if (rtl) bars.left else bars.right, 0)
            activityBar.setPadding(0, bars.top, 0, 0)
            recycler.setPaddingRelative(
                0,
                bars.top + context.resources.getDimensionPixelSize(R.dimen.metacubex_toolbar_height),
                0,
                bars.bottom,
            )
            insets
        }
        root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    sealed class Request {
        data object OpenConfiguration : Request()
        data object NavigateBack : Request()
    }
}
