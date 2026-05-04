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
 * - Holds [streamUrl] and [title] extracted from the Intent/SavedState.
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
        const val KEY_STREAM_URL = "stream_url"
        const val KEY_TITLE      = "title"
        const val KEY_POSITION   = "playback_position_ms"
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

    /** HLS manifest URL set by [setStreamUrl]. */
    val streamUrl: String
        get() = savedStateHandle[KEY_STREAM_URL] ?: ""

    /** Display title set by [setTitle]. */
    val title: String
        get() = savedStateHandle[KEY_TITLE] ?: ""

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Set the HLS stream URL and transition state to [PlayerState.Loading].
     * Idempotent if the same URL is set again (avoids redundant rebuffers).
     */
    fun setStreamUrl(url: String) {
        if (streamUrl == url && _uiState.value == PlayerState.Playing) return
        savedStateHandle[KEY_STREAM_URL] = url
        _uiState.value = PlayerState.Loading
    }

    /** Update the display title (does not affect playback state). */
    fun setTitle(title: String) {
        savedStateHandle[KEY_TITLE] = title
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
