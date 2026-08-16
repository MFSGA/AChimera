package rs.chimera.android.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

internal enum class UnderlyingNetworkTransport(val priority: Int) {
    ETHERNET(4),
    WIFI(3),
    CELLULAR(2),
    BLUETOOTH(1),
    OTHER(0),
}

internal data class UnderlyingNetworkCandidate<T>(
    val value: T,
    val isActive: Boolean,
    val isValidated: Boolean,
    val isMetered: Boolean,
    val transport: UnderlyingNetworkTransport,
)

internal object UnderlyingNetworkPolicy {
    fun <T> order(candidates: Collection<UnderlyingNetworkCandidate<T>>): List<T> =
        candidates
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<UnderlyingNetworkCandidate<T>>> {
                    score(it.value)
                }.thenBy { it.index },
            ).map { it.value.value }

    private fun <T> score(candidate: UnderlyingNetworkCandidate<T>): Int {
        var score = candidate.transport.priority
        if (candidate.isValidated) {
            score += VALIDATED_SCORE
            if (!candidate.isMetered) score += UNMETERED_SCORE
            if (candidate.isActive) score += ACTIVE_VALIDATED_SCORE
        } else if (candidate.isActive) {
            score += ACTIVE_UNVALIDATED_SCORE
        }
        return score
    }

    private const val VALIDATED_SCORE = 300
    private const val UNMETERED_SCORE = 50
    private const val ACTIVE_VALIDATED_SCORE = 200
    private const val ACTIVE_UNVALIDATED_SCORE = 100
}

internal data class UnderlyingNetworkTransition(
    val previousPrimary: Long?,
    val currentPrimary: Long?,
)

internal class NetworkResetGeneration {
    private val latest = AtomicLong()

    fun next(): Long = latest.incrementAndGet()

    fun isLatest(generation: Long): Boolean = latest.get() == generation

    fun invalidate() {
        latest.incrementAndGet()
    }
}

internal class UnderlyingNetworkChangeTracker {
    private var initialized = false
    private var primaryHandle: Long? = null

    fun update(currentPrimary: Long?): UnderlyingNetworkTransition? {
        if (!initialized) {
            initialized = true
            primaryHandle = currentPrimary
            return null
        }
        if (primaryHandle == currentPrimary) return null

        return UnderlyingNetworkTransition(
            previousPrimary = primaryHandle,
            currentPrimary = currentPrimary,
        ).also {
            primaryHandle = currentPrimary
        }
    }

    fun reset() {
        initialized = false
        primaryHandle = null
    }
}

internal class UnderlyingNetworkCoordinator(
    private val connectivityManager: ConnectivityManager,
    private val applyNetworks: (Array<Network>?) -> Boolean,
    private val onPrimaryNetworkChanged: () -> Unit,
) {
    private val capabilitiesByNetwork = LinkedHashMap<Network, NetworkCapabilities>()
    private val changeTracker = UnderlyingNetworkChangeTracker()
    private var started = false
    private var lastAppliedHandles: List<Long>? = null

    private val request =
        NetworkRequest
            .Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetwork(network, connectivityManager.getNetworkCapabilities(network), "available")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                updateNetwork(network, networkCapabilities, "capabilities")
            }

            override fun onLost(network: Network) {
                synchronized(this@UnderlyingNetworkCoordinator) {
                    if (!started) return
                    capabilitiesByNetwork.remove(network)
                    applyCurrentNetworks("lost")
                }
            }
        }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        runCatching {
            connectivityManager.registerNetworkCallback(request, callback)
        }.onFailure { error ->
            started = false
            capabilitiesByNetwork.clear()
            Log.w(TAG, "Failed to start underlying network tracking", error)
        }
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        capabilitiesByNetwork.clear()
        lastAppliedHandles = null
        changeTracker.reset()
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            .onFailure { error ->
                Log.w(TAG, "Failed to stop underlying network tracking", error)
            }
    }

    @Synchronized
    private fun updateNetwork(
        network: Network,
        capabilities: NetworkCapabilities?,
        reason: String,
    ) {
        if (!started) return
        if (capabilities?.isEligibleUnderlyingNetwork() == true) {
            capabilitiesByNetwork[network] = capabilities
        } else {
            capabilitiesByNetwork.remove(network)
        }
        applyCurrentNetworks(reason)
    }

    private fun applyCurrentNetworks(reason: String) {
        val activeNetwork = connectivityManager.activeNetwork
        val orderedNetworks =
            UnderlyingNetworkPolicy.order(
                capabilitiesByNetwork.map { (network, capabilities) ->
                    UnderlyingNetworkCandidate(
                        value = network,
                        isActive = network == activeNetwork,
                        isValidated = capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                        ),
                        isMetered = !capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                        ),
                        transport = capabilities.underlyingTransport(),
                    )
                },
            )
        val handles = orderedNetworks.map(Network::getNetworkHandle)
        if (handles == lastAppliedHandles) return

        val applied =
            runCatching { applyNetworks(orderedNetworks.toTypedArray()) }
                .onFailure { error ->
                    Log.w(TAG, "Failed to apply underlying networks ($reason)", error)
                }.getOrDefault(false)
        if (!applied) return

        lastAppliedHandles = handles
        if (changeTracker.update(handles.firstOrNull()) != null) {
            onPrimaryNetworkChanged()
        }
    }

    private fun NetworkCapabilities.isEligibleUnderlyingNetwork(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

    private fun NetworkCapabilities.underlyingTransport(): UnderlyingNetworkTransport =
        when {
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> UnderlyingNetworkTransport.ETHERNET
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> UnderlyingNetworkTransport.WIFI
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> UnderlyingNetworkTransport.CELLULAR
            hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> UnderlyingNetworkTransport.BLUETOOTH
            else -> UnderlyingNetworkTransport.OTHER
        }

    private companion object {
        const val TAG = "ChimeraNetwork"
    }
}
