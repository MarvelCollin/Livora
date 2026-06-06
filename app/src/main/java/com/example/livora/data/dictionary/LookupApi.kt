package com.example.livora.data.dictionary

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

data class DefinitionDto(
    @SerializedName("definition") val definition: String?,
    @SerializedName("example") val example: String?,
    @SerializedName("synonyms") val synonyms: List<String>?
)

data class MeaningDto(
    @SerializedName("partOfSpeech") val partOfSpeech: String?,
    @SerializedName("definitions") val definitions: List<DefinitionDto>?,
    @SerializedName("synonyms") val synonyms: List<String>?
)

data class DictEntryDto(
    @SerializedName("word") val word: String?,
    @SerializedName("phonetic") val phonetic: String?,
    @SerializedName("meanings") val meanings: List<MeaningDto>?
)

data class MyMemoryData(
    @SerializedName("translatedText") val translatedText: String?
)

data class MyMemoryMatch(
    @SerializedName("translation") val translation: String?,
    @SerializedName("quality") val quality: String?
)

data class MyMemoryDto(
    @SerializedName("responseData") val responseData: MyMemoryData?,
    @SerializedName("matches") val matches: List<MyMemoryMatch>?
)

data class TatoebaTranslation(
    @SerializedName("text") val text: String?,
    @SerializedName("lang") val lang: String?
)

data class TatoebaResult(
    @SerializedName("text") val text: String?,
    @SerializedName("lang") val lang: String?,
    @SerializedName("translations") val translations: List<List<TatoebaTranslation>>?
)

data class TatoebaDto(
    @SerializedName("results") val results: List<TatoebaResult>?
)

data class LtReplacement(
    @SerializedName("value") val value: String?
)

data class LtRule(
    @SerializedName("issueType") val issueType: String?
)

data class LtMatch(
    @SerializedName("replacements") val replacements: List<LtReplacement>?,
    @SerializedName("rule") val rule: LtRule?
)

data class LanguageToolDto(
    @SerializedName("matches") val matches: List<LtMatch>?
)

interface LookupApi {

    @GET("api/v2/entries/{lang}/{word}")
    suspend fun define(
        @Path("lang") lang: String,
        @Path("word") word: String
    ): List<DictEntryDto>

    @GET
    suspend fun translate(@Url url: String): MyMemoryDto

    @GET
    suspend fun examples(@Url url: String): TatoebaDto

    @FormUrlEncoded
    @POST
    suspend fun spellCheck(
        @Url url: String,
        @Field("text") text: String,
        @Field("language") language: String
    ): LanguageToolDto
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
