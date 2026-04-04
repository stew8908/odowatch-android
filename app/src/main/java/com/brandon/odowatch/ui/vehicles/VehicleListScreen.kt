package com.brandon.odowatch.ui.vehicles

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brandon.odowatch.R
import com.brandon.odowatch.audio.hasBluetoothAudioPermission
import com.brandon.odowatch.audio.rememberBluetoothAudioOutputs
import com.brandon.odowatch.audio.routeUuidMatchesActiveBluetooth
import java.text.NumberFormat
import java.util.Locale

private val ConnectedGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(
    vehicles: List<Vehicle>,
    onVehicleClick: (String) -> Unit,
    onAddVehicleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bluetoothConnectGranted by remember {
        mutableStateOf(hasBluetoothAudioPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bluetoothConnectGranted = granted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothAudioPermission(context)) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val activeBluetoothOutputs = rememberBluetoothAudioOutputs(
        permissionGranted = bluetoothConnectGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
    )

    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.vehicles_title)) },
            actions = {
                IconButton(onClick = onAddVehicleClick) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_vehicle),
                    )
                }
            },
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = vehicles,
                key = { it.id },
            ) { vehicle ->
                val routeMatches = routeUuidMatchesActiveBluetooth(
                    vehicle.routeUUID,
                    activeBluetoothOutputs,
                )
                VehicleRow(
                    vehicle = vehicle,
                    odometerFormatted = numberFormat.format(vehicle.listDisplayOdometer()),
                    milesUntilServiceFormatted = numberFormat.format(vehicle.milesUntilNextServiceAdjusted()),
                    isActiveAudioRoute = routeMatches,
                    onClick = { onVehicleClick(vehicle.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun VehicleRow(
    vehicle: Vehicle,
    odometerFormatted: String,
    milesUntilServiceFormatted: String,
    isActiveAudioRoute: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.DirectionsCar,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = vehicle.vehicleName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.odometer_reading, odometerFormatted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.miles_until_service_adjusted, milesUntilServiceFormatted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isActiveAudioRoute) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.vehicle_audio_route_connected_icon_desc),
                    modifier = Modifier.size(22.dp),
                    tint = ConnectedGreen,
                )
                Text(
                    text = stringResource(R.string.vehicle_audio_route_connected),
                    style = MaterialTheme.typography.labelLarge,
                    color = ConnectedGreen,
                )
            }
        }
    }
}
