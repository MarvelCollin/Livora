package com.example.livora.data.supabase

import retrofit2.HttpException

class DictionaryRepository {

    private val api: DictionaryApi = SupabaseClient.retrofit.create(DictionaryApi::class.java)

    suspend fun fetchAll(): List<DictionaryDto> = call { api.getAll() }

    suspend fun insert(dto: DictionaryInsertDto): DictionaryDto =
        call { api.insert(dto) }.first()

    suspend fun delete(id: String) {
        call { api.delete("eq.$id") }
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
