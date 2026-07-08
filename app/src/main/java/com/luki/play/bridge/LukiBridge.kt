// bridge/LukiBridge.kt
package com.luki.play.bridge

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import com.luki.play.data.auth.TokenStore
import com.luki.play.player.PlayerActivity
import com.luki.play.player.StreamConfig
import com.luki.play.util.Constants
import com.luki.play.util.DeviceUtilsContract
import org.json.JSONObject

/**
 * JavascriptInterface exposed to the web layer as `window.LukiNative`.
 *
 * All public methods annotated with [@JavascriptInterface] are callable
 * from JavaScript.  Every call arrives on an arbitrary background thread,
 * so any UI work must be posted to the main thread (done via [context]
 * if the context is an Activity, or via [android.os.Handler]).
 *
 * @param context       Application or Activity context — used only for
 *                      [startActivity]; prefer ApplicationContext to avoid
 *                      leaks (MainActivity passes applicationContext).
 * @param deviceUtils   Platform-specific device helper injected by
 *                      MainActivity; decoupled via [DeviceUtilsContract].
 * @param tokenStore    Shared session store (SecureTokenStore over
 *                      EncryptedSharedPreferences) — single source of truth
 *                      for tokens across the web bridge and the native stack.
 * @param onLogout      Lambda called when the web signals user logout, so
 *                      MainActivity can clear WebView state. Always invoked
 *                      on the main thread.
 */
