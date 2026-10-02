package com.example.livora.data.supabase

import retrofit2.HttpException

class DictionaryRepository {

    private val api: DictionaryApi = SupabaseClient.retrofit.create(DictionaryApi::class.java)

    suspend fun fetchAll(): List<DictionaryDto> = call { api.getAll() }

    suspend fun insert(dto: DictionaryInsertDto): DictionaryDto {
        return try {
            insertWithLanguage(dto)
        } catch (e: IllegalStateException) {
            val optional = dto.category != null || dto.synonymTranslations != null
            if (!optional || !isMissingOptionalColumn(e.message)) throw e
            insertWithLanguage(dto.copy(category = null, synonymTranslations = null))
        }
    }

    private suspend fun insertWithLanguage(dto: DictionaryInsertDto): DictionaryDto {
        val withLanguage = dto.copy(language = LEGACY_LANGUAGE)
        if (needsLanguage) return call { api.insert(withLanguage) }.first()
        return try {
            call { api.insert(dto) }.first()
        } catch (e: IllegalStateException) {
            if (!isMissingLanguage(e.message)) throw e
            needsLanguage = true
            call { api.insert(withLanguage) }.first()
        }
    }

    suspend fun updateStats(id: String, correctCount: Int, wrongCount: Int) {
        call { api.updateStats("eq.$id", DictionaryStatsUpdateDto(correctCount, wrongCount)) }
    }

    suspend fun delete(id: String) {
        call { api.delete("eq.$id") }
    }

    private suspend fun <T> call(block: suspend () -> T): T {
        check(SupabaseClient.isConfigured) { NOT_CONFIGURED_MESSAGE }
        try {
            return block()
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
            throw IllegalStateException(body ?: "HTTP ${e.code()} ${e.message()}", e)
        }
    }

    private fun isMissingLanguage(message: String?): Boolean =
        message != null && message.contains("23502") && message.contains("language")

    private fun isMissingOptionalColumn(message: String?): Boolean =
        message != null && (message.contains("category") || message.contains("synonym_translations")) &&
            (message.contains("PGRST204") || message.contains("42703"))

    private companion object {
        const val LEGACY_LANGUAGE = "en"

        @Volatile
        var needsLanguage = false

        const val NOT_CONFIGURED_MESSAGE =
            "Cloud sync is not set up. Add secrets.properties and rebuild the app."
    }
}
