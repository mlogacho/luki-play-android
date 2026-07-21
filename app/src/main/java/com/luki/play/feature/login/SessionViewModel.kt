// feature/login/SessionViewModel.kt
package com.luki.play.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthRepository
import com.luki.play.data.profiles.ProfilesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Acciones de sesión que viven por encima de una pantalla concreta
 * (hoy solo logout). Se aloja en el NavGraph para que el cierre de sesión
 * pueda reescribir el back-stack.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profilesRepository: ProfilesRepository,
) : ViewModel() {

    private val _loggedOut = MutableStateFlow(false)

    /** Pasa a true cuando la sesión quedó cerrada; el NavGraph navega. */
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    /**
     * Cierra sesión. [AuthRepository.logout] es best-effort contra el
     * servidor pero SIEMPRE limpia el store local, así que esto no falla.
     *
     * Limpia además el perfil activo: si no, el siguiente login entraría
     * directo a HOME con el perfil del usuario anterior.
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            profilesRepository.clearActive()
            _loggedOut.value = true
        }
    }

    /** El NavGraph ya navegó; evita re-disparos al recomponer. */
    fun consumeLoggedOut() {
        _loggedOut.value = false
    }
}
