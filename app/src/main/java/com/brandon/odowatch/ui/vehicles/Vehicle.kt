package com.brandon.odowatch.ui.vehicles

import kotlin.math.round

/**
 * Matches Firestore vehicle documents (same keys as iOS).
 * Path: users/{uid}/vehicles/{documentId}
 */
data class Vehicle(
    val id: String,
    val vehicleName: String,
    val image: String = "",
    val initialOdometer: Long = 0L,
    val estimatedMiles: Long = 0L,
    val nextOilChange: Long = 0L,
    val ownerId: String = "",
    val invitedUsers: List<String> = emptyList(),
    val pendingInvites: List<String> = emptyList(),
    val routeUUID: String? = null,
)

/** Odometer shown in the list: stored odometer plus estimated miles since last sync. */
fun Vehicle.listDisplayOdometer(): Long = initialOdometer + estimatedMiles

/** Odometer for list when audio-linked: initial + estimated + in-progress session miles. */
fun Vehicle.listDisplayOdometerWithSession(sessionMiles: Double): Double =
    initialOdometer + estimatedMiles + sessionMiles

/**
 * Miles until next service: [nextOilChange] minus rounded total odometer
 * (initial + estimated + [sessionMiles] when this vehicle is the live GPS session).
 * Matches iOS: `nextServiceMiles - Int(round(totalOdometer))`.
 */
fun Vehicle.milesUntilNextService(sessionMiles: Double = 0.0): Long {
    val totalOdometer = listDisplayOdometerWithSession(sessionMiles)
    return nextOilChange - round(totalOdometer).toLong()
}
