// data/catalog/api/CatalogApi.kt
package com.luki.play.data.catalog.api

import com.luki.play.util.Constants
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class ChannelDto(
    @Json(name = "id")         val id: String,
    @Json(name = "nombre")     val name: String,
    @Json(name = "logo")       val logo: String?,
    @Json(name = "categoria")  val category: String?,
    @Json(name = "tipo")       val type: String?,
    @Json(name = "requiereControlParental") val requiresParental: Boolean?,
)

@JsonClass(generateAdapter = true)
data class SliderDto(
    @Json(name = "id")          val id: String,
    @Json(name = "titulo")      val title: String,
    @Json(name = "subtitulo")   val subtitle: String?,
    @Json(name = "imagen")      val image: String?,
    @Json(name = "actionType")  val actionType: String?,
    @Json(name = "actionValue") val actionValue: String?,
)

@JsonClass(generateAdapter = true)
data class StreamDto(
    @Json(name = "url")        val url: String,
    @Json(name = "sessionId")  val sessionId: String?,
    @Json(name = "manifestType") val manifestType: String?,
    @Json(name = "drmScheme")  val drmScheme: String?,
    @Json(name = "licenseUrl") val licenseUrl: String?,
)

/**
 * Endpoints de catálogo. Las rutas vienen de [Constants.Public]; usamos las
 * strings directas porque Retrofit necesita literales en la anotación.
 */
interface CatalogApi {

    @GET("/public/canales")
    suspend fun getChannels(): List<ChannelDto>

    @GET("/public/sliders")
    suspend fun getSliders(): List<SliderDto>

    @GET("/public/canales/{id}/stream")
    suspend fun getChannelStream(@Path("id") channelId: String): StreamDto
}
