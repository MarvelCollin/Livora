package com.example.livora.data.supabase

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

data class TodoDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("notes") val notes: String,
    @SerializedName("interval_value") val intervalValue: Int,
    @SerializedName("interval_unit") val intervalUnit: String,
    @SerializedName("time_of_day") val timeOfDay: String?,
    @SerializedName("duration_value") val durationValue: Int,
    @SerializedName("duration_unit") val durationUnit: String,
    @SerializedName("has_timer") val hasTimer: Boolean = false,
    @SerializedName("created_at") val createdAt: Long
)

data class TodoInsertDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("notes") val notes: String,
    @SerializedName("interval_value") val intervalValue: Int,
    @SerializedName("interval_unit") val intervalUnit: String,
    @SerializedName("time_of_day") val timeOfDay: String?,
    @SerializedName("duration_value") val durationValue: Int,
    @SerializedName("duration_unit") val durationUnit: String,
    @SerializedName("has_timer") val hasTimer: Boolean,
    @SerializedName("created_at") val createdAt: Long
)

data class TodoUpdateDto(
    @SerializedName("title") val title: String,
    @SerializedName("notes") val notes: String,
    @SerializedName("interval_value") val intervalValue: Int,
    @SerializedName("interval_unit") val intervalUnit: String,
    @SerializedName("time_of_day") val timeOfDay: String?,
    @SerializedName("duration_value") val durationValue: Int,
    @SerializedName("duration_unit") val durationUnit: String,
    @SerializedName("has_timer") val hasTimer: Boolean
)

data class CompletionDto(
    @SerializedName("id") val id: String,
    @SerializedName("todo_id") val todoId: String,
    @SerializedName("completed_at") val completedAt: Long
)

data class CompletionInsertDto(
    @SerializedName("id") val id: String,
    @SerializedName("todo_id") val todoId: String,
    @SerializedName("completed_at") val completedAt: Long
)

interface TodoApi {

    @GET("todos")
    suspend fun getAllTodos(
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): List<TodoDto>

    @Headers("Prefer: return=representation")
    @POST("todos")
    suspend fun insertTodo(@Body body: TodoInsertDto): List<TodoDto>

    @Headers("Prefer: return=representation")
    @PATCH("todos")
    suspend fun updateTodo(
        @Query("id") idFilter: String,
        @Body body: TodoUpdateDto
    ): List<TodoDto>

    @DELETE("todos")
    suspend fun deleteTodo(@Query("id") idFilter: String)

    @GET("todo_completions")
    suspend fun getAllCompletions(
        @Query("select") select: String = "*",
        @Query("order") order: String = "completed_at.desc"
    ): List<CompletionDto>

    @Headers("Prefer: return=representation")
    @POST("todo_completions")
    suspend fun insertCompletion(@Body body: CompletionInsertDto): List<CompletionDto>

    @DELETE("todo_completions")
    suspend fun deleteCompletion(@Query("id") idFilter: String)
}
