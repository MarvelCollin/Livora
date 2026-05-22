package com.example.livora.data.supabase

class TodoRepository {

    private val api: TodoApi = SupabaseClient.retrofit.create(TodoApi::class.java)

    suspend fun fetchAllTodos(): List<TodoDto> = api.getAllTodos()

    suspend fun fetchAllCompletions(): List<CompletionDto> = api.getAllCompletions()

    suspend fun insertTodo(dto: TodoInsertDto): TodoDto =
        api.insertTodo(dto).first()

    suspend fun updateTodo(id: String, dto: TodoUpdateDto): TodoDto =
        api.updateTodo("eq.$id", dto).first()

    suspend fun deleteTodo(id: String) {
        api.deleteTodo("eq.$id")
    }

    suspend fun insertCompletion(dto: CompletionInsertDto): CompletionDto =
        api.insertCompletion(dto).first()

    suspend fun deleteCompletion(id: String) {
        api.deleteCompletion("eq.$id")
    }
}
