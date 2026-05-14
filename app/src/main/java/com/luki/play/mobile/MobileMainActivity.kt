// mobile/MobileMainActivity.kt
package com.luki.play.mobile

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.luki.play.BuildConfig
import com.luki.play.R
import com.luki.play.bridge.LukiBridge
import com.luki.play.databinding.ActivityMobileMainBinding
import com.luki.play.util.DeviceUtils
import com.luki.play.webview.LukiWebViewClient
import com.luki.play.webview.WebViewConfig

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge disabled: the Expo/RN-Web portal manages its own safe-area insets
        // via env(safe-area-inset-*). Enabling it in the native shell caused a rendering
        // offset on the left side of the banner (safe-area-inset-left mismatch in WebView).
        binding = ActivityMobileMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRetry()
        setupWebView()
        setupBackPress()
        binding.webView.loadUrl(BuildConfig.BASE_URL)
        Log.d(TAG, "Loading: ${BuildConfig.BASE_URL}")
    }

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
                Log.d("LukiJS", "[${message.messageLevel()}] ${message.message()}")
                return true
            }
        }

        wv.addJavascriptInterface(
            LukiBridge(
                context     = applicationContext,
                deviceUtils = deviceUtils,
                onLogout    = { clearWebViewSession() }
            ),
            LukiBridge.JS_INTERFACE_NAME
        )
    }

    // ── Back-press (double-tap to exit) ───────────────────────────────────────

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
    }

    private fun showProgress(visible: Boolean) {
        binding.progressBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showError(code: Int, description: String) {
        Log.e(TAG, "Page error $code: $description")
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
        binding.webView.loadUrl(BuildConfig.BASE_URL)
        Log.d(TAG, "WebView session cleared — reloading base URL")
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
