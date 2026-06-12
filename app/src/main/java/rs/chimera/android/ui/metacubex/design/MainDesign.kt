package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import rs.chimera.android.R
import rs.chimera.android.databinding.MetaDesignMainBinding
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenLogs,
        OpenSettings,
        OpenHelp,
        OpenAbout,
    }

    val binding = MetaDesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.colorClashStarted = context.getColor(R.color.clash_started)
        binding.colorClashStopped = context.getColor(R.color.clash_stopped)

        binding.cardStatus.setOnClickListener { request(Request.ToggleStatus) }
        binding.cardProxy.setOnClickListener { request(Request.OpenProxy) }
        binding.cardProfiles.setOnClickListener { request(Request.OpenProfiles) }
        binding.cardLogs.setOnClickListener { request(Request.OpenLogs) }
        binding.cardSettings.setOnClickListener { request(Request.OpenSettings) }
    }

    fun setProfileName(name: String?) {
        binding.profileName = name
        binding.executePendingBindings()
    }

    fun setClashRunning(running: Boolean) {
        binding.clashRunning = running
        binding.executePendingBindings()
    }

    fun setForwarded(value: String) {
        binding.forwarded = value
        binding.executePendingBindings()
    }

    fun setMode(mode: String) {
        binding.mode = mode
        binding.executePendingBindings()
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
