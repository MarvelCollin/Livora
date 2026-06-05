package com.example.livora.data.supabase

import retrofit2.HttpException

class TodoRepository {

    private val api: TodoApi = SupabaseClient.retrofit.create(TodoApi::class.java)

    suspend fun fetchAllTodos(): List<TodoDto> = call { api.getAllTodos() }

    suspend fun fetchAllCompletions(): List<CompletionDto> = call { api.getAllCompletions() }

    suspend fun insertTodo(dto: TodoInsertDto): TodoDto =
        call { api.insertTodo(dto) }.first()

    suspend fun updateTodo(id: String, dto: TodoUpdateDto): TodoDto =
        call { api.updateTodo("eq.$id", dto) }.first()

    suspend fun deleteTodo(id: String) {
        call { api.deleteTodo("eq.$id") }
    }

    suspend fun insertCompletion(dto: CompletionInsertDto): CompletionDto =
        call { api.insertCompletion(dto) }.first()

    suspend fun deleteCompletion(id: String) {
        call { api.deleteCompletion("eq.$id") }
    }

    private suspend fun <T> call(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
            throw IllegalStateException(body ?: "HTTP ${e.code()} ${e.message()}", e)
        }
    }
}
