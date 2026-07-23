// data/subscription/SubscriptionRepository.kt
package com.luki.play.data.subscription

import com.luki.play.data.subscription.api.MePlanDto
import com.luki.play.data.subscription.api.SubscriptionApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Plan contratado, ya mapeado a dominio (`MePlanResponse.plan` del portal). */
data class PlanInfo(
    val nombre: String,
    val descripcion: String,
    val precio: Double?,
    val moneda: String,
    val videoQuality: String,
    val maxDevices: Int,
    val maxConcurrentStreams: Int,
    val maxProfiles: Int,
)

/** Vigencia de la suscripción (`MePlanResponse.subscription`). Puede no existir. */
data class SubscriptionInfo(
    val status: String,
    val startDate: String,
    val expirationDate: String,
    val gracePeriodEnd: String?,
)

/** Resultado de `GET /public/me/plan`: plan + suscripción (esta última opcional). */
data class MePlan(
    val plan: PlanInfo?,
    val subscription: SubscriptionInfo?,
)

/**
 * Repositorio de suscripción. Encapsula `GET /public/me/plan` y mapea el DTO a
 * dominio con vacíos seguros. Suspende en [Dispatchers.IO].
 */
@Singleton
class SubscriptionRepository internal constructor(
    private val api: SubscriptionApi,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject constructor(api: SubscriptionApi) : this(api, Dispatchers.IO)

    suspend fun getMePlan(): Result<MePlan> = withContext(ioDispatcher) {
        runCatching { api.mePlan().toDomain() }
            .onFailure { Timber.w(it, "SubscriptionRepository: getMePlan falló") }
    }

    private fun MePlanDto.toDomain(): MePlan = MePlan(
        plan = plan?.let {
            PlanInfo(
                nombre               = it.nombre.orEmpty(),
                descripcion          = it.descripcion.orEmpty(),
                precio               = it.precio,
                moneda               = it.moneda.orEmpty().ifBlank { "USD" },
                videoQuality         = it.videoQuality.orEmpty(),
                maxDevices           = it.maxDevices,
                maxConcurrentStreams = it.maxConcurrentStreams,
                maxProfiles          = it.maxProfiles,
            )
        },
        subscription = subscription?.let {
            SubscriptionInfo(
                status         = it.status.orEmpty(),
                startDate      = it.startDate.orEmpty(),
                expirationDate = it.expirationDate.orEmpty(),
                gracePeriodEnd = it.gracePeriodEnd,
            )
        },
    )
}
