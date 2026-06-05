package com.example.livora.data.dictionary

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

data class DefinitionDto(
    @SerializedName("definition") val definition: String?,
    @SerializedName("example") val example: String?
)

data class MeaningDto(
    @SerializedName("partOfSpeech") val partOfSpeech: String?,
    @SerializedName("definitions") val definitions: List<DefinitionDto>?
)

data class DictEntryDto(
    @SerializedName("word") val word: String?,
    @SerializedName("phonetic") val phonetic: String?,
    @SerializedName("meanings") val meanings: List<MeaningDto>?
)

data class MyMemoryData(
    @SerializedName("translatedText") val translatedText: String?
)

data class MyMemoryDto(
    @SerializedName("responseData") val responseData: MyMemoryData?
)

interface LookupApi {

    @GET("api/v2/entries/{lang}/{word}")
    suspend fun define(
        @Path("lang") lang: String,
        @Path("word") word: String
    ): List<DictEntryDto>

    @GET
    suspend fun translate(@Url url: String): MyMemoryDto
}

object LookupClient {

    private const val TIMEOUT_SECONDS = 20L

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val api: LookupApi = Retrofit.Builder()
        .baseUrl("https://api.dictionaryapi.dev/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LookupApi::class.java)
}
