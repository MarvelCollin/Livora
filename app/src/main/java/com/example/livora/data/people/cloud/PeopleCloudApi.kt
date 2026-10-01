package com.example.livora.data.people.cloud

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class BackupMetaDto(
    @SerializedName("generation") val generation: Long,
    @SerializedName("parts") val parts: Int,
    @SerializedName("persons") val persons: Int,
    @SerializedName("photos") val photos: Int,
    @SerializedName("faces") val faces: Int
)

data class BackupPartDto(
    @SerializedName("part") val part: Int,
    @SerializedName("data") val data: String
)

data class BackupRowDto(
    @SerializedName("id") val id: String,
    @SerializedName("generation") val generation: Long,
    @SerializedName("part") val part: Int,
    @SerializedName("parts") val parts: Int,
    @SerializedName("persons") val persons: Int,
    @SerializedName("photos") val photos: Int,
    @SerializedName("faces") val faces: Int,
    @SerializedName("data") val data: String
)

interface PeopleCloudApi {

    @GET("people_backup")
    suspend fun metas(
        @Query("id") id: String,
        @Query("part") part: String = "eq.0",
        @Query("select") select: String = "generation,parts,persons,photos,faces",
        @Query("order") order: String = "generation.desc",
        @Query("limit") limit: Int = 10
    ): List<BackupMetaDto>

    @GET("people_backup")
    suspend fun parts(
        @Query("id") id: String,
        @Query("generation") generation: String,
        @Query("select") select: String = "part,data",
        @Query("order") order: String = "part.asc"
    ): List<BackupPartDto>

    @POST("people_backup")
    suspend fun insert(@Body rows: List<BackupRowDto>)

    @DELETE("people_backup")
    suspend fun delete(
        @Query("id") id: String,
        @Query("generation") generation: String
    )
}
