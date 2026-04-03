package com.app.cade.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.screens.ConnectionPanelScreen
import com.app.cade.ui.screens.DashboardScreen
import com.app.cade.ui.screens.PermissionsScreen
import com.app.cade.ui.screens.PermissionsScreen
import com.app.cade.ui.screens.RegistrationScreen
import com.app.cade.ui.screens.SettingsScreen
import com.app.cade.ui.screens.onboarding.OnboardingScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun CadeNavGraph(navController: NavHostController, viewModel: AppViewModel) {
    val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsState()
    
    NavHost(
        navController = navController,
        startDestination = if (isOnboardingComplete) "permissions" else "onboarding"
    ) {
        composable("onboarding") {
            OnboardingScreen(
                viewModel = viewModel,
                onFinish = {
                    navController.navigate("permissions") { popUpTo("onboarding") { inclusive = true } }
                }
            )
        }
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
                onNavigateToConnections = { navController.navigate("connections") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("connections") {
            ConnectionPanelScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() }
            )
        }
    }
}
