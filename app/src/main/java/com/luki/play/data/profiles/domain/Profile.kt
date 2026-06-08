// data/profiles/domain/Profile.kt
package com.luki.play.data.profiles.domain

/**
 * Perfil de usuario dentro de una misma cuenta (multiperfil tipo Netflix).
 *
 * @param parentalRating  Edad máxima permitida (PG-13, R, etc.) representada en años.
 * @param isKid           Perfil infantil — fuerza filtros + UI simplificada.
 * @param requiresPin     El perfil pide PIN al seleccionarse (perfiles adultos).
 */
data class Profile(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val isKid: Boolean,
    val parentalRating: Int,
    val requiresPin: Boolean,
)
