package com.brandon.odowatch.location

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.brandon.odowatch.audio.hasBluetoothAudioPermission
import com.brandon.odowatch.audio.rememberBluetoothAudioOutputs
import com.brandon.odowatch.audio.routeUuidMatchesActiveBluetooth
import com.brandon.odowatch.ui.vehicles.Vehicle

/**
 * Starts [DriveDistanceForegroundService] when a vehicle's [Vehicle.routeUUID] matches an active
 * Bluetooth audio output and location permission is granted; stops the service (which flushes
 * distance to Firestore) when the match ends or permissions are revoked.
 */
@Composable
fun DriveTrackingCoordinator(vehicles: List<Vehicle>) {
    val context = LocalContext.current

    var bluetoothGranted by remember {
        mutableStateOf(hasBluetoothAudioPermission(context))
    }
    var fineLocationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var coarseLocationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var backgroundLocationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> bluetoothGranted = granted }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        fineLocationGranted = granted
        coarseLocationGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> backgroundLocationGranted = granted }

    var askedBackgroundLocation by remember { mutableStateOf(false) }

    var postNotificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> postNotificationsGranted = granted }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothAudioPermission(context)) {
            bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (!fineLocationGranted && !coarseLocationGranted) {
            fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !postNotificationsGranted) {
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(fineLocationGranted, coarseLocationGranted, backgroundLocationGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (fineLocationGranted || coarseLocationGranted) &&
            !backgroundLocationGranted &&
            !askedBackgroundLocation
        ) {
            askedBackgroundLocation = true
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    val bluetoothOutputs = rememberBluetoothAudioOutputs(
        permissionGranted = bluetoothGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
    )

    val matchedVehicle = remember(vehicles, bluetoothOutputs) {
        vehicles.firstOrNull { v -> routeUuidMatchesActiveBluetooth(v.routeUUID, bluetoothOutputs) }
    }

    LaunchedEffect(
        matchedVehicle?.id,
        matchedVehicle?.routeUUID,
        fineLocationGranted,
        coarseLocationGranted,
        bluetoothGranted,
        postNotificationsGranted,
    ) {
        val app = context.applicationContext
        val locationOk = fineLocationGranted || coarseLocationGranted
        val bluetoothOk = bluetoothGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        val notifOk = postNotificationsGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        if (!locationOk || !bluetoothOk || !notifOk) {
            DriveDistanceForegroundService.stop(app)
            return@LaunchedEffect
        }
        val route = matchedVehicle?.routeUUID
        if (matchedVehicle != null && !route.isNullOrBlank()) {
            DriveDistanceForegroundService.start(app, matchedVehicle.id, route)
        } else {
            DriveDistanceForegroundService.stop(app)
        }
    }
}
