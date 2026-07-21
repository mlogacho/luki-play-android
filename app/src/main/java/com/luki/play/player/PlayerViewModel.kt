// player/PlayerViewModel.kt
package com.luki.play.player

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/**
 * ViewModel for [PlayerActivity].
 *
 * Responsibilities:
 * - Exposes a [PlayerState] sealed hierarchy via [uiState] LiveData.
 * - Holds the active [StreamConfig] as source of truth across process death.
 * - Survives rotation / configuration changes (SavedStateHandle).
 * - Persists playback position across process-death via [SavedStateHandle].
 *
 * All mutations happen on the calling thread (main thread only from Activity).
 */
class PlayerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    // ------------------------------------------------------------------ //
    //  Keys for SavedStateHandle persistence
    // ------------------------------------------------------------------ //

    private companion object {
        const val KEY_POSITION = "playback_position_ms"
        const val KEY_CONFIG   = "stream_config"
    }

    // ------------------------------------------------------------------ //
    //  State
    // ------------------------------------------------------------------ //

    /**
     * Represents the current UI state of the player screen.
     */
    sealed class PlayerState {
        /** No stream configured yet (initial state). */
        object Idle : PlayerState()

        /** Stream URL set; waiting for first data to arrive. */
        object Loading : PlayerState()

        /** Player has data and playback has started (or is paused). */
        object Playing : PlayerState()

        /**
         * An unrecoverable error occurred.
         * @param message Human-readable description for the error TextView.
         */
        data class Error(val message: String) : PlayerState()
    }

    private val _uiState = MutableLiveData<PlayerState>(PlayerState.Idle)

    /** Observe this from the Activity to drive the UI overlay. */
    val uiState: LiveData<PlayerState> = _uiState

    // ------------------------------------------------------------------ //
    //  Stream metadata (restored on process re-creation)
    // ------------------------------------------------------------------ //

    /**
     * URL actualmente cargada — solo respalda el guard de idempotencia de
     * [setStreamUrl]. No se persiste: tras process death la config activa
     * ([KEY_CONFIG]) ya restaura todo y un primer setStreamUrl debe pasar.
     */
    private var streamUrl: String = ""

    /**
     * Config del stream actualmente cargado. Es la fuente de verdad al recrear
     * la Activity: tras un zapping vía onNewIntent + process death, el sistema
     * vuelve a entregar el Intent ORIGINAL (setIntent no persiste), así que
     * restaurar desde el Intent reabriría el canal equivocado.
     */
    fun setActiveConfig(config: StreamConfig) {
        savedStateHandle[KEY_CONFIG] = config
    }

    fun activeConfig(): StreamConfig? = savedStateHandle[KEY_CONFIG]

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Set the HLS stream URL and transition state to [PlayerState.Loading].
     * Idempotent if the same URL is set again (avoids redundant rebuffers).
     */
    fun setStreamUrl(url: String) {
        if (streamUrl == url && _uiState.value == PlayerState.Playing) return
        streamUrl = url
        _uiState.value = PlayerState.Loading
    }

    /** Transitions to [PlayerState.Playing]. Called by the Activity's player listener. */
    fun onPlaybackReady() {
        _uiState.value = PlayerState.Playing
    }

    /**
     * Transitions to [PlayerState.Error].
     * @param message Localised error description shown to the user.
     */
    fun onPlaybackError(message: String) {
        _uiState.value = PlayerState.Error(message)
    }

    /** Resets state to [PlayerState.Loading] — called when the user taps Retry. */
    fun onRetry() {
        _uiState.value = PlayerState.Loading
    }

    // ------------------------------------------------------------------ //
    //  Position persistence (TV App Quality — resume after interruption)
    // ------------------------------------------------------------------ //

    /**
     * Persist the current playback position so it survives rotation and
     * process death.
     *
     * @param positionMs Current [androidx.media3.exoplayer.ExoPlayer.currentPosition].
     */
    fun saveCurrentPosition(positionMs: Long) {
        savedStateHandle[KEY_POSITION] = positionMs
    }

    /**
     * Retrieve the last persisted position.
     *
     * @return Saved position in milliseconds, or 0 if none stored.
     */
    fun restorePosition(): Long = savedStateHandle[KEY_POSITION] ?: 0L
}