class LukiBridge(
    private val context: Context,
    private val deviceUtils: DeviceUtilsContract,
    private val tokenStore: TokenStore,
    private val onLogout: () -> Unit
) {

    companion object {
        private const val TAG = "LukiBridge"
        /** Name registered in WebView: window.LukiNative */
        const val JS_INTERFACE_NAME = "LukiNative"
    }

    // ------------------------------------------------------------------ //
    //  Playback
    // ------------------------------------------------------------------ //

    /**
     * Launch native HLS player with a given stream URL.
     *
     * JS usage:
     * ```js
     * window.LukiNative.playStream(
     *   JSON.stringify({
     *     url:              "http://.../canal.m3u8",
     *     title:            "Canal HD",
     *     poster:           "http://.../thumb.jpg",  // opcional
     *     subtitleUri:      "http://.../subs.vtt",   // opcional
     *     subtitleMimeType: "text/vtt"               // opcional, default: text/vtt
     *   })
     * )
     * ```
     */
    @JavascriptInterface
    fun playStream(jsonConfig: String) {
        Log.d(TAG, "playStream() → $jsonConfig")
        val msg = BridgeMessage.from(jsonConfig)
        if (msg !is BridgeMessage.PlayStream) {
            // Fallback: tratar el string como URL plana
            val fallbackUrl = jsonConfig.trim()
            if (fallbackUrl.startsWith("http")) {
                launchPlayer(StreamConfig(url = fallbackUrl))
            } else {
                Log.w(TAG, "playStream: payload no reconocido, ignorado")
            }
            return
        }
        launchPlayer(
            StreamConfig(
                url              = msg.url,
                title            = msg.title,
                posterUrl        = msg.poster,
                subtitleUri      = msg.subtitleUri,
                subtitleMimeType = msg.subtitleMimeType ?: "text/vtt"
            )
        )
    }

    /**
     * Stop any active native player.
     * No-op if no player is running (the Activity will just not exist).
     *
     * JS usage: `window.LukiNative.stopStream()`
     */
    @JavascriptInterface
    fun stopStream() {
        Log.d(TAG, "stopStream()")
        // PlayerActivity finishes itself; broadcast is simpler than keeping a reference.
        val intent = Intent(PlayerActivity.ACTION_STOP_PLAYBACK)
            .setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    // ------------------------------------------------------------------ //
    //  Session / Auth
    // ------------------------------------------------------------------ //

    /**
     * Persiste el token de acceso recibido desde la web tras un login exitoso.
     *
     * La web llama este método inmediatamente después de autenticarse, pasando
     * el accessToken JWT que el nativo necesita para llamar endpoints protegidos
     * (ej: `/public/canales/{id}/stream`).
     *
     * JS usage:
     * ```js
     * window.LukiNative.onLoginSuccess(JSON.stringify({
     *   userId:      "uuid-del-usuario",
     *   displayName: "Marco L",
     *   accessToken: "eyJhbGci..."      // JWT del backend
     * }))
     * ```
     */
    @JavascriptInterface
    fun onLoginSuccess(jsonPayload: String) {
        Log.i(TAG, "onLoginSuccess()")
        try {
            val obj = JSONObject(jsonPayload)
            val accessToken = obj.optString("accessToken").ifBlank { null }
            if (accessToken == null) {
                Log.w(TAG, "onLoginSuccess: payload sin accessToken, ignorado")
                return
            }
            tokenStore.save(
                accessToken  = accessToken,
                refreshToken = obj.optString("refreshToken").ifBlank { null },
                userId       = obj.optString("userId").ifBlank { null },
                displayName  = obj.optString("displayName").ifBlank { null }
            )
            Log.i(TAG, "Token persisted for user: ${obj.optString("userId")}")
        } catch (e: Exception) {
            Log.e(TAG, "onLoginSuccess: error persisting session", e)
        }
    }

    /**
     * Devuelve la sesión persistida en el almacén seguro como JSON, para que
     * la web pueda hidratar su authStore al arrancar y evitar volver a pedir
     * el escaneo del QR tras cerrar/abrir la app.
     *
     * JS usage:
     * ```js
     * const raw = window.LukiNative.getStoredSession();
     * const { accessToken, refreshToken, userId, displayName } = JSON.parse(raw);
     * ```
     *
     * Returns `"{}"` si no hay sesión.
     */
    @JavascriptInterface
    fun getStoredSession(): String = try {
        val refresh = tokenStore.refreshToken()
        if (refresh.isNullOrBlank()) "{}"
        else JSONObject().apply {
            put("accessToken",  tokenStore.accessToken() ?: "")
            put("refreshToken", refresh)
            put("userId",       tokenStore.userId() ?: "")
            put("displayName",  tokenStore.displayName() ?: "")
        }.toString()
    } catch (e: Exception) {
        // El almacén cifrado puede fallar (Keystore corrupto/invalidado); un
        // throw aquí mataría el proceso desde el hilo del bridge JS.
        Log.e(TAG, "getStoredSession: fallo leyendo el almacén seguro", e)
        "{}"
    }

    /**
     * Borra los tokens persistidos. La web debe llamarla al hacer logout para
     * que el próximo arranque vuelva a la pantalla de activación.
     *
     * JS usage: `window.LukiNative.clearStoredSession()`
     */
    @JavascriptInterface
    fun clearStoredSession() {
        Log.i(TAG, "clearStoredSession()")
        try {
            tokenStore.clear()
        } catch (e: Exception) {
            Log.e(TAG, "clearStoredSession: fallo limpiando el almacén seguro", e)
        }
    }

    /**
     * Notify native that the user logged out.
     * Clears WebView cookies/storage via [onLogout] callback.
     *
     * JS usage: `window.LukiNative.logout()`
     */
    @JavascriptInterface
    fun logout() {
        Log.i(TAG, "logout()")
        clearStoredSession()
        // onLogout toca el WebView (clearCache/loadUrl), que solo admite el
        // hilo principal; este método llega en el hilo del bridge JS.
        Handler(Looper.getMainLooper()).post { onLogout() }
    }

    // ------------------------------------------------------------------ //
    //  Device / UI
    // ------------------------------------------------------------------ //

    /**
     * Returns a JSON string with device context so the web layer can
     * adapt its layout without UA sniffing.
     *
     * JS usage:
     * ```js
     * const info = JSON.parse(window.LukiNative.getDeviceInfo());
     * if (info.isTV) { /* render TV layout */ }
     * ```
     *
     * Response shape:
     * ```json
     * {
     *   "isTV":          true,
     *   "label":         "Chromecast with Google TV",
     *   "screenWidthDp": 1920,
     *   "screenHeightDp":1080,
     *   "supportsPip":   false
     * }
     * ```
     */
    @JavascriptInterface
    fun getDeviceInfo(): String {
        val deviceId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "android-unknown"
        return JSONObject().apply {
            put("isTV",           deviceUtils.isTvDevice())
            put("label",          deviceUtils.getDeviceLabel())
            put("screenWidthDp",  deviceUtils.getScreenWidthDp())
            put("screenHeightDp", deviceUtils.getScreenHeightDp())
            put("supportsPip",    deviceUtils.supportsPip())
            put("deviceId",       deviceId)   // Necesario para /auth/app/*-login
            put("platform",       "android")
            put("apiBaseUrl",     Constants.API_BASE_URL)  // Para que la web sepa la URL
        }.toString()
    }

    /**
     * Request native PiP (mobile only; no-op on TV).
     *
     * JS usage: `window.LukiNative.enterPip()`
     */
    @JavascriptInterface
    fun enterPip() {
        Log.d(TAG, "enterPip()")
        deviceUtils.enterPip()
    }

    /**
     * Generic dispatch entry-point for future message types.
     * The web can also call specific methods above directly.
     *
     * JS usage:
     * ```js
     * window.LukiNative.dispatch(
     *   JSON.stringify({ type: "get_device_info" })
     * )
     * ```
     */
    @JavascriptInterface
    fun dispatch(json: String) {
        Log.d(TAG, "dispatch() → $json")
        when (val msg = BridgeMessage.from(json)) {
            is BridgeMessage.PlayStream   -> launchPlayer(StreamConfig(msg.url, msg.title, msg.poster))
            is BridgeMessage.StopStream   -> stopStream()
            is BridgeMessage.LoginSuccess -> onLoginSuccess(json)
            is BridgeMessage.Logout       -> logout()
            is BridgeMessage.EnterPip     -> enterPip()
            is BridgeMessage.GetDeviceInfo -> { /* synchronous — caller should use getDeviceInfo() */ }
            null -> Log.w(TAG, "dispatch: unknown message type, ignored")
        }
    }

    // ------------------------------------------------------------------ //
    //  Internal helpers
    // ------------------------------------------------------------------ //

    private fun launchPlayer(config: StreamConfig) {
        val intent = PlayerActivity.newIntent(context, config)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
