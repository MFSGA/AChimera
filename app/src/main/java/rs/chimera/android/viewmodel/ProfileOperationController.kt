package rs.chimera.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class ProfileOperationController(
    private val gate: ProfileOperationGate = ProfileOperationGate(),
) {
    var state by mutableStateOf(ProfileOperationState())
        private set

    fun tryBegin(kind: ProfileOperationKind = ProfileOperationKind.MUTATION): Boolean {
        if (!gate.tryAcquire()) return false
        state = ProfileOperationState.active(kind)
        return true
    }

    fun end() {
        state = ProfileOperationState()
        gate.release()
    }
}
