package com.brandon.odowatch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brandon.odowatch.R
import com.brandon.odowatch.location.DriveTrackingCoordinator
import com.brandon.odowatch.ui.auth.AuthViewModel
import com.brandon.odowatch.ui.auth.LoginScreen
import com.brandon.odowatch.ui.auth.SignUpScreen
import com.brandon.odowatch.ui.vehicles.VehiclesNavHost
import com.brandon.odowatch.ui.vehicles.VehiclesViewModel

private enum class MainTab {
    Vehicles,
    Profile,
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val authViewModel: AuthViewModel = viewModel()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

    if (currentUser == null) {
        var showSignUp by rememberSaveable { mutableStateOf(false) }
        if (showSignUp) {
            SignUpScreen(
                viewModel = authViewModel,
                onNavigateToLogin = { showSignUp = false }
            )
        } else {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToSignUp = { showSignUp = true }
            )
        }
    } else {
        AppContent(authViewModel = authViewModel, modifier = modifier)
    }
}

@Composable
private fun AppContent(
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Vehicles) }
    val vehiclesViewModel: VehiclesViewModel = viewModel()
    val vehicles by vehiclesViewModel.vehicles.collectAsStateWithLifecycle()
    val revenueCatPro by authViewModel.revenueCatProActive.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? ComponentActivity

    DriveTrackingCoordinator(vehicles = vehicles)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Vehicles,
                    onClick = { selectedTab = MainTab.Vehicles },
                    icon = {
                        Icon(
                            Icons.Filled.DirectionsCar,
                            contentDescription = stringResource(R.string.tab_vehicles),
                        )
                    },
                    label = { Text(stringResource(R.string.tab_vehicles)) },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Profile,
                    onClick = { selectedTab = MainTab.Profile },
                    icon = {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = stringResource(R.string.tab_profile),
                        )
                    },
                    label = { Text(stringResource(R.string.tab_profile)) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                MainTab.Vehicles -> VehiclesNavHost(
                    viewModel = vehiclesViewModel,
                    isPro = revenueCatPro,
                    onLimitReached = {
                        activity?.let {
                            authViewModel.presentRevenueCatPaywall(it) { _ -> }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                MainTab.Profile -> ProfileScreen(
                    authViewModel = authViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
