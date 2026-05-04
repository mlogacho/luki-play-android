// mobile/MobileMainActivity.kt
package com.luki.play.mobile

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
 * Hosts the Luki web portal in a full-screen WebView optimised for
 * mobile / tablet form factors. Key features:
 *
 *  - [WebViewConfig] applies all WebView settings (JS, DOM storage, cache, UA).
 *  - [LukiWebViewClient] handles page lifecycle, error routing, and URL interception.
 *  - [LukiBridge] exposes `window.LukiNative` JS interface for native<→web comms.
 *  - Back-press navigates WebView history; double-back exits the app.
 *  - Loading indicator hides once the page finishes loading.
 */
class MobileMainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MobileMainActivity"
    }

    private lateinit var binding: ActivityMobileMainBinding

    // Timestamp of the last back-press for double-back-to-exit detection
    private var lastBackPressTime = 0L

    // DeviceUtils implementation satisfying DeviceUtilsContract
    private val deviceUtils by lazy { DeviceUtils.createImpl(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMobileMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupBackPress()

        // Start loading the web app
        binding.webView.loadUrl(BuildConfig.BASE_URL)
        Log.d(TAG, "Loading: ${BuildConfig.BASE_URL}")
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = binding.webView

        // 1. Apply standard Luki WebView settings
        WebViewConfig.apply(wv)
        WebViewConfig.enableThirdPartyCookies(wv)

        // 2. Attach our custom WebViewClient (handles page events & errors)
        wv.webViewClient = LukiWebViewClient(
            onPageStarted  = { showLoading(true) },
            onPageFinished = { showLoading(false) },
            onError        = { code, desc ->
                showLoading(false)
                showError(code, desc)
            }
        )

        // 3. Attach WebChromeClient for JS dialogs & console messages
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                // Could drive a ProgressBar here if desired
            }

            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                Log.d("LukiJS", "[${message.messageLevel()}] ${message.message()}")
                return true
            }
        }

        // 4. Register the native bridge as window.LukiNative
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
                if (now - lastBackPressTime < 2000) {
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

    private fun showLoading(visible: Boolean) {
        binding.progressBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showError(code: Int, description: String) {
        Log.e(TAG, "Page error $code: $description")
        // Show the retry overlay; hide the WebView
        binding.errorLayout.visibility = View.VISIBLE
        binding.webView.visibility     = View.GONE

        binding.btnRetry.setOnClickListener {
            binding.errorLayout.visibility = View.GONE
            binding.webView.visibility     = View.VISIBLE
            binding.webView.reload()
        }
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
