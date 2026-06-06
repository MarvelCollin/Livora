package com.example.livora.data.supabase

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

data class DictionaryDto(
    @SerializedName("id") val id: String,
    @SerializedName("word") val word: String,
    @SerializedName("language") val language: String,
    @SerializedName("translation") val translation: String,
    @SerializedName("description") val description: String,
    @SerializedName("description_id") val descriptionId: String = "",
    @SerializedName("example") val example: String = "",
    @SerializedName("synonyms") val synonyms: String = "",
    @SerializedName("created_at") val createdAt: Long
)

data class DictionaryInsertDto(
    @SerializedName("id") val id: String,
    @SerializedName("word") val word: String,
    @SerializedName("language") val language: String,
    @SerializedName("translation") val translation: String,
    @SerializedName("description") val description: String,
    @SerializedName("description_id") val descriptionId: String,
    @SerializedName("example") val example: String,
    @SerializedName("synonyms") val synonyms: String,
    @SerializedName("created_at") val createdAt: Long
)

interface DictionaryApi {

    @GET("dictionary_entries")
    suspend fun getAll(
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): List<DictionaryDto>

    @Headers("Prefer: return=representation")
    @POST("dictionary_entries")
    suspend fun insert(@Body body: DictionaryInsertDto): List<DictionaryDto>

    @DELETE("dictionary_entries")
    suspend fun delete(@Query("id") idFilter: String)
}
