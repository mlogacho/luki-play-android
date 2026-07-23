// data/auth/api/TvAuthApi.kt
package com.luki.play.data.auth.api

import com.luki.play.util.Constants
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Emparejamiento de TV. Sin autenticación (la sesión aún no existe), así que
 * se provee desde el cliente "plain", igual que el login.
 */
interface TvAuthApi {

    @POST(Constants.Auth.TV_SESSION)
    suspend fun createSession(@Body body: TvSessionRequest): TvSessionDto

    @GET(Constants.Auth.TV_POLL)
    suspend fun poll(@Path("sessionId") sessionId: String): TvPollDto
}
