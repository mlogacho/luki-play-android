// tv/TvMainActivity.kt
package com.luki.play.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.luki.play.BuildConfig
import com.luki.play.R
import com.luki.play.bridge.LukiBridge
import com.luki.play.data.auth.TokenStore
import com.luki.play.databinding.ActivityTvMainBinding
import com.luki.play.util.DeviceUtils
import com.luki.play.webview.BlankPageWatchdog
import com.luki.play.webview.LukiWebViewClient
import com.luki.play.webview.WebViewConfig
import com.luki.play.webview.WebViewSupport
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/**
 * **TV WebView Activity** for Luki Play (Android TV / Google TV).
 *
 * Key differences from [com.luki.play.mobile.MobileMainActivity]:
 *  - Forwards D-Pad key events to the WebView (onKeyDown → dispatchKeyEvent);
 *    la navegación la maneja el portal web (useSpatialNavigation), no un helper
 *    nativo, para evitar dos sistemas de foco compitiendo.
 *  - Hides the system navigation bar (full-screen leanback experience).
 *  - Back-key returns to the TV launcher (no double-back toast).
 *  - WebChromeClient is stripped of unnecessary mobile-specific handlers.
 */
@AndroidEntryPoint
class TvMainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TvMainActivity"

        /**
         * JavaScript injected after every page load.
         * Maps D-Pad events forwarded from [onKeyDown] to DOM focus traversal
         * and scroll so the web UI is navigable without a touch screen.
         */
        private val TV_SCALE_JS = """
            (function() {
              if (window.__lukiTvScaleInstalled) return;
              window.__lukiTvScaleInstalled = true;

              // Ocultar texto de versión
              document.querySelectorAll('*').forEach(function(el) {
                if (el.children.length === 0 && el.textContent.trim().match(/^Versi/i)) {
                  el.style.display = 'none';
                }
              });

              // Solo zoom — no tocar el layout para no romper los clicks
              document.body.style.zoom = '0.82';
            })();
        """.trimIndent()

        /**
         * CSS del foco visual en TV: muestra claramente qué elemento tiene el foco
         * cuando navegas con el D-pad. Marca los elementos con [data-luki-focus]
         * (atributo que pone useSpatialNavigation en el portal web) con un borde
         * amarillo + sombra + escala leve, similar a Netflix / YouTube TV.
         */
        private val TV_FOCUS_CSS_JS = """
            (function() {
              if (window.__lukiTvFocusCssInstalled) return;
              window.__lukiTvFocusCssInstalled = true;

              var style = document.createElement('style');
              style.textContent = `
                [data-luki-focus] {
                  outline: 3px solid #FFB800 !important;
                  outline-offset: 4px !important;
                  transform: scale(1.08) !important;
                  box-shadow: 0 0 16px rgba(255, 184, 0, 0.5) !important;
                  transition: outline-color 0.15s, transform 0.15s, box-shadow 0.15s !important;
                }
              `;
              document.head.appendChild(style);
            })();
        """.trimIndent()

    }

    private lateinit var binding: ActivityTvMainBinding
    private val deviceUtils by lazy { DeviceUtils.createImpl(this) }
    private var watchdog: BlankPageWatchdog? = null

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflar este layout instancia el <WebView>. En TVs baratas el paquete
        // "Android System WebView" puede estar ausente, deshabilitado o a medio
        // actualizar y la instanciación lanza (la app "se instala pero se
        // cierra al abrir"). Mismo tratamiento que MobileMainActivity: pantalla
        // accionable en vez de crash.
        binding = try {
            ActivityTvMainBinding.inflate(layoutInflater)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "No se pudo inicializar el WebView del sistema")
            WebViewSupport.showFallbackScreen(this, getString(R.string.error_webview_missing))
            return
        }
        setContentView(binding.root)

        // El portal exige Chromium >= 85 para parsear su bundle; en motores más
        // viejos la página queda negra sin error. Avisar antes de cargar.
        if (WebViewSupport.isOutdated(this)) {
            val version = WebViewSupport.versionName(this) ?: "?"
            Timber.tag(TAG).w("WebView demasiado antiguo para el portal: %s", version)
            WebViewSupport.showFallbackScreen(
                this, getString(R.string.error_webview_outdated, version)
            )
            return
        }

        hideSystemUi()
        // TV App Quality: mantener la pantalla encendida — evita que el screensaver /
        // Ambient Mode de Android TV se active durante la reproducción (el contenido
        // corre en el WebView, que por sí solo no marca actividad de usuario y dejaría
        // que la TV atenúe/apague la pantalla a mitad de un programa en vivo).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupWebView()

        binding.webView.loadUrl(BuildConfig.BASE_URL)
        Timber.tag(TAG).d("TV — Loading: ${BuildConfig.BASE_URL}")
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = binding.webView

        WebViewConfig.apply(wv)
        WebViewConfig.enableThirdPartyCookies(wv)

        // En TV, respetar el viewport que inyectamos vía JS
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort      = true

        watchdog = BlankPageWatchdog(wv) { showBlankPageError() }

        wv.webViewClient = LukiWebViewClient(
            onPageStarted  = {
                watchdog?.cancel()
                showLoading(true)
            },
            onPageFinished = {
                showLoading(false)
                watchdog?.onPageFinished()
                wv.evaluateJavascript(TV_SCALE_JS, null)
                wv.evaluateJavascript(TV_FOCUS_CSS_JS, null)
                // La navegación D-pad la maneja por completo el portal web
                // (useSpatialNavigation: nav 2D + grafo del player + zapping). Antes
                // se inyectaba aquí un DPAD_JS lineal que CHOCABA con el web (dos
                // listeners keydown en captura sobre document) y causaba foco errático.
                // Se eliminó. Las teclas siguen llegando vía onKeyDown → dispatchKeyEvent.
            },
            onError = { code, desc ->
                watchdog?.cancel()
                showLoading(false)
                showError(code, desc)
            }
        )

        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                Timber.tag("LukiJS-TV").d("[${msg.messageLevel()}] ${msg.message()}")
                return true
            }
        }

        wv.addJavascriptInterface(
            LukiBridge(
                context     = applicationContext,
                deviceUtils = deviceUtils,
                tokenStore  = tokenStore,
                onLogout    = { clearSession() }
            ),
            LukiBridge.JS_INTERFACE_NAME
        )

        // TV WebViews should be focusable and handle D-Pad
        wv.isFocusable          = true
        wv.isFocusableInTouchMode = true
        wv.requestFocus()
    }

    // ── Key event routing (D-Pad → WebView) ──────────────────────────────────

    /**
     * Forwards D-Pad and centre-button key events to the WebView so the
     * injected JavaScript can handle navigation.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!::binding.isInitialized) return super.onKeyDown(keyCode, event)
        val wv = binding.webView
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                wv.dispatchKeyEvent(event ?: return super.onKeyDown(keyCode, event))
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (wv.canGoBack()) {
                    wv.goBack()
                    true
                } else {
                    false  // Let the system handle (returns to launcher)
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun showLoading(visible: Boolean) {
        binding.progressBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showError(code: Int, description: String) {
        Timber.tag(TAG).e("TV page error $code: $description")
        showErrorOverlay()
    }

    /**
     * Página que cargó pero no montó contenido (JS del portal muerto — motor
     * WebView viejo o bundle roto). Mensaje accionable en vez de fondo negro.
     */
    private fun showBlankPageError() {
        val version = WebViewSupport.versionName(this) ?: "?"
        Timber.tag(TAG).e("Página en blanco tras cargar — WebView %s", version)
        binding.tvErrorMsg.text = getString(R.string.error_blank_title) + "\n\n" +
            getString(R.string.error_blank_detail, version)
        showErrorOverlay()
    }

    private fun showErrorOverlay() {
        binding.errorLayout.visibility = View.VISIBLE
        binding.webView.visibility     = View.GONE

        binding.btnRetry.setOnClickListener {
            binding.errorLayout.visibility = View.GONE
            binding.webView.visibility     = View.VISIBLE
            binding.webView.reload()
        }
        binding.btnRetry.requestFocus()
    }

    private fun clearSession() {
        binding.webView.clearCache(true)
        binding.webView.clearHistory()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        binding.webView.loadUrl(BuildConfig.BASE_URL)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    // Si el inflate del WebView falló, binding nunca se inicializó y los
    // callbacks de ciclo de vida no deben tocarlo (crashearían igual).

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        binding.webView.onResume()
        hideSystemUi()
    }

    override fun onPause() {
        super.onPause()
        if (!::binding.isInitialized) return
        binding.webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdog?.cancel()
        if (!::binding.isInitialized) return
        binding.webView.destroy()
    }
}
