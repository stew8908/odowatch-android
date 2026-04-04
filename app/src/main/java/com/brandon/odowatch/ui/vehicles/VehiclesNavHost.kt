package com.brandon.odowatch.ui.vehicles

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.brandon.odowatch.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.util.UUID

private const val ROUTE_LIST = "vehicles_list"
private const val ROUTE_EDIT = "vehicle_edit/{vehicleId}"
private const val ARG_VEHICLE_ID = "vehicleId"

/** Pops the edit screen only; safe to call twice (e.g. delete + snapshot) without clearing the list. */
private fun NavController.popToVehicleList(): Boolean =
    popBackStack(ROUTE_LIST, inclusive = false)

@Composable
fun VehiclesNavHost(
    viewModel: VehiclesViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveFailedMessage = stringResource(R.string.save_vehicle_failed)
    val deleteFailedMessage = stringResource(R.string.delete_vehicle_failed)

    NavHost(
        navController = navController,
        startDestination = ROUTE_LIST,
        modifier = modifier,
    ) {
        composable(ROUTE_LIST) {
            VehicleListScreen(
                vehicles = vehicles,
                onVehicleClick = { id ->
                    navController.navigate("vehicle_edit/$id")
                },
                onAddVehicleClick = {
                    navController.navigate("vehicle_edit/new")
                },
            )
        }
        composable(
            route = ROUTE_EDIT,
            arguments = listOf(
                navArgument(ARG_VEHICLE_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val vehicleId = entry.arguments?.getString(ARG_VEHICLE_ID) ?: return@composable
            val isNew = vehicleId == "new"
            val vehicle = if (isNew) {
                Vehicle(
                    id = UUID.randomUUID().toString(),
                    vehicleName = "",
                )
            } else {
                vehicles.find { it.id == vehicleId }
            }

            if (vehicle == null) {
                LaunchedEffect(vehicleId) {
                    navController.popToVehicleList()
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                VehicleEditScreen(
                    vehicle = vehicle,
                    isNew = isNew,
                    onSave = { updated ->
                        viewModel.saveVehicle(updated, isNew = isNew) { err ->
                            if (err == null) {
                                navController.popToVehicleList()
                            } else {
                                Toast.makeText(
                                    context,
                                    err.message ?: saveFailedMessage,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    onBack = { navController.popToVehicleList() },
                    onDelete = if (isNew) {
                        null
                    } else {
                        {
                            viewModel.deleteVehicle(vehicle.id) { err ->
                                if (err == null) {
                                    navController.popToVehicleList()
                                } else {
                                    Toast.makeText(
                                        context,
                                        err.message ?: deleteFailedMessage,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
