// tv/compose/TvAccountViewModel.kt
package com.luki.play.tv.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthErrorMessage
import com.luki.play.data.auth.AuthRepository
import com.luki.play.data.auth.TokenStore
import com.luki.play.data.auth.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Cuenta de TV (10-foot): la TV no tenía forma de cerrar sesión. Reusa el mismo
 * `GET /auth/me` que la pantalla de perfil móvil y el `logout()` del repositorio
 * (best-effort remoto + limpieza local). El nombre cacheado en [TokenStore] pinta
 * de inmediato mientras carga el perfil completo.
 */
@HiltViewModel
class TvAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TvAccountUiState(
            cachedName = tokenStore.displayName().orEmpty(),
            phase      = TvAccountPhase.Loading,
        )
    )
    val state: StateFlow<TvAccountUiState> = _state.asStateFlow()

    init { load() }

    /** (Re)carga el perfil. Lo usa también el botón "Reintentar". */
    fun load() {
        _state.update { it.copy(phase = TvAccountPhase.Loading) }
        viewModelScope.launch {
            authRepository.getProfile()
                .onSuccess { profile -> _state.update { it.copy(phase = TvAccountPhase.Loaded(profile)) } }
                .onFailure { t ->
                    _state.update { it.copy(phase = TvAccountPhase.Error(AuthErrorMessage.of(t, "No autenticado"))) }
                }
        }
    }

    /**
     * Cierra sesión y avisa al llamador para navegar a la activación. [logout] es
     * best-effort: aunque el servidor no responda, la sesión local queda limpia,
     * así que siempre invocamos [onDone].
     */
    fun logout(onDone: () -> Unit) {
        if (_state.value.loggingOut) return
        _state.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}

data class TvAccountUiState(
    val cachedName: String,
    val phase: TvAccountPhase,
    val loggingOut: Boolean = false,
)

sealed interface TvAccountPhase {
    data object Loading : TvAccountPhase
    data class Error(val message: String) : TvAccountPhase
    data class Loaded(val profile: UserProfile) : TvAccountPhase
}
