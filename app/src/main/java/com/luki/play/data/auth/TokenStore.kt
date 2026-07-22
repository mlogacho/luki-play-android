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

    /** Correo del usuario o `null`. Lo muestra la tarjeta del menú de cuenta. */
    fun email(): String?

    /**
     * Plan contratado en minúsculas (`lukiplay`, `premium`, …) o `null`.
     * Define el color del avatar, igual que `PLAN_COLORS` en el portal.
     */
    fun plan(): String?

    /** Persiste un nuevo par de tokens y datos asociados. */
    fun save(
        accessToken: String,
        refreshToken: String?,
        userId: String?,
        displayName: String?,
        email: String?,
        plan: String?,
    )

    /** Reemplaza únicamente los tokens (usado tras refresh). */
    fun updateTokens(accessToken: String, refreshToken: String?)

    /** Elimina toda la información de sesión. */
    fun clear()

    /**
     * Identificador estable de dispositivo (no se borra al hacer logout).
     *
     * **Lo CREA si no existe.** Para consultarlo sin provocar esa creación
     * está [existingDeviceId].
     */
    fun deviceId(): String

    /**
     * deviceId actual o `null` si todavía no hay ninguno, **sin crearlo**.
     *
     * Existe por un problema de orden en la migración: `getDeviceInfo()` del
     * bridge se invoca en el arranque (vía `isTvDevice()`) y, al usar
     * [deviceId], generaba un id propio ANTES de que la web pudiera entregar
     * el suyo con [adoptDeviceId]. Como la adopción nunca sobrescribe, eso
     * dejaba el aparato con dos identidades para siempre — justo lo que la
     * unificación pretende evitar.
     */
    fun existingDeviceId(): String?

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
