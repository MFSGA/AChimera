package rs.chimera.android.ui.metacubex.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.ui.metacubex.design.ProxyDesign

class MetaProxyDesignActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: ProxyDesign

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        design = ProxyDesign(this)
        setContentView(design.root)

        lifecycleScope.launch(Dispatchers.Default) {
            for (request in design.requests) {
                handleRequest(request)
            }
        }

        observeProxyGroups()
    }

    private fun observeProxyGroups() {
        lifecycleScope.launch(Dispatchers.Default) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    try {
                        val groups = backend.listProxyGroups()
                        withContext(Dispatchers.Main) {
                            design.setGroups(groups)
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                    kotlinx.coroutines.delay(5000)
                }
            }
        }
    }

    private suspend fun handleRequest(request: ProxyDesign.Request) {
        when (request) {
            is ProxyDesign.Request.SelectProxy -> {
                // The backend selects by group name + proxy name,
                // but we don't have the current group context here.
                // For now, just toast.
                withContext(Dispatchers.Main) {
                    design.showToast("Selected ${request.proxyName}")
                }
            }
            is ProxyDesign.Request.NavigateBack -> {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }
}
