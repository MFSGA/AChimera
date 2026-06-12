package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import rs.chimera.android.databinding.MetaDesignLogsBinding
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class LogsDesign(context: Context) : Design<LogsDesign.Request>(context) {

    val binding = MetaDesignLogsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.toolbar.setNavigationOnClickListener {
            request(Request.NavigateBack)
        }
    }

    fun setLogContent(log: String, lineCount: Int) {
        binding.logContent.text = log
        binding.logCount.text = "$lineCount lines"
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    sealed class Request {
        data object NavigateBack : Request()
        data object ClearLogs : Request()
    }
}
