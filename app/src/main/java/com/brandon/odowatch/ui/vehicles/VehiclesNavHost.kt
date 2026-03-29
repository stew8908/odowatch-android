package com.brandon.odowatch.ui.vehicles

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.brandon.odowatch.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.util.UUID

private const val ROUTE_LIST = "vehicles_list"
private const val ROUTE_EDIT = "vehicle_edit/{vehicleId}"
private const val ARG_VEHICLE_ID = "vehicleId"

@Composable
fun VehiclesNavHost(
    viewModel: VehiclesViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveFailedMessage = stringResource(R.string.save_vehicle_failed)

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
                    navController.popBackStack()
                }
            } else {
                VehicleEditScreen(
                    vehicle = vehicle,
                    isNew = isNew,
                    onSave = { updated ->
                        viewModel.saveVehicle(updated, isNew = isNew) { err ->
                            if (err == null) {
                                navController.popBackStack()
                            } else {
                                Toast.makeText(
                                    context,
                                    err.message ?: saveFailedMessage,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
