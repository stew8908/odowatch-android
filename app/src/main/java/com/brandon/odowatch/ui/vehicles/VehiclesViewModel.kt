package com.brandon.odowatch.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class VehiclesViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private var userDocListener: ListenerRegistration? = null
    private val vehicleDocListeners = mutableListOf<ListenerRegistration>()

    /** Ordered UUIDs from `users/{uid}.ownedVehicles`. */
    private var ownedVehicleIds: List<String> = emptyList()

    /** Latest vehicle doc per id (only includes ids that have returned a snapshot). */
    private val vehicleById = mutableMapOf<String, Vehicle>()
    private val aggregateLock = Any()

    init {
        loadVehicles()
    }

    private fun loadVehicles() {
        clearVehicleDocListeners()
        userDocListener?.remove()
        userDocListener = null

        val userId = auth.currentUser?.uid ?: return

        userDocListener = db.collection("users").document(userId)
            .addSnapshotListener { userSnap, error ->
                if (error != null) {
                    synchronized(aggregateLock) {
                        ownedVehicleIds = emptyList()
                        vehicleById.clear()
                        _vehicles.value = emptyList()
                    }
                    return@addSnapshotListener
                }
                if (userSnap == null || !userSnap.exists()) {
                    synchronized(aggregateLock) {
                        ownedVehicleIds = emptyList()
                        vehicleById.clear()
                        _vehicles.value = emptyList()
                    }
                    return@addSnapshotListener
                }

                val rawIds = userSnap.readStringList("ownedVehicles").distinct()
                synchronized(aggregateLock) {
                    ownedVehicleIds = rawIds
                    vehicleById.keys.retainAll(rawIds.toSet())
                    emitOrderedVehiclesLocked()
                }

                attachVehicleListeners(rawIds)
            }
    }

    private fun attachVehicleListeners(ids: List<String>) {
        clearVehicleDocListeners()
        if (ids.isEmpty()) return

        ids.forEach { vehicleId ->
            val reg = db.collection("vehicles").document(vehicleId)
                .addSnapshotListener { snap, vErr ->
                    synchronized(aggregateLock) {
                        if (vErr != null) {
                            vehicleById.remove(vehicleId)
                        } else if (snap != null && snap.exists()) {
                            vehicleById[vehicleId] = snap.toVehicle(snap.id)
                        } else {
                            vehicleById.remove(vehicleId)
                        }
                        emitOrderedVehiclesLocked()
                    }
                }
            vehicleDocListeners.add(reg)
        }
    }

    private fun emitOrderedVehiclesLocked() {
        _vehicles.value = ownedVehicleIds.mapNotNull { vehicleById[it] }
    }

    private fun clearVehicleDocListeners() {
        vehicleDocListeners.forEach { it.remove() }
        vehicleDocListeners.clear()
    }

    fun saveVehicle(
        updated: Vehicle,
        isNew: Boolean = false,
        onFinished: ((Throwable?) -> Unit)? = null,
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onFinished?.invoke(IllegalStateException("Not signed in"))
            return
        }
        viewModelScope.launch {
            try {
                val withDefaults = if (isNew && updated.image.isBlank()) {
                    updated.copy(image = "default.png")
                } else {
                    updated
                }
                val withOwner = withDefaults.copy(ownerId = userId)
                val data = withOwner.toFirestoreMap()
                db.collection("vehicles").document(updated.id)
                    .set(data, SetOptions.merge())
                    .await()
                if (isNew) {
                    // Merge so the user doc is created if missing (update alone would fail).
                    db.collection("users").document(userId)
                        .set(
                            hashMapOf("ownedVehicles" to FieldValue.arrayUnion(updated.id)),
                            SetOptions.merge(),
                        )
                        .await()
                }
                onFinished?.invoke(null)
            } catch (e: Exception) {
                onFinished?.invoke(e)
            }
        }
    }

    override fun onCleared() {
        userDocListener?.remove()
        clearVehicleDocListeners()
        super.onCleared()
    }
}

private fun DocumentSnapshot.readStringList(field: String): List<String> {
    val raw = get(field) ?: return emptyList()
    if (raw !is List<*>) return emptyList()
    return raw.mapNotNull { entry ->
        when (entry) {
            is String -> entry
            else -> entry?.toString()
        }
    }
}

private fun DocumentSnapshot.toVehicle(id: String): Vehicle {
    val vehicleName = getString("vehicleName") ?: getString("name") ?: ""
    val image = getString("image") ?: ""
    val initialOdometer = readLongPreferring(
        preferredKey = "initialOdometer",
        fallbackKey = "odometerMiles",
    )
    val nextOilChange = readLongPreferring(
        preferredKey = "nextOilChange",
        fallbackKey = "milesTillNextService",
    )
    val estimatedMiles = getLongCompat("estimatedMiles")
    val ownerId = getString("ownerId") ?: ""
    val invitedUsers = readStringList("invitedUsers")
    val pendingInvites = readStringList("pendingInvites")
    val routeUUID = getString("routeUUID")
    return Vehicle(
        id = id,
        vehicleName = vehicleName,
        image = image,
        initialOdometer = initialOdometer,
        estimatedMiles = estimatedMiles,
        nextOilChange = nextOilChange,
        ownerId = ownerId,
        invitedUsers = invitedUsers,
        pendingInvites = pendingInvites,
        routeUUID = routeUUID,
    )
}

private fun DocumentSnapshot.readLongPreferring(preferredKey: String, fallbackKey: String): Long {
    if (contains(preferredKey)) return getLongCompat(preferredKey)
    if (contains(fallbackKey)) return getLongCompat(fallbackKey)
    return 0L
}

private fun DocumentSnapshot.getLongCompat(field: String): Long {
    val v = get(field) ?: return 0L
    return when (v) {
        is Long -> v
        is Int -> v.toLong()
        is Double -> v.toLong()
        else -> 0L
    }
}

private fun Vehicle.toFirestoreMap(): HashMap<String, Any> {
    val map = hashMapOf<String, Any>(
        "vehicleName" to vehicleName,
        "image" to image,
        "initialOdometer" to initialOdometer,
        "estimatedMiles" to estimatedMiles,
        "nextOilChange" to nextOilChange,
        "ownerId" to ownerId,
        "invitedUsers" to invitedUsers,
        "pendingInvites" to pendingInvites,
    )
    routeUUID?.let { map["routeUUID"] = it }
    return map
}
