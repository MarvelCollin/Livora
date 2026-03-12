package com.example.livora.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object AcController : Screen("ac_controller")
    data object BulbController : Screen("bulb_controller")
}
