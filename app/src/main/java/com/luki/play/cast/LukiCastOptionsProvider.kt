// cast/LukiCastOptionsProvider.kt
package com.luki.play.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * Configura el Cast Framework de Google Cast SDK.
 *
 * Receiver ID:
 *  - Por defecto usamos `CC1AD845` — el **Styled Media Receiver** oficial
 *    de Google. Reproduce HLS/MP4 clear sin registro previo. Suficiente
 *    para todos los canales FTA de Luki Play.
 *  - Para contenido Widevine se requerirá un **receiver custom** registrado
 *    en la Google Cast SDK Developer Console que sepa pedir licencia al
 *    license server de Luki. Cuando ese receptor exista, sustituir
 *    [RECEIVER_APP_ID] por el ID asignado.
 *
 * El sistema descubre esta clase automáticamente vía manifest:
 * ```
 * <meta-data
 *   android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
 *   android:value="com.luki.play.cast.LukiCastOptionsProvider" />
 * ```
 */
class LukiCastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
            .setActions(listOf(
                com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_STOP_CASTING,
            ), intArrayOf(0, 1))
            .build()

        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(RECEIVER_APP_ID)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): MutableList<SessionProvider>? = null

    companion object {
        /**
         * Styled Media Receiver de Google — reproduce HLS/MP4 clear sin registro.
         * Sustituir por el ID del receiver custom de Luki cuando esté registrado
         * en https://cast.google.com/publish para soportar Widevine y branding.
         */
        const val RECEIVER_APP_ID = "CC1AD845"
    }
}
