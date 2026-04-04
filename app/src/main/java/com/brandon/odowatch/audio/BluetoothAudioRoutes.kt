package com.brandon.odowatch.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

data class BluetoothAudioOutput(
    val productName: String,
    /** MAC-style address when available (API 28+); may be null if restricted or unknown. */
    val address: String?,
)

fun hasBluetoothAudioPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

/** Normalizes a stored route id or MAC for comparison (trim + uppercase). */
fun normalizeRouteIdentifier(raw: String?): String? {
    val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return s.uppercase(Locale.US)
}

/**
 * True when [routeUUID] matches the MAC/address of any active Bluetooth audio output device.
 */
fun routeUuidMatchesActiveBluetooth(
    routeUUID: String?,
    activeBluetoothOutputs: List<BluetoothAudioOutput>,
): Boolean {
    val target = normalizeRouteIdentifier(routeUUID) ?: return false
    return activeBluetoothOutputs.any { output ->
        val addr = normalizeRouteIdentifier(output.address) ?: return@any false
        addr == target
    }
}

fun BluetoothAudioOutput.normalizedRouteId(): String? = normalizeRouteIdentifier(address)

private fun isBluetoothOutputDevice(type: Int): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> true
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            } else {
                false
            }
        }
    }
}

private fun AudioDeviceInfo.typePriority(): Int = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
    AudioDeviceInfo.TYPE_BLE_HEADSET -> 1
    AudioDeviceInfo.TYPE_BLE_SPEAKER -> 2
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 3
    else -> 99
}

@SuppressLint("MissingPermission")
fun AudioManager.collectBluetoothAudioOutputs(): List<BluetoothAudioOutput> {
    val devices = getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { isBluetoothOutputDevice(it.type) }
        .sortedBy { it.typePriority() }
        .distinctBy { it.id }

    return devices.map { info ->
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        } ?: "Bluetooth audio"
        val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.address?.trim()?.takeIf { a ->
                a.isNotEmpty() &&
                    !a.equals("02:00:00:00:00:00", ignoreCase = true) &&
                    !a.startsWith("00:00:00", ignoreCase = true)
            }
        } else {
            null
        }
        BluetoothAudioOutput(productName = name, address = addr)
    }
}

/**
 * Live Bluetooth audio **output** devices (similar to iOS current audio route list for playback).
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberBluetoothAudioOutputs(permissionGranted: Boolean): List<BluetoothAudioOutput> {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val audioManager = remember(appContext) {
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var outputs by remember {
        mutableStateOf(
            if (permissionGranted && hasBluetoothAudioPermission(appContext)) {
                audioManager.collectBluetoothAudioOutputs()
            } else {
                emptyList()
            },
        )
    }

    DisposableEffect(audioManager, appContext, permissionGranted) {
        val handler = Handler(Looper.getMainLooper())
        fun refresh() {
            outputs = if (permissionGranted && hasBluetoothAudioPermission(appContext)) {
                audioManager.collectBluetoothAudioOutputs()
            } else {
                emptyList()
            }
        }

        refresh()
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                refresh()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                refresh()
            }
        }
        audioManager.registerAudioDeviceCallback(callback, handler)
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    return outputs
}
