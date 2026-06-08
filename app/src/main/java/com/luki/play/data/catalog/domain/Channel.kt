// data/catalog/domain/Channel.kt
package com.luki.play.data.catalog.domain

/**
 * Modelo de dominio de un canal/contenido del catálogo.
 *
 * Vive en la capa de dominio (sin dependencias Android, Compose ni Retrofit)
 * para poder testearse y reutilizarse en móvil + TV.
 */
data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val category: String,
    val type: ContentType,
    val parentalLocked: Boolean,
)

enum class ContentType { TV, MOVIE, SERIES, RADIO, OTHER }

/**
 * Slider hero / promocional.
 */
data class Slider(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val action: SliderAction,
)

sealed interface SliderAction {
    data class OpenChannel(val channelId: String) : SliderAction
    data class OpenUrl(val url: String) : SliderAction
    data object None : SliderAction
}
