package com.example.livora.data

import com.example.livora.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SheetsRepository {

    private val api: GoogleSheetsApi = Retrofit.Builder()
        .baseUrl("https://sheets.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GoogleSheetsApi::class.java)

    suspend fun fetchValues(range: String): SheetValueRange {
        return api.getValues(
            spreadsheetId = BuildConfig.SHEETS_SPREADSHEET_ID,
            range = range,
            apiKey = BuildConfig.SHEETS_API_KEY
        )
    }
}
