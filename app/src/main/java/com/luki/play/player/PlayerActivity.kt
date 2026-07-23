// player/PlayerActivity.kt
package com.luki.play.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import com.luki.play.R
import com.luki.play.data.streams.StreamSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
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
 * Widevine está cableado vía WidevineProvider y se activa cuando
 * [StreamConfig.hasDrm] es true. BridgeMessage.PlayStream ya parsea los
 * campos DRM del JSON, pero LukiBridge.playStream()/dispatch() hoy NO los
 * reenvían al construir StreamConfig — ese es el hueco a cerrar cuando se
 * active DRM; mientras tanto todo el contenido llega in-the-clear.
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
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity(), LukiPlayerManager.PlayerCallback {

    /** Sesión de stream — solo se usa si la config dice que somos su dueño. */
    @Inject lateinit var streamSessionManager: StreamSessionManager

    /** Catálogo (Room) — da el orden de zapping sin pasarlo por la navegación. */
    @Inject lateinit var channelsRepository: com.luki.play.data.catalog.ChannelsRepository

    /** Para resolver el stream del canal al que se zapea. */
    @Inject lateinit var catalogApi: com.luki.play.data.catalog.api.CatalogApi

    /** true si abrimos sesión en este reproductor; decide si hay que cerrarla. */
    private var managingStreamSession = false

    /** Distingue el primer onStart del que sigue a un paso por background. */
    private var wasInBackground = false

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
         * Instante ([SystemClock.elapsedRealtime], long) en que el bridge emitió
         * el stop. El patrón web stopStream()+playStream(B) llega en carrera y
         * el broadcast puede aterrizar DESPUÉS de que onNewIntent cargó el canal
         * nuevo; comparar instantes de emisión (bridge y Activity viven en el
         * mismo proceso, así que elapsedRealtime es comparable) descarta solo
         * los stops emitidos ANTES del load vigente — un stop legítimo posterior
         * al load siempre cierra, sin ventana arbitraria que pueda tragárselo.
         */
        const val EXTRA_STOP_ISSUED_AT = "extra_stop_issued_at_elapsed_ms"

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

    /** Marca del último load(); ver [EXTRA_STOP_ISSUED_AT]. */
    private var lastLoadAtMs = 0L

    // ------------------------------------------------------------------ //
    //  BroadcastReceiver — remote stop (from LukiBridge.stopStream)
    // ------------------------------------------------------------------ //

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_STOP_PLAYBACK) return
            // Sin extra (emisor desconocido) se honra el stop: mejor cerrar de
            // más que dejar el player imposible de cerrar desde la web.
            val issuedAtMs = intent.getLongExtra(EXTRA_STOP_ISSUED_AT, Long.MAX_VALUE)
            if (issuedAtMs < lastLoadAtMs) {
                // Stop rezagado del patrón stopStream()+playStream(): fue emitido
                // ANTES del load vigente e iba dirigido al canal anterior.
                return
            }
            finish()
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
        setupControls()
        observeState()
        setupRetryButton()
    }

    override fun onStart() {
        super.onStart()
        // Al volver de background: heartbeat inmediato y, si el lease caducó
        // mientras la app no estaba en primer plano, se reabre. NO se cierra
        // la sesión al irse a background — el portal comprobó que hacerlo
        // cortaba la reproducción a quien solo apagaba la pantalla un momento.
        val channelId = activeStreamSessionChannelId() ?: return
        if (!wasInBackground) return
        wasInBackground = false
        lifecycleScope.launch { streamSessionManager.onForeground(channelId) }
    }

    override fun onResume() {
        super.onResume()
        playerManager?.resume()
    }

    override fun onPause() {
        super.onPause()
        playerManager?.saveAndPause()
    }

    override fun onStop() {
        super.onStop()
        wasInBackground = true
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
        // Libera el cupo del plan al instante en vez de esperar a que el
        // backend caduque el lease por falta de heartbeat.
        if (managingStreamSession) streamSessionManager.close()
    }

    /**
     * Id del canal si ESTE reproductor es dueño de la sesión de stream.
     *
     * Devuelve null cuando el reproductor lo lanzó el bridge: ahí la sesión
     * ya la abrió el portal y tocarla gastaría un segundo cupo.
     */
    private fun activeStreamSessionChannelId(): String? {
        val config = viewModel.activeConfig() ?: readConfig(intent) ?: return null
        return config.channelId?.takeIf { config.managesStreamSession() }
    }

    // ------------------------------------------------------------------ //
    //  Controles del reproductor (luki_player_controls.xml)
    // ------------------------------------------------------------------ //

    /** true cuando el video llena la pantalla (recorta) en vez de ajustarse. */
    private var videoFilled = false

    /** Silencio actual; se restaura el volumen anterior al reactivar. */
    private var muted = false

    /**
     * Cablea los botones propios del layout de controles. El play/pause lo
     * maneja Media3 (id `exo_play_pause`); el resto son de la app:
     *
     *  · Volver → cierra el reproductor (antes solo salías con el gesto atrás).
     *  · Silenciar y Ajustar/Llenar → como los del portal.
     *  · Canal anterior/siguiente (zapping) → solo en el camino nativo, donde
     *    hay un canal identificado; desde el bridge se ocultan (el portal lleva
     *    su propio zapping en el WebView).
     */
    private fun setupControls() {
        val pv = binding.playerView

        pv.findViewById<android.widget.ImageButton>(R.id.btnPlayerBack)
            ?.setOnClickListener { finish() }

        pv.findViewById<android.widget.TextView>(R.id.tvPlayerTitle)?.text =
            (viewModel.activeConfig() ?: readConfig(intent))?.title.orEmpty()

        pv.findViewById<android.widget.ImageButton>(R.id.btnMute)
            ?.setOnClickListener { toggleMute(it as android.widget.ImageButton) }

        pv.findViewById<android.widget.ImageButton>(R.id.btnFit)
            ?.setOnClickListener { toggleFit(it as android.widget.ImageButton) }

        val canZap = activeStreamSessionChannelId() != null
        val prev = pv.findViewById<android.widget.ImageButton>(R.id.btnPrevChannel)
        val next = pv.findViewById<android.widget.ImageButton>(R.id.btnNextChannel)
        prev?.visibility = if (canZap) View.VISIBLE else View.GONE
        next?.visibility = if (canZap) View.VISIBLE else View.GONE
        prev?.setOnClickListener { zap(-1) }
        next?.setOnClickListener { zap(+1) }
    }

    private fun toggleMute(button: android.widget.ImageButton) {
        muted = !muted
        playerManager?.player?.volume = if (muted) 0f else 1f
        button.setImageResource(
            if (muted) R.drawable.ic_player_volume_off else R.drawable.ic_player_volume
        )
    }

    private fun toggleFit(button: android.widget.ImageButton) {
        videoFilled = !videoFilled
        binding.playerView.resizeMode =
            if (videoFilled) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        button.setImageResource(
            if (videoFilled) R.drawable.ic_player_fill else R.drawable.ic_player_fit
        )
    }

    // ------------------------------------------------------------------ //
    //  Zapping — saltar de canal siguiendo el orden del Home
    // ------------------------------------------------------------------ //

    /** Orden de zapping: mismo que ve el usuario en Home (categoría, luego canal). */
    private var zapOrder: List<com.luki.play.data.catalog.domain.Channel>? = null
    private var zapInFlight = false

    /**
     * Salta [delta] canales (−1 anterior, +1 siguiente) siguiendo el orden del
     * Home. Resuelve el stream del vecino y recarga; la sesión de stream la
     * reutiliza [StreamSessionManager.open] (upsert por dispositivo), así que
     * zapear no consume cupos extra.
     */
    private fun zap(delta: Int) {
        if (zapInFlight) return
        val currentId = activeStreamSessionChannelId() ?: return
        zapInFlight = true
        lifecycleScope.launch {
            try {
                val order = zapOrder ?: buildZapOrder().also { zapOrder = it }
                val index = order.indexOfFirst { it.id == currentId }
                if (index < 0 || order.size < 2) return@launch
                // Circular: del último salta al primero y viceversa.
                val target = order[((index + delta) % order.size + order.size) % order.size]

                val dto = catalogApi.getChannelStream(target.id)
                val config = StreamConfig(
                    url               = dto.streamUrl,
                    title             = target.name,
                    manifestType      = manifestTypeOf(dto.streamUrl),
                    channelId         = target.id,
                    ownsStreamSession = true,
                )
                binding.playerView.findViewById<android.widget.TextView>(R.id.tvPlayerTitle)
                    ?.text = target.name
                startPlayback(config, resetSavedPosition = true)
            } catch (e: Exception) {
                Timber.tag("PlayerActivity").w(e, "zapping falló")
            } finally {
                zapInFlight = false
            }
        }
    }

    /**
     * Lista plana de canales en el MISMO orden del Home: agrupada por categoría,
     * categorías ordenadas alfabéticamente. Así "siguiente" lleva al canal que
     * el usuario esperaría tras el actual.
     */
    private suspend fun buildZapOrder(): List<com.luki.play.data.catalog.domain.Channel> =
        channelsRepository.observeChannels().first()
            .groupBy { it.category }
            .toSortedMap()
            .values
            .flatten()

    private fun manifestTypeOf(url: String): ManifestType {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") -> ManifestType.HLS
            path.endsWith(".mpd")  -> ManifestType.DASH
            else                   -> ManifestType.OTHER
        }
    }

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    private fun initPlayer() {
        // Tras process death el sistema puede reentregar el Intent ORIGINAL
        // aunque hubo zapping (setIntent no persiste): la config activa del
        // SavedStateHandle es la fuente de verdad; el Intent es el fallback
        // del primer arranque.
        val config = viewModel.activeConfig() ?: readConfig(intent)
        if (config == null) {
            viewModel.onPlaybackError(getString(R.string.player_error_no_url))
            return
        }
        startPlayback(config, resetSavedPosition = false)
    }

    /**
     * La Activity es singleTop: si la web hace zapping (playStream con el
     * player ya abierto) el Intent nuevo llega aquí en vez de crear otra
     * instancia. Se reutilizan player y MediaSession — cambio de canal más
     * rápido y sin colisión de session IDs.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Validar ANTES de setIntent: un intent inválido no debe reemplazar
        // el último intent bueno (retry/recreación dependen de él).
        val config = readConfig(intent) ?: return
        setIntent(intent)
        // La posición guardada pertenece al canal anterior; no aplica al nuevo.
        startPlayback(config, resetSavedPosition = true)
    }

    /**
     * Camino único de arranque para initPlayer / onNewIntent / retry.
     * Persiste la config activa y delega URL/estado en el manager: load()
     * es el único dueño de setStreamUrl y de la transición a Loading.
     */
    private fun startPlayback(config: StreamConfig, resetSavedPosition: Boolean) {
        if (resetSavedPosition) viewModel.saveCurrentPosition(0L)
        viewModel.setActiveConfig(config)
        lastLoadAtMs = SystemClock.elapsedRealtime()
        openStreamSessionIfNeeded(config)
        (playerManager ?: createManager()).load(config)
    }

    /**
     * Abre la sesión que aplica el tope de streams simultáneos del plan.
     *
     * Solo cuando este reproductor es su dueño (camino nativo); si vino del
     * bridge, la sesión ya la abrió el portal. Se lanza en paralelo a la carga
     * igual que el portal: si el backend responde 429 se cierra el
     * reproductor; cualquier otro fallo no interrumpe la reproducción.
     */
    private fun openStreamSessionIfNeeded(config: StreamConfig) {
        val channelId = config.channelId
        if (!config.managesStreamSession() || channelId == null) return
        managingStreamSession = true
        lifecycleScope.launch {
            when (streamSessionManager.open(channelId)) {
                is StreamSessionManager.OpenResult.LimitReached -> finish()
                is StreamSessionManager.OpenResult.Failed,
                is StreamSessionManager.OpenResult.Started -> Unit
            }
        }
    }

    /** Parsea el [StreamConfig] del Intent; null si falta o la URL está vacía. */
    private fun readConfig(intent: Intent): StreamConfig? =
        IntentCompat.getParcelableExtra(intent, EXTRA_STREAM_CONFIG, StreamConfig::class.java)
            ?.takeIf { it.url.isNotBlank() }

    private fun createManager(): LukiPlayerManager {
        val manager = LukiPlayerManager(this, viewModel, this)
        playerManager = manager
        binding.playerView.player = manager.player
        return manager
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
            val config = viewModel.activeConfig() ?: readConfig(intent)
            if (config == null) {
                // Sin config válida no hay nada que reintentar; cerrar evita
                // dejar al usuario en un spinner infinito.
                finish()
                return@setOnClickListener
            }
            viewModel.onRetry()
            startPlayback(config, resetSavedPosition = false)
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
