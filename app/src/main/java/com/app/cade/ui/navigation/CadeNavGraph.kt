package com.app.cade.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.screens.ConnectionPanelScreen
import com.app.cade.ui.screens.DashboardScreen
import com.app.cade.ui.screens.PermissionsScreen
import com.app.cade.ui.screens.RegistrationScreen

@Composable
fun CadeNavGraph(navController: NavHostController, viewModel: AppViewModel) {
    NavHost(
        navController = navController,
        startDestination = "permissions"
    ) {
        composable("permissions") {
            PermissionsScreen(onPermissionsGranted = {
                navController.navigate("registration") { popUpTo("permissions") { inclusive = true } }
            })
        }
        composable("registration") {
            RegistrationScreen(
                viewModel = viewModel,
                onRegistrationComplete = {
                    navController.navigate("dashboard") { popUpTo("registration") { inclusive = true } }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToConnections = { navController.navigate("connections") }
            )
        }
        composable("connections") {
            ConnectionPanelScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() }
            )
        }
    }
}
