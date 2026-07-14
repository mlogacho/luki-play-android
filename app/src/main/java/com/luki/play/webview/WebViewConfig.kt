// webview/WebViewConfig.kt
package com.luki.play.webview

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.luki.play.BuildConfig

/**
 * Configures a [WebView] instance with the settings required for Luki Play.
 *
 * Call [apply] once, right after the WebView is inflated and before loading any URL.
 * All methods are side-effect free and return [Unit] so they can be chained easily.
 */
object WebViewConfig {

    /**
     * Applies the full Luki Play configuration to [webView].
     *
     * @param webView   The target WebView (must be attached to a window).
     * @param userAgent Optional custom UA suffix appended to the default UA string.
     *                  Defaults to "LukiPlay-Android/<versionName>".
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView, userAgent: String? = null) {
        configureSettings(webView.settings, webView, userAgent)
        configureCookies()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings(
        settings: WebSettings,
        webView: WebView,
        customUserAgent: String?
    ) {
        // ── JavaScript ───────────────────────────────────────────────────────
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = false

        // ── DOM Storage / Databases ──────────────────────────────────────────
        settings.domStorageEnabled = true

        // ── Media playback ───────────────────────────────────────────────────
        // Allow autoplay of inline video (required for HLS preview thumbnails)
        settings.mediaPlaybackRequiresUserGesture = false

        // ── Viewport / Zoom ──────────────────────────────────────────────────
        settings.useWideViewPort      = true
        settings.loadWithOverviewMode = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls  = false
        settings.displayZoomControls  = false

        // ── Cache ────────────────────────────────────────────────────────────
        // Use the default browser-like caching; the web app controls max-age.
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // ── Mixed content (HTTP inside HTTPS) ────────────────────────────────
        // En debug: ALWAYS_ALLOW para que el servidor de desarrollo (98.80.97.51)
        // funcione sin fricciones. En release: NEVER_ALLOW — el portal se sirve
        // íntegro por HTTPS y la network security config ya prohíbe cleartext.
        settings.mixedContentMode = if (BuildConfig.DEBUG) {
            WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        } else {
            WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // ── User-Agent ───────────────────────────────────────────────────────
        val baseUA = settings.userAgentString
        settings.userAgentString = customUserAgent
            ?: "$baseUA LukiPlay-Android/${appVersionName(webView)}"

        // ── Security ─────────────────────────────────────────────────────────
        // Disallow file-system access from the WebView (defense-in-depth).
        settings.allowFileAccess         = false
        settings.allowContentAccess      = false
    }

    private fun configureCookies() {
        // Solo cookies first-party: el portal es un único dominio (lukiplay.com)
        // y nada requiere cookies de terceros.
        CookieManager.getInstance().setAcceptCookie(true)
    }

    private fun appVersionName(webView: WebView): String = try {
        webView.context.packageManager
            .getPackageInfo(webView.context.packageName, 0)
            .versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }
}
