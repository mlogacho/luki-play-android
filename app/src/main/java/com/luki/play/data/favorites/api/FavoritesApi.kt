// data/favorites/api/FavoritesApi.kt
package com.luki.play.data.favorites.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class AddFavoriteRequest(
    @Json(name = "deviceId")  val deviceId: String,
    @Json(name = "profileId") val profileId: String,
)

/**
 * Favoritos por dispositivo y perfil.
 *
 * Contrato tomado de `frontend/services/favoritesApi.ts` y del controlador
 * `backend`, módulo favorites: el GET devuelve la lista de ids de canal, y
 * tanto alta como baja aceptan el canal en la ruta.
 *
 * Las escrituras devuelven [Response] en crudo a propósito: el portal trata
 * 409 (ya era favorito) y 404 (ya no lo era) como ÉXITO, porque el estado
 * final que pedía el usuario ya se cumple. Con un `suspend fun` normal
 * Retrofit lanzaría excepción y perderíamos esa distinción.
 */
interface FavoritesApi {

    @GET("/api/favorites")
    suspend fun getFavorites(
        @Query("deviceId") deviceId: String,
        @Query("profileId") profileId: String,
    ): List<String>

    @POST("/api/favorites/{channelId}")
    suspend fun addFavorite(
        @Path("channelId") channelId: String,
        @Body body: AddFavoriteRequest,
    ): Response<Unit>

    @DELETE("/api/favorites/{channelId}")
    suspend fun removeFavorite(
        @Path("channelId") channelId: String,
        @Query("deviceId") deviceId: String,
        @Query("profileId") profileId: String,
    ): Response<Unit>
}
