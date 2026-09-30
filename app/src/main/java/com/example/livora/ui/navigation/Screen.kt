package com.example.livora.ui.navigation

sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object AcController : Screen("ac_controller")
    data object BulbController : Screen("bulb_controller")
    data object DictionaryQuiz : Screen("dictionary_quiz")
    data object Vault : Screen("vault")
    data object TodoDetail : Screen("todo_detail/{todoId}") {
        const val ARG_TODO_ID = "todoId"
        fun create(todoId: String) = "todo_detail/$todoId"
    }
}

object ToolRoutes {
    const val QR = "tools/qr"
    const val DOCUMENTS = "tools/documents"
    const val DOCUMENT = "tools/documents/{id}"
    const val USAGE = "tools/usage"
    const val CLEANER = "tools/cleaner"
    const val CLEANER_REVIEW = "tools/cleaner/review/{source}"

    fun review(source: String) = "tools/cleaner/review/$source"

    fun document(id: Long) = "tools/documents/$id"
}
