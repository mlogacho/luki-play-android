// LukiApplication.kt
package com.luki.play

import android.app.Application
import android.util.Log
import android.webkit.WebView

/**
 * Custom [Application] class for Luki Play.
 *
 * Responsibilities:
 *  - Register this class in AndroidManifest.xml via `android:name=".LukiApplication"`.
 *  - Enable WebView debugging in debug builds.
 *  - Centralise any future SDK/lib initialisation (analytics, crash reporting, etc.)
 *    so that Activities remain thin.
 */
class LukiApplication : Application() {

    companion object {
        private const val TAG = "LukiApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate — Luki Play starting up")
        initWebView()
    }

    // ── Initialisation helpers ────────────────────────────────────────────────

    /**
     * Enables WebView remote debugging when running a debug build.
     *
     * Connect via Chrome DevTools at chrome://inspect after launching the app
     * on a device or emulator with USB debugging enabled.
     */
    private fun initWebView() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
            Log.d(TAG, "WebView debugging enabled (debug build)")
        }
    }
}
