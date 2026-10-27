package com.telecomprofi.kickrhiit.ride

import com.telecomprofi.kickrhiit.data.db.RideEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place the UI reads live ride state from, and sends commands to.
 *
 * The engine lives in a foreground service so a ride survives the screen going
 * off; this is the process-wide bridge to it.
 */
object RideHub {

    private val _state = MutableStateFlow(RideUiState())
    val state: StateFlow<RideUiState> = _state.asStateFlow()

    private val _lastRide = MutableStateFlow<RideEntity?>(null)
    val lastRide: StateFlow<RideEntity?> = _lastRide.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    @Volatile
    private var engine: RideEngine? = null

    val isActive: Boolean get() = engine?.isActive == true

    internal fun attach(engine: RideEngine) {
        this.engine = engine
        _lastRide.value = null
    }

    internal fun publish(state: RideUiState) { _state.value = state }

    internal fun publishRide(ride: RideEntity) { _lastRide.value = ride }

    fun setNote(message: String?) { _note.value = message }

    fun consumeRide() { _lastRide.value = null }

    fun togglePause() = engine?.togglePause()
    fun skipStep() = engine?.skipStep()
    fun stop() = engine?.stop()
    fun nextTrack() = engine?.nextTrack()
    fun toggleMusic() = engine?.toggleMusic()

    fun reset() {
        engine = null
        _state.value = RideUiState()
    }
}
