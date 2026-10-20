package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import rs.chimera.android.R
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.databinding.MetaDesignMainBinding
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProviders,
        OpenProfiles,
        OpenConnections,
        OpenLogs,
        OpenSettings,
        OpenWatfaq,
        OpenHelp,
        OpenAbout,
    }

    val binding = MetaDesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val cardHelp: View = root.findViewById(R.id.card_help)
    private val cardAbout: View = root.findViewById(R.id.card_about)
    private val cardProviders: View = root.findViewById(R.id.card_providers)
    private val statusIcon: ImageView = root.findViewById(R.id.icon_status)
    private val statusTitle: TextView = root.findViewById(R.id.text_status_title)
    private val statusSubtitle: TextView = root.findViewById(R.id.text_status_subtitle)
    private var serviceState = ServiceState.STOPPED
    private var serviceError: String? = null
    private var hasProviders = false
    private var forwarded = "0 B"
    private var statusOperationInProgress = false
    private val statusStartedColor = resolveThemeColor(R.attr.colorPrimary)
    private val statusStoppedColor = resolveThemeColor(R.attr.colorClashStopped)
    private val statusErrorColor = resolveThemeColor(R.attr.colorError)

    init {
        root.applySystemBarInsets()
        binding.cardStatus.setOnClickListener { request(Request.ToggleStatus) }
        binding.cardProxy.setOnClickListener { request(Request.OpenProxy) }
        cardProviders.setOnClickListener { request(Request.OpenProviders) }
        binding.cardProfiles.setOnClickListener { request(Request.OpenProfiles) }
        binding.cardConnections.setOnClickListener { request(Request.OpenConnections) }
        binding.cardLogs.setOnClickListener { request(Request.OpenLogs) }
        binding.cardSettings.setOnClickListener { request(Request.OpenSettings) }
        cardHelp.setOnClickListener { request(Request.OpenHelp) }
        cardAbout.setOnClickListener { request(Request.OpenAbout) }
        binding.buttonOpenWatfaq.setOnClickListener { request(Request.OpenWatfaq) }

        setProfileName(null)
        setMode(context.getString(R.string.not_available))
        setServiceState(ServiceState.STOPPED)
    }

    fun setProfileName(name: String?) {
        binding.profileName = name
            ?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.cmfa_profile_activated, it) }
            ?: context.getString(R.string.cmfa_profile_not_selected)
        binding.executePendingBindings()
    }

    fun setServiceState(state: ServiceState) {
        serviceState = state
        renderServiceState()
    }

    fun setHasProviders(hasProviders: Boolean) {
        this.hasProviders = hasProviders
        renderServiceState()
    }

    fun setServiceError(message: String?) {
        serviceError = message
        if (serviceState == ServiceState.ERROR) renderServiceState()
    }

    fun setStatusOperationInProgress(inProgress: Boolean, message: String? = null) {
        statusOperationInProgress = inProgress
        if (inProgress && message != null) statusSubtitle.text = message else renderServiceState()
        binding.cardStatus.isEnabled =
            !inProgress &&
                serviceState !in setOf(
                    ServiceState.STARTING,
                    ServiceState.STOPPING,
                )
        binding.cardStatus.alpha = if (binding.cardStatus.isEnabled) 1f else 0.72f
    }

    private fun renderServiceState() {
        val presentation = when (serviceState) {
            ServiceState.STOPPED -> StatusPresentation(
                title = context.getString(R.string.stat_vpn_stopped),
                subtitle = context.getString(R.string.cmfa_status_hint_start),
                color = statusStoppedColor,
                icon = R.drawable.ic_outline_not_interested,
                interactive = true,
                proxyVisible = false,
            )
            ServiceState.STARTING -> StatusPresentation(
                title = context.getString(R.string.cmfa_service_starting),
                subtitle = context.getString(R.string.cmfa_service_wait),
                color = statusStartedColor,
                icon = R.drawable.ic_outline_not_interested,
                interactive = false,
                proxyVisible = false,
            )
            ServiceState.RUNNING -> StatusPresentation(
                title = context.getString(R.string.stat_vpn_running),
                subtitle = context.getString(R.string.cmfa_traffic_forwarded, forwarded),
                color = statusStartedColor,
                icon = R.drawable.ic_outline_check_circle,
                interactive = true,
                proxyVisible = true,
            )
            ServiceState.STOPPING -> StatusPresentation(
                title = context.getString(R.string.cmfa_service_stopping),
                subtitle = context.getString(R.string.cmfa_service_wait),
                color = statusStoppedColor,
                icon = R.drawable.ic_outline_not_interested,
                interactive = false,
                proxyVisible = false,
            )
            ServiceState.ERROR -> StatusPresentation(
                title = context.getString(R.string.cmfa_service_error),
                subtitle = serviceError?.let {
                    context.getString(R.string.cmfa_service_error_detail, it)
                } ?: context.getString(R.string.cmfa_service_retry),
                color = statusErrorColor,
                icon = R.drawable.ic_outline_not_interested,
                interactive = true,
                proxyVisible = false,
            )
        }

        statusTitle.text = presentation.title
        statusSubtitle.text = presentation.subtitle
        statusIcon.setImageResource(presentation.icon)
        binding.cardStatus.setCardBackgroundColor(presentation.color)
        val interactive = presentation.interactive && !statusOperationInProgress
        binding.cardStatus.isEnabled = interactive
        binding.cardStatus.alpha = if (interactive) 1f else 0.72f
        binding.cardProxy.visibility = if (presentation.proxyVisible) View.VISIBLE else View.GONE
        cardProviders.visibility = if (serviceState == ServiceState.RUNNING && hasProviders) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    fun setForwarded(value: String) {
        forwarded = value
        if (serviceState == ServiceState.RUNNING) {
            statusSubtitle.text = context.getString(R.string.cmfa_traffic_forwarded, value)
        }
    }

    fun setMode(mode: String) {
        binding.mode = mode
        binding.executePendingBindings()
    }

    fun setMemory(inUse: String?, limit: String?) {
        binding.textMemory.text = if (inUse == null || limit == null) {
            context.getString(R.string.not_available)
        } else {
            context.getString(R.string.stat_memory_limit, limit).let { limitText ->
                "$inUse · $limitText"
            }
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    private fun resolveThemeColor(attribute: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attribute, value, true)
        return if (value.resourceId != 0) {
            context.getColor(value.resourceId)
        } else {
            value.data
        }
    }

    private data class StatusPresentation(
        val title: String,
        val subtitle: String,
        val color: Int,
        val icon: Int,
        val interactive: Boolean,
        val proxyVisible: Boolean,
    )
}
