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

/**
 * Respuesta de `GET /public/canales/{id}/stream`.
 *
 * El backend devuelve EXACTAMENTE `{ "streamUrl": "..." }` — nada más
 * (`public.controller.ts`: `return { streamUrl: canal.streamUrl }`). El DTO
 * anterior era especulativo: pedía `url` y además `sessionId`,
 * `manifestType`, `drmScheme` y `licenseUrl`, campos que ese endpoint nunca
 * ha enviado. Como `url` era obligatorio, Moshi rompía con "Required value
 * 'url' missing" y NINGÚN canal llegaba a reproducirse.
 *
 * Corolario: por aquí no viaja información de DRM. El tipo de manifiesto se
 * deduce de la extensión de la URL.
 */
@JsonClass(generateAdapter = true)
data class StreamDto(
    @Json(name = "streamUrl") val streamUrl: String,
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
