// player/StreamConfig.kt
package com.luki.play.player

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Tipo de manifiesto del stream.
 *
 * Se determina por el JSON del backend (`manifestType`) o, en su defecto,
 * por la extensión de la URL. El player elige el `MediaSource.Factory` adecuado.
 */
enum class ManifestType { HLS, DASH, OTHER }

/**
 * Esquema DRM. Por ahora sólo Widevine; PlayReady / ClearKey quedan reservados.
 */
enum class DrmScheme { NONE, WIDEVINE }

/**
 * Parcelable value object que lleva todos los parámetros que el reproductor necesita.
 *
 * Se pasa desde [com.luki.play.bridge.LukiBridge] → [PlayerActivity] vía Intent extras.
 *
 * @param url               URL del manifiesto HLS o DASH (obligatorio).
 * @param title             Título legible del canal / contenido.
 * @param posterUrl         Miniatura opcional mostrada antes de iniciar.
 * @param subtitleUri       URL del archivo de subtítulos externo (WebVTT, SRT, TTML). Nullable.
 * @param subtitleMimeType  MIME del subtítulo, p.ej. "text/vtt" o "application/x-subrip".
 *                          Ignorado si [subtitleUri] es null.
 * @param manifestType      [ManifestType] detectado/forzado. Si es OTHER, se deduce por URL.
 * @param drmScheme         [DrmScheme] del contenido. NONE para clear (FTA, HLS in-the-clear).
 * @param licenseUrl        URL del servidor de licencias DRM. Requerido si [drmScheme] != NONE.
 * @param licenseHeaders    Headers extra (auth, x-customdata) para la petición de licencia.
 * @param drmMultiSession   Para live DRM con rotación de claves: habilita renegociación.
 * @param channelId         Id del canal, necesario para abrir la sesión de stream.
 * @param ownsStreamSession Si el reproductor debe abrir y cerrar él la sesión
 *                          de stream (`/public/streams/...`).
 *
 *                          **Solo el camino nativo pone esto en true.** Cuando
 *                          el reproductor se lanza desde [com.luki.play.bridge.LukiBridge],
 *                          la sesión ya la abrió el portal dentro del WebView:
 *                          abrir otra gastaría DOS cupos del plan por un único
 *                          visionado y podría disparar el límite con un solo
 *                          dispositivo.
 */
@Parcelize
data class StreamConfig(
    val url: String,
    val title: String = "",
    val posterUrl: String? = null,
    val subtitleUri: String? = null,
    val subtitleMimeType: String = "text/vtt",
    val manifestType: ManifestType = ManifestType.OTHER,
    val drmScheme: DrmScheme = DrmScheme.NONE,
    val licenseUrl: String? = null,
    val licenseHeaders: Map<String, String> = emptyMap(),
    val drmMultiSession: Boolean = false,
    val channelId: String? = null,
    val ownsStreamSession: Boolean = false,
) : Parcelable {

    /**
     * true si este reproductor debe gestionar la sesión de stream. Exige
     * [channelId]: sin él no hay nada que abrir.
     */
    fun managesStreamSession(): Boolean = ownsStreamSession && !channelId.isNullOrBlank()

    /**
     * Devuelve el [ManifestType] efectivo:
     * - Si fue declarado explícito en el bridge (HLS/DASH), lo respeta.
     * - Si vino OTHER, lo deduce por la extensión del path.
     */
    fun effectiveManifestType(): ManifestType = when (manifestType) {
        ManifestType.HLS, ManifestType.DASH -> manifestType
        ManifestType.OTHER -> when {
            url.contains(".m3u8", ignoreCase = true) -> ManifestType.HLS
            url.contains(".mpd",  ignoreCase = true) -> ManifestType.DASH
            else                                     -> ManifestType.OTHER
        }
    }

    fun hasDrm(): Boolean = drmScheme != DrmScheme.NONE && !licenseUrl.isNullOrBlank()
}
