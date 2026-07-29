package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class LogsDesign(context: Context) : Design<LogsDesign.Request>(context) {
    override val root: View = context.layoutInflater.inflate(R.layout.meta_design_logs, context.root, false)

    private val logScroll = root.findViewById<ScrollView>(R.id.log_scroll)
    private val logContent = root.findViewById<TextView>(R.id.log_content)
    private val logStatus = root.findViewById<TextView>(R.id.log_status)
    private val stateContainer = root.findViewById<View>(R.id.state_container)
    private val stateProgress = root.findViewById<ProgressBar>(R.id.state_progress)
    private val stateMessage = root.findViewById<TextView>(R.id.state_message)
    private val retryButton = root.findViewById<MaterialButton>(R.id.retry_button)
    private val copyButton = root.findViewById<MaterialButton>(R.id.copy_button)
    private val clearButton = root.findViewById<MaterialButton>(R.id.clear_button)
    private val pauseSwitch = root.findViewById<SwitchMaterial>(R.id.pause_switch)
    private val autoScrollSwitch = root.findViewById<SwitchMaterial>(R.id.auto_scroll_switch)

    init {
        root.findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { request(Request.NavigateBack) }
        retryButton.setOnClickListener { request(Request.Retry) }
        copyButton.setOnClickListener { request(Request.CopyLogs) }
        clearButton.setOnClickListener { request(Request.ClearLogs) }
        pauseSwitch.setOnCheckedChangeListener { _, checked -> request(Request.SetPaused(checked)) }
        autoScrollSwitch.setOnCheckedChangeListener { _, checked -> request(Request.SetAutoScroll(checked)) }
    }

    fun showLoading() {
        logScroll.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = View.VISIBLE
        retryButton.visibility = View.GONE
        stateMessage.setText(R.string.logs_loading)
    }

    fun showContent(log: String, lineCount: Int, paused: Boolean, autoScroll: Boolean) {
        stateContainer.visibility = View.GONE
        logScroll.visibility = View.VISIBLE
        logContent.text = log.ifBlank { context.getString(R.string.home_logs_empty) }
        logStatus.text = context.getString(
            if (paused) R.string.logs_status_paused else R.string.logs_status_live,
            lineCount,
        )
        pauseSwitch.isChecked = paused
        autoScrollSwitch.isChecked = autoScroll
        copyButton.isEnabled = log.isNotBlank()
        clearButton.isEnabled = log.isNotBlank()
        if (autoScroll) logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    fun showError(message: String) {
        logScroll.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = View.GONE
        retryButton.visibility = View.VISIBLE
        stateMessage.text = message
    }

    fun showToast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    sealed class Request {
        data class SetPaused(val paused: Boolean) : Request()
        data class SetAutoScroll(val enabled: Boolean) : Request()
        data object CopyLogs : Request()
        data object ClearLogs : Request()
        data object Retry : Request()
        data object NavigateBack : Request()
    }
}
