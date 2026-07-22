// data/streams/api/StreamSessionApi.kt
package com.luki.play.data.streams.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class StartStreamRequest(
    @Json(name = "channelId") val channelId: String,
    @Json(name = "deviceId")  val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class StartStreamResponse(
    @Json(name = "streamId") val streamId: String,
)

/**
 * Sesiones de reproducción — es lo que aplica el tope de streams simultáneos
 * del plan. Contrato tomado de `frontend/services/streamApi.ts` y de
 * `backend`, `stream-session.service.ts`.
 *
 * El lease es por **(cliente, dispositivo)**: repetir `start` con el mismo
 * deviceId NO consume otro cupo, solo actualiza el canal y el heartbeat. Por
 * eso hacer zapping es barato y reabrir tras un fallo es seguro.
 *
 * `heartbeat` y `stop` devuelven [Response] en crudo porque el código importa:
 * un 4xx significa "esta sesión ya no vale" y no debe reintentarse, mientras
 * que un 5xx sí.
 */
interface StreamSessionApi {

    /** @throws retrofit2.HttpException 429 si se alcanzó el tope del plan. */
    @POST("/public/streams/start")
    suspend fun start(@Body body: StartStreamRequest): StartStreamResponse

    @PATCH("/public/streams/{streamId}/heartbeat")
    suspend fun heartbeat(@Path("streamId") streamId: String): Response<Unit>

    @DELETE("/public/streams/{streamId}")
    suspend fun stop(@Path("streamId") streamId: String): Response<Unit>
}
