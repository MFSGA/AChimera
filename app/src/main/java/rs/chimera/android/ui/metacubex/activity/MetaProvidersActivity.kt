package rs.chimera.android.ui.metacubex.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.ProviderSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.ui.metacubex.design.ProvidersDesign
import rs.chimera.android.ui.metacubex.design.ProviderDisplayPolicy
import rs.chimera.android.ui.metacubex.design.providerRuntimeStates

class MetaProvidersActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: ProvidersDesign

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        design = ProvidersDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    is ProvidersDesign.Request.Update -> updateProvider(request.index, request.provider)
                    ProvidersDesign.Request.NavigateBack -> finish()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    backend.runtimeStatus
                        .providerRuntimeStates()
                        .collectLatest { state ->
                            when (state) {
                                ServiceState.RUNNING -> loadProviders()
                                ServiceState.STOPPED, ServiceState.ERROR -> finish()
                                ServiceState.STARTING, ServiceState.STOPPING -> Unit
                            }
                        }
                }
                launch {
                    while (true) {
                        delay(60_000)
                        design.refreshElapsed()
                    }
                }
            }
        }
    }

    private suspend fun loadProviders() {
        try {
            if (backend.serviceState.value != ServiceState.RUNNING) {
                throw IllegalStateException(getString(R.string.stat_vpn_stopped))
            }
            val providers = withContext(Dispatchers.IO) {
                ProviderDisplayPolicy.sorted(backend.listProviders())
            }
            design.submitProviders(providers)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            design.showToast(
                getString(
                    R.string.meta_providers_load_failed,
                    error.message?.takeIf(String::isNotBlank)
                        ?: getString(R.string.profile_unknown_error),
                ),
            )
            finish()
        }
    }

    private fun updateProvider(index: Int, provider: ProviderSnapshot) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    backend.updateProvider(provider.type, provider.name)
                }
                design.notifyUpdated(index, succeeded = true)
            } catch (error: CancellationException) {
                design.notifyUpdated(index, succeeded = false)
                throw error
            } catch (error: Exception) {
                design.notifyUpdated(index, succeeded = false)
                design.showToast(
                    getString(
                        R.string.meta_provider_update_failed,
                        provider.name,
                        error.message?.takeIf(String::isNotBlank)
                            ?: getString(R.string.profile_unknown_error),
                    ),
                )
            }
        }
    }
}
