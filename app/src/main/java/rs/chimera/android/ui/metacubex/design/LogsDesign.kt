package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.adapter.LogMessageAdapter
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class LogsDesign(context: Context) : Design<LogsDesign.Request>(context) {
    override val root: View = context.layoutInflater.inflate(R.layout.meta_design_logs, context.root, false)

    private val activityBar = root.findViewById<View>(R.id.log_activity_bar)
    private val recycler = root.findViewById<RecyclerView>(R.id.log_recycler_list)
    private val stateContainer = root.findViewById<View>(R.id.state_container)
    private val stateProgress = root.findViewById<ProgressBar>(R.id.state_progress)
    private val stateMessage = root.findViewById<TextView>(R.id.state_message)
    private val retryButton = root.findViewById<TextView>(R.id.retry_button)
    private val closeButton = root.findViewById<View>(R.id.log_close_button)
    private val title = root.findViewById<TextView>(R.id.log_title)
    private val adapter = LogMessageAdapter { requests.trySend(Request.CopyMessage(it.message)) }
    private var submittedMessages: List<RuntimeLogEntry>? = null
    private var userScrollRevision = 0L
    private var elevated = false
    private var elevationAnimator: ValueAnimator? = null
    private var archiveMode = false

    init {
        root.findViewById<View>(R.id.log_back_button).setOnClickListener { requests.trySend(Request.NavigateBack) }
        closeButton.setOnClickListener { requests.trySend(Request.Close) }
        retryButton.setOnClickListener { requests.trySend(Request.Retry) }
        recycler.isFocusable = false
        recycler.layoutManager = LinearLayoutManager(context).apply {
            reverseLayout = true
            stackFromEnd = true
        }
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) userScrollRevision++
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val next = recyclerView.computeVerticalScrollOffset() != 0
                if (next == elevated) return
                elevated = next
                elevationAnimator?.end()
                elevationAnimator = ValueAnimator.ofFloat(
                    activityBar.elevation,
                    if (next) context.resources.getDimension(R.dimen.metacubex_tab_layout_elevation) else 0f,
                ).apply {
                    addUpdateListener { activityBar.elevation = it.animatedValue as Float }
                    start()
                }
            }
        })
        val toolbarHeight = context.resources.getDimensionPixelSize(R.dimen.metacubex_toolbar_height)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val horizontalInsets = landscapeContentInsets(
                width = context.resources.displayMetrics.widthPixels,
                height = context.resources.displayMetrics.heightPixels,
                minWidth = context.resources.getDimensionPixelSize(
                    R.dimen.metacubex_surface_landscape_min_width,
                ),
                start = if (rtl) bars.right else bars.left,
                end = if (rtl) bars.left else bars.right,
            )
            view.setPaddingRelative(horizontalInsets.start, 0, horizontalInsets.end, 0)
            activityBar.setPadding(0, bars.top, 0, 0)
            recycler.setPaddingRelative(0, bars.top + toolbarHeight, 0, bars.bottom)
            stateContainer.setPadding(0, bars.top + toolbarHeight, 0, bars.bottom)
            insets
        }
        root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    fun setArchiveMode() {
        archiveMode = true
        closeButton.visibility = View.GONE
        title.setText(R.string.meta_logs_archive_title)
        root.findViewById<View>(R.id.log_back_button).contentDescription = context.getString(R.string.back)
        retryButton.setText(R.string.meta_logs_return_to_history)
        (recycler.layoutManager as? LinearLayoutManager)?.apply {
            reverseLayout = false
            stackFromEnd = false
        }
    }

    fun showLoading() {
        recycler.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = View.VISIBLE
        retryButton.visibility = View.GONE
        stateMessage.setText(R.string.logs_loading)
    }

    fun showContent(messages: List<RuntimeLogEntry>) {
        stateContainer.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        if (messages == submittedMessages) return
        val snapshot = messages.toList()
        submittedMessages = snapshot
        if (archiveMode) {
            adapter.submitList(snapshot) {
                if (snapshot.isNotEmpty()) recycler.scrollToPosition(0)
            }
            return
        }
        val followLatest = adapter.itemCount == 0 || recycler.computeVerticalScrollOffset() == 0
        val revision = userScrollRevision
        adapter.submitList(snapshot) {
            if (followLatest && revision == userScrollRevision && snapshot.isNotEmpty()) {
                recycler.scrollToPosition(snapshot.lastIndex)
            }
        }
    }

    fun showError(message: String) {
        recycler.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = View.GONE
        retryButton.visibility = View.VISIBLE
        stateMessage.text = message
    }

    sealed class Request {
        data class CopyMessage(val message: String) : Request()
        data object Close : Request()
        data object Retry : Request()
        data object NavigateBack : Request()
    }
}
