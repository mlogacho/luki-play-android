// feature/profiles/ProfilePickerViewModel.kt
package com.luki.play.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.profiles.ProfilesRepository
import com.luki.play.data.profiles.domain.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilePickerUiState(
    val profiles: List<Profile> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pendingPinForProfile: Profile? = null,
    val pickedProfileId: String? = null,
)

@HiltViewModel
class ProfilePickerViewModel @Inject constructor(
    private val repository: ProfilesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfilePickerUiState(isLoading = true))
    val uiState: StateFlow<ProfilePickerUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            repository.list()
                .onSuccess { _state.value = _state.value.copy(profiles = it, isLoading = false) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, errorMessage = it.message) }
        }
    }

    /** Click en un perfil — si requiere PIN, queda pending; si no, lo activa. */
    fun onProfileSelected(profile: Profile) {
        if (profile.requiresPin) {
            _state.value = _state.value.copy(pendingPinForProfile = profile)
        } else {
            activate(profile)
        }
    }

    fun onPinVerified() {
        _state.value.pendingPinForProfile?.let { activate(it) }
        _state.value = _state.value.copy(pendingPinForProfile = null)
    }

    fun dismissPinPrompt() {
        _state.value = _state.value.copy(pendingPinForProfile = null)
    }

    fun consumePicked() {
        _state.value = _state.value.copy(pickedProfileId = null)
    }

    private fun activate(profile: Profile) {
        repository.setActive(profile.id)
        _state.value = _state.value.copy(pickedProfileId = profile.id)
    }
}
