// webview/LukiWebViewClient.kt
package com.luki.play.webview

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import com.luki.play.BuildConfig
import com.luki.play.util.Constants
import timber.log.Timber

/**
 * Custom [WebViewClient] for Luki Play.
 *
 * Responsibilities:
 *  - Intercept page start / finish events to drive a loading indicator.
 *  - Handle SSL errors gracefully (always cancel — never proceed).
 *  - Route errors to a native offline/retry screen rather than blank WebView.
 *  - Block navigation to external domains outside the Luki backend.
 *
 * @param onPageStarted Called on the main thread when navigation begins.
 * @param onPageFinished Called on the main thread when the page finishes loading.
 * @param onError Called when a network or HTTP error occurs; the host Activity
 *                can show a retry UI.
 */
class LukiWebViewClient(
    private val onPageStarted: () -> Unit = {},
    private val onPageFinished: () -> Unit = {},
    private val onError: (errorCode: Int, description: String) -> Unit = { _, _ -> }
) : WebViewClient() {

    companion object {
        private const val TAG = "LukiWebViewClient"

        /**
         * Host of the Luki portal, derived from [BuildConfig.BASE_URL] so the
         * navigation allowlist always matches the URL each build actually loads.
         */
        private val LUKI_HOST: String =
            Uri.parse(BuildConfig.BASE_URL).host ?: Constants.SERVER_HOST
    }

    // ── Page lifecycle ────────────────────────────────────────────────────────

    @MainThread
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Timber.tag(TAG).d("onPageStarted: %s", url)
        onPageStarted()
    }

    @MainThread
    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        Timber.tag(TAG).d("onPageFinished: %s", url)
        onPageFinished()
    }

    // ── Error handling ────────────────────────────────────────────────────────

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        // Only report errors on the main frame (not sub-resources like images).
        if (request.isForMainFrame) {
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                error.errorCode
            } else {
                -1
            }
            val desc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                error.description?.toString() ?: "Unknown error"
            } else {
                "Load error"
            }
            Timber.tag(TAG).e("onReceivedError: code=%d desc=%s url=%s", code, desc, request.url)
            onError(code, desc)
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        if (request.isForMainFrame) {
            val code = errorResponse.statusCode
            Timber.tag(TAG).e("onReceivedHttpError: status=%d url=%s", code, request.url)
            if (code >= 500) {
                onError(code, "HTTP $code")
            }
        }
    }

    /**
     * Handle SSL errors. The portal is served over HTTPS with a valid
     * certificate, so this should not trigger in normal operation. If it does
     * (e.g. captive portal interception), we log it and cancel to avoid
     * security leaks.
     *
     * NOTE: Do NOT call handler.proceed() for arbitrary SSL errors in production.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        Timber.tag(TAG).w("onReceivedSslError: %s — cancelling request", error)
        handler.cancel()
        onError(-2, "SSL error: ${error.primaryError}")
    }

    // ── Navigation interception ───────────────────────────────────────────────

    /**
     * Allows navigation only within the Luki backend ([LUKI_HOST]).
     * External links (e.g. social logins, payment gateways) are blocked
     * to prevent the WebView from becoming a general-purpose browser.
     *
     * Return false → WebView handles the URL (load it).
     * Return true  → native handles the URL (we intercept and block/redirect).
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url  = request.url ?: return false
        val host = url.host ?: return false

        return if (host == LUKI_HOST || host.endsWith(".$LUKI_HOST")) {
            // Trusted host → let WebView load it normally
            Timber.tag(TAG).d("shouldOverrideUrlLoading: allowing %s", url)
            false
        } else {
            // External host → block silently (or open in browser if needed)
            Timber.tag(TAG).w("shouldOverrideUrlLoading: blocking external URL %s", url)
            true
        }
    }
}
