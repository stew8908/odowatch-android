package com.brandon.odowatch.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    private val _profileUsername = MutableStateFlow<String?>(null)
    val profileUsername = _profileUsername.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            val uid = firebaseAuth.currentUser?.uid
            if (uid == null) {
                _profileUsername.value = null
            } else {
                viewModelScope.launch {
                    runCatching {
                        val snap = db.collection("users").document(uid).get().await()
                        if (firebaseAuth.currentUser?.uid == uid) {
                            _profileUsername.value = snap.getString("username")
                        }
                    }
                }
            }
        }
    }

    fun signIn(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            onResult(false, "Email and password cannot be empty")
            return
        }
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    fun signUp(
        email: String,
        pass: String,
        username: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        val trimmedEmail = email.trim()
        val trimmedUsername = username.trim()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            onResult(false, "Email and password cannot be empty")
            return
        }
        if (trimmedUsername.isBlank()) {
            onResult(false, "Username cannot be empty")
            return
        }
        auth.createUserWithEmailAndPassword(trimmedEmail, pass)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    onResult(false, task.exception?.message)
                    return@addOnCompleteListener
                }
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    onResult(false, "Not signed in")
                    return@addOnCompleteListener
                }
                viewModelScope.launch {
                    try {
                        db.collection("users").document(uid)
                            .set(
                                newUserDocument(trimmedEmail, trimmedUsername),
                                SetOptions.merge(),
                            )
                            .await()
                        onResult(true, null)
                    } catch (e: Exception) {
                        onResult(false, e.message)
                    }
                }
            }
    }

    fun signOut() {
        auth.signOut()
    }

    /**
     * Deletes owned vehicle docs, the user profile doc, then the Firebase Auth user.
     * May fail with a "requires recent login" error until the user signs in again.
     */
    fun deleteAccount(onResult: (Boolean, String?) -> Unit) {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            onResult(false, "Not signed in")
            return
        }
        val uid = firebaseUser.uid
        viewModelScope.launch {
            try {
                val userSnap = db.collection("users").document(uid).get().await()
                val ownedIds = userSnap.readOwnedVehicleIds()
                ownedIds.chunked(450).forEach { chunk ->
                    val vehicleBatch = db.batch()
                    chunk.forEach { vehicleId ->
                        vehicleBatch.delete(db.collection("vehicles").document(vehicleId))
                    }
                    vehicleBatch.commit().await()
                }
                val userBatch = db.batch()
                userBatch.delete(db.collection("users").document(uid))
                userBatch.commit().await()
                firebaseUser.delete().await()
                // Auth listener can fire later; sync immediately so UI shows sign-in.
                auth.signOut()
                _profileUsername.value = null
                _currentUser.value = auth.currentUser
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }
}

private fun DocumentSnapshot.readOwnedVehicleIds(): List<String> {
    val raw = get("ownedVehicles") ?: return emptyList()
    if (raw !is List<*>) return emptyList()
    return raw.mapNotNull { entry ->
        when (entry) {
            is String -> entry
            else -> entry?.toString()
        }
    }
}

/**
 * Default `users/{uid}` shape used elsewhere in the app (e.g. [com.brandon.odowatch.ui.vehicles.VehiclesViewModel]).
 * New accounts get the same fields as existing users, with empty collections where appropriate.
 */
private fun newUserDocument(email: String, username: String): HashMap<String, Any> =
    hashMapOf(
        "email" to email,
        "username" to username,
        "ownedVehicles" to emptyList<String>(),
    )
