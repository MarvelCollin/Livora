package com.example.livora.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.livora.ui.ac.AcControllerScreen
import com.example.livora.ui.ac.AcViewModel
import com.example.livora.ui.bulb.BulbControllerScreen
import com.example.livora.ui.bulb.BulbViewModel
import com.example.livora.ui.dashboard.DashboardScreen
import com.example.livora.ui.quicksettings.QuickSettingsScreen
import com.example.livora.ui.todo.TodoDetailScreen
import com.example.livora.ui.todo.TodoScreen
import com.example.livora.ui.todo.TodoViewModel

@Composable
fun AppNavHost(navController: NavHostController) {
    val acViewModel: AcViewModel = viewModel()
    val bulbViewModel: BulbViewModel = viewModel()
    val todoViewModel: TodoViewModel = viewModel()

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
                onNavigateToBulb = { navController.navigate(Screen.BulbController.route) },
                onNavigateToTodo = { navController.navigate(Screen.TodoList.route) }
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
        composable(Screen.TodoList.route) {
            TodoScreen(
                viewModel = todoViewModel,
                onBack = { navController.popBackStack() },
                onOpenDetail = { id -> navController.navigate(Screen.TodoDetail.create(id)) }
            )
        }
        composable(
            route = Screen.TodoDetail.route,
            arguments = listOf(navArgument(Screen.TodoDetail.ARG_TODO_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(Screen.TodoDetail.ARG_TODO_ID).orEmpty()
            TodoDetailScreen(
                viewModel = todoViewModel,
                todoId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
