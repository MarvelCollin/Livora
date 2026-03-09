package com.example.livora.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.livora.ui.ac.AcControllerScreen
import com.example.livora.ui.ac.AcViewModel
import com.example.livora.ui.dashboard.DashboardScreen

@Composable
fun LivoraNavHost(navController: NavHostController) {
    val acViewModel: AcViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                acViewModel = acViewModel,
                onNavigateToAc = { navController.navigate(Screen.AcController.route) }
            )
        }
        composable(Screen.AcController.route) {
            AcControllerScreen(
                viewModel = acViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
