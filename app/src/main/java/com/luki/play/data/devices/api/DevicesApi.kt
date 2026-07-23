// data/devices/api/DevicesApi.kt
package com.luki.play.data.devices.api

import com.luki.play.util.Constants
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Dispositivos del usuario. El Bearer lo pone el
 * [com.luki.play.data.network.AuthInterceptor].
 *
 * `register` devuelve [Response] en crudo porque el portal lo llama en modo
 * best-effort (ignora cualquier fallo) antes de listar.
 */
interface DevicesApi {

    @POST(Constants.Public.DEVICES_REGISTER)
    suspend fun register(@Body body: RegisterDeviceRequest): Response<Unit>

    @GET(Constants.Public.DEVICES)
    suspend fun getDevices(@Query("currentDevice") currentDevice: String): DevicesResponseDto

    @PATCH(Constants.Public.DEVICE_BY_FINGERPRINT)
    suspend fun rename(
        @Path("fingerprint") fingerprint: String,
        @Body body: RenameDeviceRequest,
    )

    @DELETE(Constants.Public.DEVICE_BY_FINGERPRINT)
    suspend fun remove(
        @Path("fingerprint") fingerprint: String,
        @Query("currentDevice") currentDevice: String,
    )
}
