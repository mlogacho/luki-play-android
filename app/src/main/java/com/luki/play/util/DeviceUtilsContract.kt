// util/DeviceUtilsContract.kt
package com.luki.play.util

/**
 * Contract for device-capability queries that differ between
 * mobile and Android TV form factors.
 *
 * tv-mobile-ui implements this via [com.luki.play.ui.DeviceUtils].
 * WebView-bridge holds a reference to it so that LukiBridge can
 * answer JS queries about the running context without coupling
 * directly to UI or sensor APIs.
 */
interface DeviceUtilsContract {

    /**
     * Returns true when the app is running on an Android TV / Leanback
     * surface (UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION).
     */
    fun isTvDevice(): Boolean

    /**
     * Returns the display name that the web layer should show for the
     * current device (e.g. "Samsung Smart TV", "Pixel 7", "Fire Stick").
     * Defaults to [Build.MODEL] when no label has been set.
     */
    fun getDeviceLabel(): String

    /**
     * Returns the physical screen width in dp (density-independent pixels).
     * Used by the web layer to decide whether to render the TV or mobile
     * breakpoint of its responsive layout.
     */
    fun getScreenWidthDp(): Int

    /**
     * Returns the physical screen height in dp.
     */
    fun getScreenHeightDp(): Int

    /**
     * True when the current Android version supports Picture-in-Picture
     * (API 26+) AND the device is NOT a TV (PiP on TV uses a different UX).
     */
    fun supportsPip(): Boolean

    /**
     * Requests that the host Activity enter Picture-in-Picture mode.
     * No-op if [supportsPip] is false.
     */
    fun enterPip()
}
