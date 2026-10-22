package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class LogsHomeDesign(context: Context) : Design<LogsHomeDesign.Request>(context) {
    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_logs_home,
        context.root,
        false,
    )

    init {
        root.applySystemBarInsets()
        root.findViewById<View>(R.id.toolbar_navigation)
            .setOnClickListener { request(Request.NavigateBack) }
        root.findViewById<View>(R.id.start_logcat)
            .setOnClickListener { request(Request.OpenRuntimeLogs) }
        root.findViewById<View>(R.id.delete_all)
            .setOnClickListener { request(Request.DeleteAll) }
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    sealed class Request {
        data object NavigateBack : Request()
        data object OpenRuntimeLogs : Request()
        data object DeleteAll : Request()
    }
}
