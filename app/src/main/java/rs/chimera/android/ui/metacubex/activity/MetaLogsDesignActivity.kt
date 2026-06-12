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
import rs.chimera.android.ui.metacubex.design.LogsDesign

class MetaLogsDesignActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: LogsDesign

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        design = LogsDesign(this)
        setContentView(design.root)

        lifecycleScope.launch(Dispatchers.Default) {
            for (request in design.requests) {
                handleRequest(request)
            }
        }

        observeLogs()
    }

    private fun observeLogs() {
        lifecycleScope.launch(Dispatchers.Default) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    try {
                        val log = backend.readRuntimeLogs(500)
                        val lines = log.count { it == '\n' }
                        withContext(Dispatchers.Main) {
                            design.setLogContent(log, lines)
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                    kotlinx.coroutines.delay(2000)
                }
            }
        }
    }

    private suspend fun handleRequest(request: LogsDesign.Request) {
        when (request) {
            LogsDesign.Request.NavigateBack -> {
                withContext(Dispatchers.Main) { finish() }
            }
            LogsDesign.Request.ClearLogs -> {
                runCatching { backend.clearRuntimeLogs() }
            }
        }
    }
}
