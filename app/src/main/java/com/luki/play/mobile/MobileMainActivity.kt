// mobile/MobileMainActivity.kt
package com.luki.play.mobile

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.luki.play.BuildConfig
import com.luki.play.R
import com.luki.play.bridge.LukiBridge
import com.luki.play.data.auth.TokenStore
import com.luki.play.databinding.ActivityMobileMainBinding
import com.luki.play.util.DeviceUtils
import com.luki.play.webview.LukiWebViewClient
import com.luki.play.webview.WebViewConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber
import kotlin.math.abs

/**
 * **Mobile WebView Activity** for Luki Play.
 *
 * Hosts the Luki web portal in a full-screen edge-to-edge WebView optimised for
 * mobile / tablet form factors. Key features:
 *
 *  - [WebViewConfig] applies all WebView settings (JS, DOM storage, cache, UA).
 *  - [LukiWebViewClient] handles page lifecycle, error routing, and URL interception.
 *  - [LukiBridge] exposes `window.LukiNative` JS interface for native<→web comms.
 *  - Back-press navigates WebView history; double-back exits the app.
 *  - Horizontal LinearProgressIndicator tracks page load progress.
 *  - Error overlay differentiates network vs server errors.
 */
@AndroidEntryPoint
class MobileMainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MobileMainActivity"
        private const val DOUBLE_BACK_INTERVAL_MS = 2000L

        private val NETWORK_ERROR_CODES = setOf(-2, -6, -7, -8)

        // Injected immediately on page start (before DOM is fully built).
        // Sets the viewport AND injects early CSS overrides before RN Web renders.
        private val EARLY_VIEWPORT_JS = """
            (function() {
                var m = document.querySelector('meta[name="viewport"]');
                if (!m) {
                    m = document.createElement('meta');
                    m.setAttribute('name', 'viewport');
                    if (document.head) document.head.appendChild(m);
                }
                if (m) m.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no');

                // Early CSS: prevent overflow + fix banner images for mobile
                if (!document.getElementById('luki-early-css')) {
                    var s = document.createElement('style');
                    s.id = 'luki-early-css';
                    s.textContent = [
                        'html,body{max-width:100vw!important;overflow-x:hidden!important}',
                        'img[src*="banner"],img[src*="slide"],img[src*="hero"],img[src*="promo"],img[src*="carousel"]{',
                        '  object-position:left center!important;',
                        '}',
                        'div[style*="background-image"]{',
                        '  background-position:left center!important;',
                        '  background-size:cover!important;',
                        '}'
                    ].join('');
                    if (document.head) document.head.appendChild(s);
                }
            })();
        """.trimIndent()

        // Injected after page load. Fixes banner images by forcing object-position
        // and background-position to show the left side (where text/CTAs are).
        // Puente JS para recibir swipes detectados por el GestureDetector nativo.
        // Expone window.__lukiNativeSwipe('next'|'prev') que el player web escucha
        // via window.__lukiSwipeHandler, registrado por el player al montarse.
        private val SWIPE_BRIDGE_JS = """
            (function() {
                window.__lukiNativeSwipe = function(direction) {
                    if (typeof window.__lukiSwipeHandler === 'function') {
                        window.__lukiSwipeHandler(direction);
                    }
                };
            })();
        """.trimIndent()

        private val MOBILE_RESPONSIVE_JS = """
            (function() {
                if (document.getElementById('luki-fix-applied')) return;
                var marker = document.createElement('div');
                marker.id = 'luki-fix-applied';
                marker.style.display = 'none';
                document.body.appendChild(marker);

                function fixBannerImages() {
                    var vpw = window.innerWidth;

                    // Fix all large images (likely banner/hero images)
                    var imgs = document.querySelectorAll('img');
                    for (var i = 0; i < imgs.length; i++) {
                        var img = imgs[i];
                        var r = img.getBoundingClientRect();
                        // Target images that span full width and are tall (banner-like)
                        if (r.width >= vpw * 0.9 && r.height > 150) {
                            img.style.objectPosition = 'left center';
                            img.style.objectFit = 'cover';
                            console.log('[LukiFix] fixed img w=' + Math.round(r.width) + ' h=' + Math.round(r.height) + ' src=' + (img.src||'').substring(0,60));
                        }
                    }

                    // Fix divs with background-image (alternative banner implementation)
                    var all = document.querySelectorAll('div');
                    for (var j = 0; j < all.length; j++) {
                        var div = all[j];
                        var cs = window.getComputedStyle(div);
                        if (cs.backgroundImage && cs.backgroundImage !== 'none') {
                            var rd = div.getBoundingClientRect();
                            if (rd.width >= vpw * 0.9 && rd.height > 150) {
                                div.style.backgroundPosition = 'left center';
                                console.log('[LukiFix] fixed bg-div w=' + Math.round(rd.width) + ' h=' + Math.round(rd.height));
                            }
                        }
                    }
                }

                // Run multiple times as banner images load asynchronously
                fixBannerImages();
                setTimeout(fixBannerImages, 1000);
                setTimeout(fixBannerImages, 3000);
                setTimeout(fixBannerImages, 6000);

                // Re-apply on DOM changes (React re-renders, carousel slides)
                var timer = null;
                new MutationObserver(function() {
                    if (timer) clearTimeout(timer);
                    timer = setTimeout(fixBannerImages, 300);
                }).observe(document.body, { childList: true, subtree: true });
            })();
        """.trimIndent()
    }

    private lateinit var binding: ActivityMobileMainBinding
    private var lastBackPressTime = 0L
    private val deviceUtils by lazy { DeviceUtils.createImpl(this) }

    // ── HTML5 fullscreen state (reproductor web a pantalla completa) ──────────
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge disabled: the Expo/RN-Web portal manages its own safe-area insets
        // via env(safe-area-inset-*). Enabling it in the native shell caused a rendering
        // offset on the left side of the banner (safe-area-inset-left mismatch in WebView).
        //
        // Inflar este layout instancia el <WebView>, lo que obliga al sistema a cargar el
        // paquete "Android System WebView". En algunos equipos antiguos (varios MIUI/Redmi)
        // ese paquete viene deshabilitado o a medio actualizar y la instanciación lanza
        // una excepción (MissingWebViewPackageException / Resources$NotFoundException),
        // haciendo que la app "se instale pero se cierre al abrir". En vez de crashear,
        // mostramos una pantalla accionable. En equipos sanos el try pasa de largo y el
        // comportamiento es idéntico al actual.
        binding = try {
            ActivityMobileMainBinding.inflate(layoutInflater)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "No se pudo inicializar el WebView del sistema")
            showWebViewUnavailable()
            return
        }
        setContentView(binding.root)
        setupRetry()
        setupWebView()
        setupBackPress()
        binding.webView.loadUrl(mobileLoginUrl())
        Timber.tag(TAG).d("Loading: ${mobileLoginUrl()}")
    }

    /**
     * Pantalla de respaldo cuando el WebView del sistema no está disponible
     * (deshabilitado o a medio actualizar en algunos MIUI/equipos antiguos).
     * Evita el cierre inmediato y le dice al usuario cómo solucionarlo. No depende
     * del binding (que es justo lo que falló), se construye en código.
     */
    private fun showWebViewUnavailable() {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val message = android.widget.TextView(this).apply {
            text = "No se pudo iniciar el navegador interno.\n\n" +
                "Actualiza “Android System WebView” y Google Chrome desde " +
                "Play Store y luego vuelve a abrir Luki Play."
            setPadding(pad, pad, pad, pad)
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#240046"))
            addView(
                message,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(root)
    }

    /**
     * URL de login para móvil. A diferencia de TV (que carga la pantalla de
     * activación por QR en BASE_URL), el móvil abre el login con formulario/URL.
     */
    private fun mobileLoginUrl(): String =
        BuildConfig.BASE_URL.trimEnd('/') + "/login"

    // ── Edge-to-edge insets ───────────────────────────────────────────────────

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mobileRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val hPad = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
            val vPad = resources.getDimensionPixelSize(R.dimen.space_lg)
            // Apply safe-area padding only to the error overlay; WebView fills edge-to-edge
            binding.errorLayout.setPadding(
                bars.left + hPad,
                bars.top + vPad,
                bars.right + hPad,
                bars.bottom + vPad
            )
            insets
        }
    }

    // ── Retry button (wired once, not per-error) ──────────────────────────────

    private fun setupRetry() {
        binding.btnRetry.setOnClickListener {
            binding.errorLayout.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
            binding.webView.reload()
        }
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = binding.webView
        wv.setBackgroundColor(android.graphics.Color.parseColor("#240046"))
        WebViewConfig.apply(wv)
        WebViewConfig.enableThirdPartyCookies(wv)

        wv.webViewClient = LukiWebViewClient(
            onPageStarted  = {
                showProgress(true)
                // Inject viewport ASAP so the page renders at device width from the start
                binding.webView.evaluateJavascript(EARLY_VIEWPORT_JS, null)
            },
            onPageFinished = {
                showProgress(false)
                injectResponsiveFixes()
            },
            onError        = { code, desc ->
                showProgress(false)
                showError(code, desc)
            }
        )

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress == 100) showProgress(false)
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Timber.tag("LukiJS").d("[${message.messageLevel()}] ${message.message()}")
                return true
            }

            // ── HTML5 fullscreen (requestFullscreen del <video>/reproductor web) ──
            // Sin estos callbacks, requestFullscreen() desde la web es un no-op en
            // un WebView. Aquí montamos la custom view a pantalla completa, ocultamos
            // el WebView y forzamos landscape mientras dura; al salir restauramos.
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                enterWebFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                exitWebFullscreen()
            }
        }

        wv.addJavascriptInterface(
            LukiBridge(
                context     = applicationContext,
                deviceUtils = deviceUtils,
                tokenStore  = tokenStore,
                onLogout    = { clearWebViewSession() }
            ),
            LukiBridge.JS_INTERFACE_NAME
        )

        setupSwipeGesture(wv)
    }

    // ── Swipe nativo para zapping de canales ──────────────────────────────────
    // El PanResponder de React Native Web pierde la carrera contra el sistema
    // de gestos del WebView Android. Detectamos el swipe horizontal aquí, en
    // capa nativa, y lo notificamos al JS vía evaluateJavascript.

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGesture(wv: WebView) {
        val gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                private val SWIPE_MIN_DISTANCE = 80   // px mínimos horizontales
                private val SWIPE_MAX_OFFPATH  = 100  // px máximos verticales

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val e1 = e1 ?: return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    if (abs(dy) > SWIPE_MAX_OFFPATH) return false
                    if (abs(dx) < SWIPE_MIN_DISTANCE) return false

                    val direction = if (dx < 0) "next" else "prev"
                    Timber.tag(TAG).d("Swipe nativo detectado: $direction (dx=$dx)")
                    wv.post {
                        wv.evaluateJavascript(
                            "window.__lukiNativeSwipe && window.__lukiNativeSwipe('$direction');",
                            null
                        )
                    }
                    return true
                }
            }
        )

        wv.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            // Retornar false para que el WebView también procese el evento
            // (scroll, tap, etc.) — el GestureDetector solo actúa en fling.
            v.onTouchEvent(event)
        }
    }

    // ── HTML5 fullscreen del reproductor web ──────────────────────────────────

    /**
     * Llamado por [WebChromeClient.onShowCustomView] cuando la web invoca
     * `requestFullscreen()` (p. ej. al girar el teléfono en el player). Monta la
     * vista del video a pantalla completa sobre todo el contenido, oculta el
     * WebView y fuerza orientación horizontal mientras dura.
     */
    private fun enterWebFullscreen(view: View, callback: CustomViewCallback) {
        // Si ya hay una custom view, descártala (contrato de WebChromeClient).
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback

        val decor = window.decorView as ViewGroup
        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        decor.addView(
            fullscreenContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        binding.webView.visibility = View.GONE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Pantalla completa inmersiva + horizontal mientras dura el video.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    /** Llamado por [WebChromeClient.onHideCustomView] al salir de fullscreen. */
    private fun exitWebFullscreen() {
        val view = customView ?: return
        val decor = window.decorView as ViewGroup
        fullscreenContainer?.let {
            it.removeView(view)
            decor.removeView(it)
        }
        fullscreenContainer = null
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null

        binding.webView.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Volver al sensor libre: el usuario puede seguir girando, pero al cerrar
        // el video la app no queda trabada en horizontal.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    // ── Back-press (double-tap to exit) ───────────────────────────────────────

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Si el reproductor web está en fullscreen, el back primero sale
                // del fullscreen (no del WebView ni de la app).
                if (customView != null) {
                    binding.webView.evaluateJavascript(
                        "(function(){if(document.fullscreenElement&&document.exitFullscreen)document.exitFullscreen();})();",
                        null
                    )
                    exitWebFullscreen()
                    return
                }
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < DOUBLE_BACK_INTERVAL_MS) {
                    finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(
                        this@MobileMainActivity,
                        getString(R.string.exit_app),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun injectResponsiveFixes() {
        binding.webView.evaluateJavascript(MOBILE_RESPONSIVE_JS, null)
        binding.webView.evaluateJavascript(SWIPE_BRIDGE_JS, null)
    }

    private fun showProgress(visible: Boolean) {
        binding.progressBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showError(code: Int, description: String) {
        Timber.tag(TAG).e("Page error $code: $description")
        val (title, detail) = if (code in NETWORK_ERROR_CODES) {
            getString(R.string.error_no_internet) to getString(R.string.error_no_internet_detail)
        } else {
            getString(R.string.error_server) to getString(R.string.error_server_detail)
        }
        binding.tvErrorMsg.text = title
        binding.tvErrorDetail.text = detail
        binding.errorLayout.visibility = View.VISIBLE
        binding.webView.visibility = View.GONE
    }

    // ── Session management ────────────────────────────────────────────────────

    private fun clearWebViewSession() {
        binding.webView.clearCache(true)
        binding.webView.clearHistory()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        binding.webView.loadUrl(mobileLoginUrl())
        Timber.tag(TAG).d("WebView session cleared — reloading login URL")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
    }
}
