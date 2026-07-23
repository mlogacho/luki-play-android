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
     * Cambia la contraseña. En éxito el backend ya cerró todas las sesiones,
     * así que la pantalla debe hacer logout local después. Los errores se
     * traducen con la misma regla del resto de auth.
     */
    fun changePassword(
        current: String,
        next: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            authRepository.changePassword(current, next)
                .onSuccess { onSuccess() }
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
