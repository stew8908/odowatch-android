package com.brandon.odowatch.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live session miles for the vehicle currently tracked by [DriveDistanceForegroundService].
 * Used by the vehicle list to show odometer + estimated + in-progress GPS distance.
 */
object DriveTrackingSessionState {
    data class ActiveSession(
        val vehicleId: String,
        val sessionMiles: Double,
    )

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    fun publish(vehicleId: String, sessionMiles: Double) {
        _activeSession.value = ActiveSession(vehicleId = vehicleId, sessionMiles = sessionMiles)
    }

    fun clear() {
        _activeSession.value = null
    }
}
