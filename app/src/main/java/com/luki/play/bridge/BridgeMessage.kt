// bridge/BridgeMessage.kt
package com.luki.play.bridge

import org.json.JSONObject

/**
 * Sealed hierarchy for every message the web layer can send to the
 * native bridge via `window.LukiNative.<method>(jsonString)`.
 *
 * All messages share a [type] discriminator so that [LukiBridge] can
 * dispatch them in a single entry-point if needed, or the web can call
 * each method directly (both patterns are supported).
 */
sealed class BridgeMessage {

    // ------------------------------------------------------------------ //
    //  Playback
    // ------------------------------------------------------------------ //

    /**
     * Web requests native HLS playback.
     *
     * JSON shape:
     * ```json
     * {
     *   "url":              "http://...",
     *   "title":            "Canal HD",
     *   "poster":           "http://...",
     *   "subtitleUri":      "http://.../subs.vtt",
     *   "subtitleMimeType": "text/vtt"
     * }
     * ```
     */
    data class PlayStream(
        val url: String,
        val title: String,
        val poster: String?,
        val subtitleUri: String?,
        val subtitleMimeType: String?
    ) : BridgeMessage()

    /** Web requests the player to stop / return from PlayerActivity. */
    object StopStream : BridgeMessage()

    // ------------------------------------------------------------------ //
    //  Session / Auth
    // ------------------------------------------------------------------ //

    /**
     * Web signals that the user has completed login.
     * Native side can react (e.g. hide progress indicators, store tokens).
     *
     * JSON shape:
     * ```json
     * { "userId": "42", "displayName": "Marco" }
     * ```
     */
    data class LoginSuccess(val userId: String, val displayName: String) : BridgeMessage()

    /** Web signals that the user has logged out; native clears any cached state. */
    object Logout : BridgeMessage()

    // ------------------------------------------------------------------ //
    //  Device / UI
    // ------------------------------------------------------------------ //

    /**
     * Web requests entering Picture-in-Picture (mobile only).
     * Native checks [DeviceUtilsContract.supportsPip] before acting.
     */
    object EnterPip : BridgeMessage()

    /**
     * Web requests the current device context so it can adjust its layout.
     * Native responds via [LukiBridge.getDeviceInfo].
     */
    object GetDeviceInfo : BridgeMessage()

    // ------------------------------------------------------------------ //
    //  Parse helper
    // ------------------------------------------------------------------ //

    companion object {
        /**
         * Parses a raw JSON string arriving from the WebView JS layer.
         * Returns `null` for unrecognised [type] values (logged, not thrown).
         */
        fun from(json: String): BridgeMessage? {
            return try {
                val obj  = JSONObject(json)
                val type = obj.optString("type")
                when (type) {
                    "play_stream"   -> PlayStream(
                        url              = obj.getString("url"),
                        title            = obj.optString("title", ""),
                        poster           = obj.optString("poster").ifBlank { null },
                        subtitleUri      = obj.optString("subtitleUri").ifBlank { null },
                        subtitleMimeType = obj.optString("subtitleMimeType").ifBlank { null }
                    )
                    "stop_stream"   -> StopStream
                    "login_success" -> LoginSuccess(
                        userId      = obj.getString("userId"),
                        displayName = obj.optString("displayName", "")
                    )
                    "logout"        -> Logout
                    "enter_pip"     -> EnterPip
                    "get_device_info" -> GetDeviceInfo
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
