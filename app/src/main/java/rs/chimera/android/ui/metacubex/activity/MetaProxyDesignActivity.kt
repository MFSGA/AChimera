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
                runCatching {
                    backend.selectProxy(request.groupName, request.proxyName)
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        design.showToast(it.message ?: "Failed to select proxy")
                    }
                }
            }
            ProxyDesign.Request.DelayTest -> {
                val groups = backend.listProxyGroups()
                for (group in groups) {
                    for (proxyName in group.proxies) {
                        runCatching {
                            backend.testProxyDelay(proxyName)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    design.showToast("Delay test queued")
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
