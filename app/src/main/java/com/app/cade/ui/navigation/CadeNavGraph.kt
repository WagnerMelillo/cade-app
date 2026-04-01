package com.app.cade.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.app.cade.ui.screens.PermissionsScreen
import com.app.cade.ui.screens.DashboardScreen
import com.app.cade.ui.screens.RegistrationScreen
import com.app.cade.ui.screens.ConnectionPanelScreen

@Composable
fun CadeNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "permissions") {
        composable("permissions") {
            PermissionsScreen(onPermissionsGranted = {
                navController.navigate("registration") {
                    popUpTo("permissions") { inclusive = true }
                }
            })
        }
        composable("registration") {
            RegistrationScreen(onRegistrationComplete = {
                navController.navigate("dashboard") {
                    popUpTo("registration") { inclusive = true }
                }
            })
        }
        composable("dashboard") {
            DashboardScreen(
                onNavigateToConnections = { navController.navigate("connections") }
            )
        }
        composable("connections") {
            ConnectionPanelScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
