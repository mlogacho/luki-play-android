// feature/profile/ProfileViewModel.kt
package com.luki.play.feature.profile

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pantalla de cuenta (`profile.tsx` del portal).
 *
 * El estado arranca con la identidad CACHEADA en [TokenStore] (nombre, correo,
 * plan) para que el avatar y su color se pinten al instante, y en paralelo
 * carga el perfil completo de `GET /auth/me`. El color del avatar sale del
 * plan cacheado, igual que en el portal (usa `user.plan`, no el perfil).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileUiState(
            cachedName  = tokenStore.displayName().orEmpty(),
            cachedEmail = tokenStore.email().orEmpty(),
            plan        = tokenStore.plan().orEmpty(),
            phase       = ProfilePhase.Loading,
        )
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { load() }

    /** (Re)carga el perfil. Lo usa también el botón "Reintentar". */
    fun load() {
        _state.update { it.copy(phase = ProfilePhase.Loading) }
        viewModelScope.launch {
            authRepository.getProfile()
                .onSuccess { profile -> _state.update { it.copy(phase = ProfilePhase.Loaded(profile)) } }
                .onFailure { t ->
                    _state.update { it.copy(phase = ProfilePhase.Error(AuthErrorMessage.of(t, "No autenticado"))) }
                }
        }
    }

    /**
     * Cambia la contraseña. En éxito el backend ya cerró TODAS las sesiones
     * (incluida esta), así que hay que forzar el cierre de sesión local después.
     *
     * Fiel al portal (`setTimeout(onSuccess, 1800)`): se muestra el éxito ~1,8 s
     * y luego se fuerza el logout. El retardo y el `onForcedLogout` viven en
     * [viewModelScope] —no en la corrutina de la hoja— para que el cierre de
     * sesión ocurra SÍ O SÍ aunque el usuario descarte la hoja dentro de esa
     * ventana; si no, la app se quedaría con un token que el servidor ya revocó.
     */
    fun changePassword(
        current: String,
        next: String,
        onSuccess: () -> Unit,
        onForcedLogout: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            authRepository.changePassword(current, next)
                .onSuccess {
                    onSuccess()
                    delay(1800)
                    onForcedLogout()
                }
                .onFailure { onError(AuthErrorMessage.of(it, "Error al cambiar la contraseña")) }
        }
    }
}

data class ProfileUiState(
    val cachedName: String,
    val cachedEmail: String,
    val plan: String,
    val phase: ProfilePhase,
)

sealed interface ProfilePhase {
    data object Loading : ProfilePhase
    data class Error(val message: String) : ProfilePhase
    data class Loaded(val profile: UserProfile) : ProfilePhase
}
