// data/config/FeatureFlags.kt
package com.luki.play.data.config

/**
 * Feature flags de la app. Hoy solo el interruptor del arranque nativo, que
 * es el que necesita rollout gradual y kill-switch.
 *
 * La lectura es **síncrona**: devuelve el último valor activado (cacheado en
 * disco por Remote Config) o, si nunca se activó ninguno, el valor por
 * defecto compilado. Así [com.luki.play.ui.RouterActivity] puede decidir la
 * ruta sin bloquear en red; la actualización viaja en segundo plano y aplica
 * en el siguiente arranque.
 */
interface FeatureFlags {

    /** ¿Arrancar en la UI nativa (Compose) en vez del portal WebView? */
    val nativeHomeEnabled: Boolean

    /** Dispara una actualización en segundo plano para el próximo arranque. */
    fun refresh()

    companion object {
        /** Clave del parámetro en la consola de Firebase Remote Config. */
        const val KEY_NATIVE_HOME_ENABLED = "native_home_enabled"
    }
}
