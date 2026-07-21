// data/auth/TokenStore.kt
package com.luki.play.data.auth

/**
 * Almacén de tokens de sesión. Abstrae [com.luki.play.util.SecureStorage]
 * para que las capas de red y dominio no dependan de Android directamente.
 *
 * Las operaciones son sincrónicas y rápidas (SharedPreferences cifradas):
 * pueden invocarse desde el hilo de I/O sin penalización notable.
 */
interface TokenStore {

    /** Token de acceso actual o `null` si no hay sesión. */
    fun accessToken(): String?

    /** Refresh token actual o `null` si no hay sesión. */
    fun refreshToken(): String?

    /** Identificador del usuario autenticado o `null`. */
    fun userId(): String?

    /** Nombre legible del usuario o `null`. */
    fun displayName(): String?

    /** Persiste un nuevo par de tokens y datos asociados. */
    fun save(
        accessToken: String,
        refreshToken: String?,
        userId: String?,
        displayName: String?,
    )

    /** Reemplaza únicamente los tokens (usado tras refresh). */
    fun updateTokens(accessToken: String, refreshToken: String?)

    /** Elimina toda la información de sesión. */
    fun clear()

    /** Identificador estable de dispositivo (no se borra al hacer logout). */
    fun deviceId(): String

    /**
     * Adopta un deviceId externo SOLO si aún no hay uno propio.
     *
     * Existe por la migración web→nativo: el portal ya venía generando su
     * propio id en `localStorage['luki-device-id']` y registrándolo en el
     * backend. Si el nativo estrenara uno distinto, el backend vería un
     * dispositivo NUEVO y consumiría otro cupo de `deviceLimitPolicy` para
     * el mismo aparato. Adoptando el de la web se preserva la continuidad.
     *
     * Nunca sobrescribe: si ya existe uno propio, gana el propio. Así la
     * operación es idempotente y no puede degradarse por una llamada tardía.
     *
     * @return el deviceId vigente tras la operación.
     */
    fun adoptDeviceId(candidate: String): String
}
