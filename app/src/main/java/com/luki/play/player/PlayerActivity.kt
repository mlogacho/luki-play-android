// player/PlayerActivity.kt
package com.luki.play.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.luki.play.R
import com.luki.play.databinding.ActivityPlayerBinding

/**
 * Full-screen HLS playback Activity powered by Media3 ExoPlayer.
 *
 * Launch contract
 * ───────────────
 * Use [newIntent] to build the Intent with a [StreamConfig] extra:
 * ```
 * startActivity(PlayerActivity.newIntent(context, config))
 * ```
 *
 * Stream source for testing
 * ─────────────────────────
 * https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
 *
 * DRM
 * ───
 * ⚠️ DRM Widevine no activado. Para futuro: configurar
 * DefaultDrmSessionManager si drmToken != null.
 *
 * TV App Quality checklist (handled here)
 * ────────────────────────────────────────
 * ✅ Immersive full-screen (sticky immersive flags).
 * ✅ Keeps screen on while playing (android:keepScreenOn in layout).
 * ✅ configChanges in Manifest → no Activity restart on rotation.
 * ✅ Position persisted across config changes via SavedStateHandle.
 * ✅ BroadcastReceiver listens for ACTION_STOP_PLAYBACK (from LukiBridge).
 * ✅ onUserLeaveHint pauses playback gracefully.
 */
class PlayerActivity : AppCompatActivity(), LukiPlayerManager.PlayerCallback {

    // ------------------------------------------------------------------ //
    //  Companion
    // ------------------------------------------------------------------ //

    companion object {
        private const val EXTRA_STREAM_CONFIG = "extra_stream_config"

        /**
         * Action sent by [com.luki.play.bridge.LukiBridge.stopStream] to
         * request the player to finish itself from outside.
         */
        const val ACTION_STOP_PLAYBACK = "com.luki.play.ACTION_STOP_PLAYBACK"

        /**
         * Factory method — always use this to create the launch Intent.
         *
         * @param context Application or Activity context.
         * @param config  Stream parameters (URL required, title and poster optional).
         * @return        Configured Intent ready to pass to [startActivity].
         */
        fun newIntent(context: Context, config: StreamConfig): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_STREAM_CONFIG, config)
    }

    // ------------------------------------------------------------------ //
    //  Dependencies
    // ------------------------------------------------------------------ //

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()
    private var playerManager: LukiPlayerManager? = null

    // ------------------------------------------------------------------ //
    //  BroadcastReceiver — remote stop (from LukiBridge.stopStream)
    // ------------------------------------------------------------------ //

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_PLAYBACK) finish()
        }
    }

    // ------------------------------------------------------------------ //
    //  Lifecycle
    // ------------------------------------------------------------------ //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyFullscreen()
        registerStopReceiver()
        initPlayer()
        observeState()
        setupRetryButton()
    }

    override fun onResume() {
        super.onResume()
        playerManager?.resume()
    }

    override fun onPause() {
        super.onPause()
        playerManager?.saveAndPause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Pause gracefully when the user navigates away (Home button, Recents, etc.)
        playerManager?.saveAndPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager?.release()
        playerManager = null
        unregisterReceiver(stopReceiver)
    }

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    private fun initPlayer() {
        val config: StreamConfig? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_STREAM_CONFIG, StreamConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_STREAM_CONFIG)
        }

        if (config == null || config.url.isBlank()) {
            viewModel.onPlaybackError(getString(R.string.player_error_no_url))
            return
        }

        val manager = LukiPlayerManager(this, viewModel, this)
        playerManager = manager
        binding.playerView.player = manager.player

        // Restore title into ViewModel (survives rotation via SavedStateHandle)
        viewModel.setTitle(config.title)
        viewModel.setStreamUrl(config.url)

        manager.load(config)
    }

    private fun observeState() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is PlayerViewModel.PlayerState.Idle -> {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.errorMessage.visibility     = View.GONE
                    binding.retryButton.visibility      = View.GONE
                }
                is PlayerViewModel.PlayerState.Loading -> {
                    binding.loadingIndicator.visibility = View.VISIBLE
                    binding.errorMessage.visibility     = View.GONE
                    binding.retryButton.visibility      = View.GONE
                }
                is PlayerViewModel.PlayerState.Playing -> {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.errorMessage.visibility     = View.GONE
                    binding.retryButton.visibility      = View.GONE
                }
                is PlayerViewModel.PlayerState.Error -> {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.errorMessage.visibility     = View.VISIBLE
                    binding.errorMessage.text           = state.message
                    binding.retryButton.visibility      = View.VISIBLE
                }
            }
        }
    }

    private fun setupRetryButton() {
        binding.retryButton.setOnClickListener {
            viewModel.onRetry()
            playerManager?.let { mgr ->
                val config: StreamConfig? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_STREAM_CONFIG, StreamConfig::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_STREAM_CONFIG)
                }
                config?.let { mgr.load(it) }
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  LukiPlayerManager.PlayerCallback
    // ------------------------------------------------------------------ //

    override fun onReady() {
        // PlayerState already set to Playing by LukiPlayerManager → ViewModel
    }

    override fun onBuffering() {
        // ExoPlayer's own buffering indicator on PlayerView handles this;
        // the loadingIndicator ProgressBar is controlled by PlayerState.
    }

    override fun onEnded() {
        finish()
    }

    override fun onError(message: String) {
        // ViewModel already updated via onPlaybackError; UI driven by observeState()
    }

    // ------------------------------------------------------------------ //
    //  System UI — Immersive fullscreen
    // ------------------------------------------------------------------ //

    private fun applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.systemBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    // ------------------------------------------------------------------ //
    //  BroadcastReceiver helpers
    // ------------------------------------------------------------------ //

    private fun registerStopReceiver() {
        val filter = IntentFilter(ACTION_STOP_PLAYBACK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }
    }
}
