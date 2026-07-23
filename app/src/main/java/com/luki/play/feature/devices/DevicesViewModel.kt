// feature/devices/DevicesViewModel.kt
package com.luki.play.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthErrorMessage
import com.luki.play.data.devices.Device
import com.luki.play.data.devices.DevicesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Mis Dispositivos" (`devices.tsx` del portal).
 *
 * Rename y remove siguen la conducta del portal: primero espera el éxito del
 * backend y luego actualiza la lista local (no es optimista). Los fallos de
 * eliminación se exponen por [actionError] para mostrar una alerta, como el
 * `Alert.alert` del portal.
 */
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val repository: DevicesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState(phase = DevicesPhase.Loading))
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(phase = DevicesPhase.Loading) }
        viewModelScope.launch {
            repository.load()
                .onSuccess { list -> _state.update { it.copy(phase = DevicesPhase.Loaded(list.devices, list.limit)) } }
                .onFailure { t ->
                    _state.update { it.copy(phase = DevicesPhase.Error(AuthErrorMessage.of(t, "Error al cargar dispositivos"))) }
                }
        }
    }

    fun rename(fingerprint: String, nombre: String) {
        viewModelScope.launch {
            repository.rename(fingerprint, nombre)
                .onSuccess { updateDevices { list -> list.map { if (it.fingerprint == fingerprint) it.copy(nombre = nombre) else it } } }
                .onFailure { _actionError.value = AuthErrorMessage.of(it, "Error al renombrar") }
        }
    }

    fun remove(fingerprint: String) {
        viewModelScope.launch {
            repository.remove(fingerprint)
                .onSuccess { updateDevices { list -> list.filterNot { it.fingerprint == fingerprint } } }
                .onFailure { _actionError.value = AuthErrorMessage.of(it, "Error al eliminar") }
        }
    }

    fun consumeActionError() { _actionError.value = null }

    private inline fun updateDevices(transform: (List<Device>) -> List<Device>) {
        _state.update { st ->
            val phase = st.phase
            if (phase is DevicesPhase.Loaded) st.copy(phase = phase.copy(devices = transform(phase.devices)))
            else st
        }
    }
}

data class DevicesUiState(val phase: DevicesPhase)

sealed interface DevicesPhase {
    data object Loading : DevicesPhase
    data class Error(val message: String) : DevicesPhase
    data class Loaded(val devices: List<Device>, val limit: Int) : DevicesPhase
}
