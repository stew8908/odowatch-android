package com.brandon.odowatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

@Composable
private fun ProfileScreen(
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val user by authViewModel.currentUser.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = user?.email ?: "No email")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { authViewModel.signOut() }) {
            Text(text = stringResource(R.string.sign_out))
        }
    }
}
