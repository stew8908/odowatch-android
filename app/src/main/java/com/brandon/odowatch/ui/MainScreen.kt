package com.brandon.odowatch.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    modifier: Modifier = Modifier,
) {
    val profileUsername by authViewModel.profileUsername.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteAccountConfirm by rememberSaveable { mutableStateOf(false) }
    val deleteAccountFailed = stringResource(R.string.delete_account_failed)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.field_username),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = profileUsername?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.profile_no_username),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { authViewModel.signOut() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(text = stringResource(R.string.sign_out))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showDeleteAccountConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(text = stringResource(R.string.delete_account))
        }
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text(stringResource(R.string.delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.delete_account_confirm_message)) },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountConfirm = false
                        authViewModel.deleteAccount { success, error ->
                            if (!success) {
                                Toast.makeText(
                                    context,
                                    error ?: deleteAccountFailed,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}
