// tv/TvMainActivity.kt
package com.luki.play.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.luki.play.BuildConfig
import com.luki.play.R
import com.luki.play.bridge.LukiBridge
import com.luki.play.databinding.ActivityTvMainBinding
import com.luki.play.util.DeviceUtils
import com.luki.play.webview.LukiWebViewClient
import com.luki.play.webview.WebViewConfig

/**
 * **TV WebView Activity** for Luki Play (Android TV / Google TV).
 *
 * Key differences from [com.luki.play.mobile.MobileMainActivity]:
 *  - Injects a JS helper that maps D-Pad key events to scroll / focus
 *    commands so the web portal works without a touchscreen.
 *  - Hides the system navigation bar (full-screen leanback experience).
 *  - Back-key returns to the TV launcher (no double-back toast).
 *  - WebChromeClient is stripped of unnecessary mobile-specific handlers.
 */
class TvMainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TvMainActivity"

        /**
         * JavaScript injected after every page load.
         * Maps D-Pad events forwarded from [onKeyDown] to DOM focus traversal
         * and scroll so the web UI is navigable without a touch screen.
         */
        private val DPAD_JS = """
            (function() {
              if (window.__lukiDpadInstalled) return;
              window.__lukiDpadInstalled = true;

              document.addEventListener('keydown', function(e) {
                var scrollAmt = 200;
                switch(e.key) {
                  case 'ArrowUp':    window.scrollBy(0, -scrollAmt); break;
                  case 'ArrowDown':  window.scrollBy(0,  scrollAmt); break;
                  case 'ArrowLeft':  window.scrollBy(-scrollAmt, 0); break;
                  case 'ArrowRight': window.scrollBy( scrollAmt, 0); break;
                  case 'Enter':
                    var el = document.activeElement;
                    if (el) el.click();
                    break;
                }
              }, true);
            })();
        """.trimIndent()
    }

    private lateinit var binding: ActivityTvMainBinding
    private val deviceUtils by lazy { DeviceUtils.createImpl(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTvMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUi()
        setupWebView()

        binding.webView.loadUrl(BuildConfig.BASE_URL)
        Log.d(TAG, "TV — Loading: ${BuildConfig.BASE_URL}")
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = binding.webView

        WebViewConfig.apply(wv)
        WebViewConfig.enableThirdPartyCookies(wv)

        wv.webViewClient = LukiWebViewClient(
            onPageStarted  = { showLoading(true) },
            onPageFinished = {
                showLoading(false)
                // Inject D-Pad navigation helper after every page load
                wv.evaluateJavascript(DPAD_JS, null)
            },
            onError = { code, desc ->
                showLoading(false)
                showError(code, desc)
            }
        )

        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                Log.d("LukiJS-TV", "[${msg.messageLevel()}] ${msg.message()}")
                return true
            }
        }

        wv.addJavascriptInterface(
            LukiBridge(
                context     = applicationContext,
                deviceUtils = deviceUtils,
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
        Log.e(TAG, "TV page error $code: $description")
        binding.errorLayout.visibility = View.VISIBLE
        binding.webView.visibility     = View.GONE

        binding.btnRetry.setOnClickListener {
            binding.errorLayout.visibility = View.GONE
            binding.webView.visibility     = View.VISIBLE
            binding.webView.reload()
        }
    }

    private fun clearSession() {
        binding.webView.clearCache(true)
        binding.webView.clearHistory()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        binding.webView.loadUrl(BuildConfig.BASE_URL)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        hideSystemUi()
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
