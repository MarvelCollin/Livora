package com.example.livora.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class SheetValueRange(
    val range: String,
    val majorDimension: String,
    val values: List<List<String>>
)

interface GoogleSheetsApi {
    @GET("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("key") apiKey: String
    ): SheetValueRange
}
