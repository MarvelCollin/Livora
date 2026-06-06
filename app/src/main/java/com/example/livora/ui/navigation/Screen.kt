package com.example.livora.ui.navigation

sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object AcController : Screen("ac_controller")
    data object BulbController : Screen("bulb_controller")
    data object DictionaryQuiz : Screen("dictionary_quiz")
    data object TodoDetail : Screen("todo_detail/{todoId}") {
        const val ARG_TODO_ID = "todoId"
        fun create(todoId: String) = "todo_detail/$todoId"
    }
}
