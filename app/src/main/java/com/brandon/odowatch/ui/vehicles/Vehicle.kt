package com.brandon.odowatch.ui.vehicles

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

/** Miles until next service: `nextOilChange - initialOdometer + estimatedMiles` (negative if overdue). */
fun Vehicle.milesUntilNextServiceAdjusted(): Long =
    nextOilChange - initialOdometer + estimatedMiles
