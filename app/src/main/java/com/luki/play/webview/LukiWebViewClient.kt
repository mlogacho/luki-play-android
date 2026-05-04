// webview/LukiWebViewClient.kt
package com.luki.play.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread

/**
 * Custom [WebViewClient] for Luki Play.
 *
 * Responsibilities:
 *  - Intercept page start / finish events to drive a loading indicator.
 *  - Handle SSL errors gracefully (proceed only for our trusted IP).
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

        /** IP address of the Luki backend — only domain where cleartext is allowed. */
        private const val LUKI_HOST = "98.80.97.51"
    }

    // ── Page lifecycle ────────────────────────────────────────────────────────

    @MainThread
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "onPageStarted: $url")
        onPageStarted()
    }

    @MainThread
    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "onPageFinished: $url")
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
            Log.e(TAG, "onReceivedError: code=$code desc=$desc url=${request.url}")
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
            Log.e(TAG, "onReceivedHttpError: status=$code url=${request.url}")
            if (code >= 500) {
                onError(code, "HTTP $code")
            }
        }
    }

    /**
     * Handle SSL errors. Because our server uses plain HTTP, this should not
     * be triggered in normal operation. If it is (e.g. captive portal HTTPS
     * redirect), we log it and cancel to avoid security leaks.
     *
     * NOTE: Do NOT call handler.proceed() for arbitrary SSL errors in production.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        Log.w(TAG, "onReceivedSslError: $error — cancelling request")
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
            Log.d(TAG, "shouldOverrideUrlLoading: allowing $url")
            false
        } else {
            // External host → block silently (or open in browser if needed)
            Log.w(TAG, "shouldOverrideUrlLoading: blocking external URL $url")
            true
        }
    }
}
