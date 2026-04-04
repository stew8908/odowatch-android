package com.brandon.odowatch.ui.vehicles

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.brandon.odowatch.R
import com.brandon.odowatch.audio.hasBluetoothAudioPermission
import com.brandon.odowatch.audio.normalizedRouteId
import com.brandon.odowatch.audio.rememberBluetoothAudioOutputs
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(
    vehicle: Vehicle,
    onSave: (Vehicle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
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

    LaunchedEffect(isNew) {
        if (isNew && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothAudioPermission(context)) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val bluetoothRoutes = rememberBluetoothAudioOutputs(
        permissionGranted = bluetoothConnectGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
    )
    val primaryBluetoothRoute = bluetoothRoutes.firstOrNull()

    var name by remember(vehicle.id) { mutableStateOf(vehicle.vehicleName) }
    var odometerText by remember(vehicle.id) {
        mutableStateOf(if (isNew && vehicle.initialOdometer == 0L) "" else vehicle.initialOdometer.toString())
    }
    var milesTillServiceText by remember(vehicle.id) {
        mutableStateOf(if (isNew && vehicle.nextOilChange == 0L) "" else vehicle.nextOilChange.toString())
    }

    val displayedEstimatedMiles = remember(odometerText, vehicle.initialOdometer, vehicle.estimatedMiles) {
        val parsedOdometer = odometerText.toLongOrNull()
        val odometerWillResetEstimate =
            parsedOdometer != null && parsedOdometer != vehicle.initialOdometer
        val value = if (odometerWillResetEstimate) 0L else vehicle.estimatedMiles
        NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (isNew) R.string.add_vehicle else R.string.edit_vehicle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_vehicle_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = if (isNew) {
                    { Text(stringResource(R.string.vehicle_name_example)) }
                } else {
                    null
                },
            )
            OutlinedTextField(
                value = odometerText,
                onValueChange = { odometerText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.field_odometer)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = milesTillServiceText,
                onValueChange = { milesTillServiceText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.field_miles_till_service)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Text(
                text = stringResource(R.string.estimated_miles_readout, displayedEstimatedMiles),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    val odometer = odometerText.toLongOrNull() ?: 0L
                    val milesTill = milesTillServiceText.toLongOrNull() ?: 0L
                    if (name.isBlank()) return@Button
                    val odometerChanged = odometer != vehicle.initialOdometer
                    val estimatedMiles = if (odometerChanged) 0L else vehicle.estimatedMiles
                    val routeId = if (isNew) primaryBluetoothRoute?.normalizedRouteId() else vehicle.routeUUID
                    onSave(
                        vehicle.copy(
                            vehicleName = name.trim(),
                            initialOdometer = odometer,
                            nextOilChange = milesTill,
                            estimatedMiles = estimatedMiles,
                            routeUUID = routeId,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
            if (isNew) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.audio_route_section_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !bluetoothConnectGranted -> {
                                Text(
                                    text = stringResource(R.string.bluetooth_permission_rationale),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = {
                                        permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    },
                                ) {
                                    Text(stringResource(R.string.bluetooth_permission_grant))
                                }
                            }
                            primaryBluetoothRoute == null -> {
                                Text(
                                    text = stringResource(R.string.audio_route_none_bluetooth),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = primaryBluetoothRoute.productName,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            text = primaryBluetoothRoute.address
                                                ?: stringResource(R.string.bluetooth_mac_unavailable),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
