package com.example.livora.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.livora.ui.ac.AcControllerScreen
import com.example.livora.ui.ac.AcViewModel
import com.example.livora.ui.bulb.BulbControllerScreen
import com.example.livora.ui.bulb.BulbViewModel
import com.example.livora.ui.dashboard.DashboardScreen
import com.example.livora.ui.quicksettings.QuickSettingsScreen

@Composable
fun LivoraNavHost(navController: NavHostController) {
    val acViewModel: AcViewModel = viewModel()
    val bulbViewModel: BulbViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.QuickSettings.route
    ) {
        composable(Screen.QuickSettings.route) {
            QuickSettingsScreen(
                acViewModel = acViewModel,
                bulbViewModel = bulbViewModel,
                onNavigateToAdvanced = { navController.navigate(Screen.Dashboard.route) }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                acViewModel = acViewModel,
                bulbViewModel = bulbViewModel,
                onNavigateToAc = { navController.navigate(Screen.AcController.route) },
                onNavigateToBulb = { navController.navigate(Screen.BulbController.route) }
            )
        }
        composable(Screen.AcController.route) {
            AcControllerScreen(
                viewModel = acViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.BulbController.route) {
            BulbControllerScreen(
                viewModel = bulbViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
