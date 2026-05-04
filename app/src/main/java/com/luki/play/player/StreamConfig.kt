// player/StreamConfig.kt
package com.luki.play.player

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Parcelable value object que lleva todos los parámetros que el reproductor necesita.
 *
 * Pasado desde [com.luki.play.bridge.LukiBridge] → [PlayerActivity] via Intent extras.
 *
 * @param url             URL del manifiesto HLS (obligatorio).
 * @param title           Título legible del canal / contenido.
 * @param posterUrl       Miniatura opcional mostrada antes de iniciar.
 * @param subtitleUri     URL del archivo de subtítulos (WebVTT, SRT, etc.). Nullable.
 * @param subtitleMimeType MIME del subtítulo, p.ej. "text/vtt" o "application/x-subrip".
 *                        Ignorado si [subtitleUri] es null.
 * @param drmToken        Token DRM reservado para v2 — NO activado.
 */
@Parcelize
data class StreamConfig(
    val url: String,
    val title: String = "",
    val posterUrl: String? = null,
    val subtitleUri: String? = null,
    val subtitleMimeType: String = "text/vtt",   // WebVTT por defecto
    // ⚠️ DRM Widevine no activado. Para futuro: configurar
    // DefaultDrmSessionManager si drmToken != null.
    val drmToken: String? = null
) : Parcelable

