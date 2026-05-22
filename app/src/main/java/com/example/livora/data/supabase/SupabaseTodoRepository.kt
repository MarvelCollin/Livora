package com.example.livora.data.supabase

class SupabaseTodoRepository {

    private val api: SupabaseTodoApi = SupabaseClient.retrofit.create(SupabaseTodoApi::class.java)

    suspend fun fetchAllTodos(): List<SupabaseTodoDto> = api.getAllTodos()

    suspend fun fetchAllCompletions(): List<SupabaseCompletionDto> = api.getAllCompletions()

    suspend fun insertTodo(dto: SupabaseTodoInsertDto): SupabaseTodoDto =
        api.insertTodo(dto).first()

    suspend fun updateTodo(id: String, dto: SupabaseTodoUpdateDto): SupabaseTodoDto =
        api.updateTodo("eq.$id", dto).first()

    suspend fun deleteTodo(id: String) {
        api.deleteTodo("eq.$id")
    }

    suspend fun insertCompletion(dto: SupabaseCompletionInsertDto): SupabaseCompletionDto =
        api.insertCompletion(dto).first()

    suspend fun deleteCompletion(id: String) {
        api.deleteCompletion("eq.$id")
    }
}
