// data/profiles/api/ProfilesApi.kt
package com.luki.play.data.profiles.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class ProfileDto(
    @Json(name = "id")            val id: String,
    @Json(name = "name")          val name: String,
    @Json(name = "avatarUrl")     val avatarUrl: String?,
    @Json(name = "isKid")         val isKid: Boolean = false,
    @Json(name = "parentalRating") val parentalRating: Int = 18,
    @Json(name = "requiresPin")   val requiresPin: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class CreateProfileRequest(
    val name: String,
    val avatarUrl: String?,
    val isKid: Boolean,
    val parentalRating: Int,
    val requiresPin: Boolean,
)

interface ProfilesApi {
    @GET("/auth/profiles")
    suspend fun list(): List<ProfileDto>

    @POST("/auth/profiles")
    suspend fun create(@Body body: CreateProfileRequest): ProfileDto

    @PUT("/auth/profiles/{id}")
    suspend fun update(@Path("id") id: String, @Body body: CreateProfileRequest): ProfileDto

    @DELETE("/auth/profiles/{id}")
    suspend fun delete(@Path("id") id: String)
}
