// data/subscription/api/SubscriptionApi.kt
package com.luki.play.data.subscription.api

import com.luki.play.util.Constants
import retrofit2.http.GET

/**
 * Plan y suscripción del usuario autenticado. El Bearer lo adjunta el
 * [com.luki.play.data.network.AuthInterceptor] igual que en el resto de
 * endpoints autenticados.
 */
interface SubscriptionApi {

    @GET(Constants.Public.ME_PLAN)
    suspend fun mePlan(): MePlanDto
}
