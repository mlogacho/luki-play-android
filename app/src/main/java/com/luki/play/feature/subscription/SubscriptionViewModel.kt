// feature/subscription/SubscriptionViewModel.kt
package com.luki.play.feature.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthErrorMessage
import com.luki.play.data.auth.TokenStore
import com.luki.play.data.subscription.MePlan
import com.luki.play.data.subscription.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pantalla "Mi Suscripción" (`subscription.tsx` del portal).
 *
 * El plan cacheado en [TokenStore] sirve de respaldo para el color del plan
 * mientras carga o si `plan.nombre` no viene, igual que el portal
 * (`getPlanColor(plan?.nombre ?? user?.plan)`).
 */
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SubscriptionUiState(
            cachedPlan = tokenStore.plan().orEmpty(),
            phase = SubscriptionPhase.Loading,
        )
    )
    val state: StateFlow<SubscriptionUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(phase = SubscriptionPhase.Loading) }
        viewModelScope.launch {
            repository.getMePlan()
                .onSuccess { mePlan -> _state.update { it.copy(phase = SubscriptionPhase.Loaded(mePlan)) } }
                .onFailure { t ->
                    _state.update { it.copy(phase = SubscriptionPhase.Error(AuthErrorMessage.of(t, "Error al obtener el plan"))) }
                }
        }
    }
}

data class SubscriptionUiState(
    val cachedPlan: String,
    val phase: SubscriptionPhase,
)

sealed interface SubscriptionPhase {
    data object Loading : SubscriptionPhase
    data class Error(val message: String) : SubscriptionPhase
    data class Loaded(val mePlan: MePlan) : SubscriptionPhase
}
