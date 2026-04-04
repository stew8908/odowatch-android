package com.brandon.odowatch.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.brandon.odowatch.MainActivity
import com.brandon.odowatch.R
import com.brandon.odowatch.audio.collectBluetoothAudioOutputs
import com.brandon.odowatch.audio.hasBluetoothAudioPermission
import com.brandon.odowatch.audio.routeUuidMatchesActiveBluetooth
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToLong

/**
 * Foreground service: while the vehicle's Bluetooth audio route matches, accumulates GPS distance
 * as successive segment lengths (previous fix → next fix). On disconnect or stop, adds rounded
 * miles to Firestore [estimatedMiles] via [FieldValue.increment].
 */
class DriveDistanceForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var audioManager: AudioManager

    private val stateLock = Any()
    private var activeVehicleId: String? = null
    private var expectedRouteUuid: String? = null
    private var sessionMiles: Double = 0.0
    private var lastLocation: Location? = null

    private val audioHandler = Handler(Looper.getMainLooper())
    private var audioPollRunnable: Runnable? = null

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            checkRouteStillMatches()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            checkRouteStillMatches()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            synchronized(stateLock) {
                lastLocation?.let { prev ->
                    val meters = prev.distanceTo(loc)
                    if (meters > 0.5f) {
                        sessionMiles += meters / METERS_PER_MILE
                    }
                }
                lastLocation = loc
            }
            updateNotification()
            publishUiSessionState()
        }
    }

    private fun publishUiSessionState() {
        val vid = synchronized(stateLock) { activeVehicleId }
        val miles = synchronized(stateLock) { sessionMiles }
        if (vid == null) {
            DriveTrackingSessionState.clear()
        } else {
            DriveTrackingSessionState.publish(vid, miles)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch { flushSessionAndStop() }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val vid = intent.getStringExtra(EXTRA_VEHICLE_ID) ?: return START_NOT_STICKY
                val route = intent.getStringExtra(EXTRA_ROUTE_UUID) ?: return START_NOT_STICKY
                // Required before any async work after startForegroundService().
                startForegroundWithNotification()
                serviceScope.launch { handleStart(vid, route) }
            }
        }
        return START_STICKY
    }

    private suspend fun handleStart(vehicleId: String, routeUuid: String) {
        if (!hasBluetoothAudioPermission(this) || !hasLocationPermission()) {
            stopSelfSafely()
            return
        }

        val alreadyTracking = synchronized(stateLock) {
            activeVehicleId == vehicleId && expectedRouteUuid == routeUuid
        }
        if (alreadyTracking) {
            startForegroundWithNotification()
            publishUiSessionState()
            return
        }

        val previousId = synchronized(stateLock) { activeVehicleId }
        if (previousId != null && previousId != vehicleId) {
            flushSessionInternal()
        }

        stopLocationUpdatesInternal()
        unregisterAudioInternal()

        synchronized(stateLock) {
            activeVehicleId = vehicleId
            expectedRouteUuid = routeUuid
            sessionMiles = 0.0
            lastLocation = null
        }

        startForegroundWithNotification()
        registerAudio()
        startLocationUpdates()
        scheduleAudioPoll()
        publishUiSessionState()
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerAudio() {
        audioManager.registerAudioDeviceCallback(audioCallback, audioHandler)
    }

    private fun unregisterAudioInternal() {
        runCatching { audioManager.unregisterAudioDeviceCallback(audioCallback) }
        audioPollRunnable?.let { audioHandler.removeCallbacks(it) }
        audioPollRunnable = null
    }

    private fun scheduleAudioPoll() {
        audioPollRunnable?.let { audioHandler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                checkRouteStillMatches()
                audioHandler.postDelayed(this, AUDIO_POLL_MS)
            }
        }
        audioPollRunnable = r
        audioHandler.postDelayed(r, AUDIO_POLL_MS)
    }

    private fun checkRouteStillMatches() {
        val route = synchronized(stateLock) { expectedRouteUuid } ?: return
        if (!hasBluetoothAudioPermission(this)) return
        val outputs = audioManager.collectBluetoothAudioOutputs()
        if (!routeUuidMatchesActiveBluetooth(route, outputs)) {
            serviceScope.launch { flushSessionAndStop() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_MS)
            .setMaxUpdateDelayMillis(LOCATION_MAX_DELAY_MS)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdatesInternal() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val milesText = synchronized(stateLock) { "%.2f".format(sessionMiles) }
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_drive_tracking_title))
            .setContentText(getString(R.string.notif_drive_tracking_body, milesText))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_drive_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        mgr.createNotificationChannel(ch)
    }

    private suspend fun flushSessionAndStop() {
        flushSessionInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        unregisterAudioInternal()
        stopLocationUpdatesInternal()
        synchronized(stateLock) {
            activeVehicleId = null
            expectedRouteUuid = null
            sessionMiles = 0.0
            lastLocation = null
        }
        DriveTrackingSessionState.clear()
        stopSelf()
    }

    private suspend fun flushSessionInternal() {
        val vid = synchronized(stateLock) { activeVehicleId } ?: return
        val delta = synchronized(stateLock) {
            val d = sessionMiles.roundToLong()
            sessionMiles = 0.0
            lastLocation = null
            d
        }
        if (delta <= 0L) return
        if (FirebaseAuth.getInstance().currentUser == null) return
        runCatching {
            FirebaseFirestore.getInstance()
                .collection("vehicles")
                .document(vid)
                .update("estimatedMiles", FieldValue.increment(delta))
                .await()
        }
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        unregisterAudioInternal()
        stopLocationUpdatesInternal()
        synchronized(stateLock) {
            activeVehicleId = null
            expectedRouteUuid = null
            sessionMiles = 0.0
            lastLocation = null
        }
        DriveTrackingSessionState.clear()
        stopSelf()
    }

    override fun onDestroy() {
        unregisterAudioInternal()
        stopLocationUpdatesInternal()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch { flushSessionAndStop() }
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val CHANNEL_ID = "drive_distance_track"
        private const val NOTIFICATION_ID = 7101
        const val ACTION_START = "com.brandon.odowatch.action.START_DRIVE_TRACK"
        const val ACTION_STOP = "com.brandon.odowatch.action.STOP_DRIVE_TRACK"
        const val EXTRA_VEHICLE_ID = "vehicle_id"
        const val EXTRA_ROUTE_UUID = "route_uuid"

        private const val METERS_PER_MILE = 1609.344
        private const val LOCATION_INTERVAL_MS = 10_000L
        private const val LOCATION_FASTEST_MS = 5_000L
        private const val LOCATION_MAX_DELAY_MS = 30_000L
        private const val AUDIO_POLL_MS = 15_000L

        fun start(context: Context, vehicleId: String, routeUuid: String) {
            val app = context.applicationContext
            val i = Intent(app, DriveDistanceForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VEHICLE_ID, vehicleId)
                putExtra(EXTRA_ROUTE_UUID, routeUuid)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(i)
            } else {
                app.startService(i)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.startService(
                Intent(app, DriveDistanceForegroundService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
