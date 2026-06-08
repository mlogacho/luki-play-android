// feature/parental/ParentalPinViewModel.kt
package com.luki.play.feature.parental

import androidx.lifecycle.ViewModel
import com.luki.play.data.parental.ParentalControl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class ParentalMode { CREATE, VERIFY }

data class ParentalPinUiState(
    val mode: ParentalMode,
    val pin: String = "",
    val confirmPin: String = "",
    val errorMessage: String? = null,
    val succeeded: Boolean = false,
)

@HiltViewModel
class ParentalPinViewModel @Inject constructor(
    private val parental: ParentalControl,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ParentalPinUiState(mode = if (parental.hasPin()) ParentalMode.VERIFY else ParentalMode.CREATE)
    )
    val uiState: StateFlow<ParentalPinUiState> = _state.asStateFlow()

    fun onPinChange(pin: String) {
        if (pin.length <= 6 && pin.all { it.isDigit() }) {
            _state.value = _state.value.copy(pin = pin, errorMessage = null)
        }
    }

    fun onConfirmPinChange(pin: String) {
        if (pin.length <= 6 && pin.all { it.isDigit() }) {
            _state.value = _state.value.copy(confirmPin = pin, errorMessage = null)
        }
    }

    fun submit() {
        val s = _state.value
        when (s.mode) {
            ParentalMode.CREATE -> {
                if (s.pin.length < 4) { _state.value = s.copy(errorMessage = "Mínimo 4 dígitos"); return }
                if (s.pin != s.confirmPin) { _state.value = s.copy(errorMessage = "No coinciden"); return }
                runCatching { parental.setPin(s.pin) }
                    .onSuccess { _state.value = s.copy(succeeded = true) }
                    .onFailure { _state.value = s.copy(errorMessage = it.message) }
            }
            ParentalMode.VERIFY -> {
                if (parental.verify(s.pin)) {
                    _state.value = s.copy(succeeded = true)
                } else {
                    _state.value = s.copy(pin = "", errorMessage = "PIN incorrecto")
                }
            }
        }
    }
}
