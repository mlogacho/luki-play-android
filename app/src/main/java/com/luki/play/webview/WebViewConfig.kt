// webview/WebViewConfig.kt
package com.luki.play.webview

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

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
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls  = false
        settings.displayZoomControls  = false

        // ── Cache ────────────────────────────────────────────────────────────
        // Use the default browser-like caching; the web app controls max-age.
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // ── Mixed content (HTTP inside HTTPS) ────────────────────────────────
        // Required because our server is plain HTTP (98.80.97.51).
        // ALWAYS_ALLOW is acceptable here because network_security_config.xml
        // already restricts cleartext to that single IP.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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
        CookieManager.getInstance().setAcceptCookie(true)
        // Per-instance third-party cookies are enabled via enableThirdPartyCookies()
        // called from each Activity after WebViewConfig.apply() returns.
    }

    /** Enable third-party cookies for [webView] (must be called per-instance). */
    fun enableThirdPartyCookies(webView: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }
    }

    private fun appVersionName(webView: WebView): String = try {
        webView.context.packageManager
            .getPackageInfo(webView.context.packageName, 0)
            .versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }
}
