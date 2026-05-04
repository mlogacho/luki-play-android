// util/Constants.kt
package com.luki.play.util

/**
 * Constantes globales del proyecto Luki Play Android.
 *
 * Arquitectura del servidor (98.80.97.51):
 * ─────────────────────────────────────────
 *   /home         → Portal web (React Native/Expo)  ← WebView carga esto
 *   /cms          → Panel CMS admin
 *   /public/*     → API REST pública (sin auth)
 *   /auth/*       → API de autenticación
 *   /admin/*      → API admin (requiere rol admin)
 *   /uploads/*    → Assets estáticos (logos, imágenes)
 */
object Constants {

    // ── URLs base ──────────────────────────────────────────────────────────

    /** IP del servidor Luki Play */
    const val SERVER_HOST = "98.80.97.51"

    /** URL base del servidor (sin trailing slash) */
    const val SERVER_BASE = "http://$SERVER_HOST"

    /** URL del portal web que carga el WebView como pantalla principal */
    const val BASE_URL = "$SERVER_BASE/home"

    /** URL base de la API REST */
    const val API_BASE_URL = SERVER_BASE  // = http://98.80.97.51

    // ── Endpoints API ──────────────────────────────────────────────────────

    /** Endpoints públicos (no requieren auth) */
    object Public {
        const val CHANNELS = "/public/canales"
        const val SLIDERS  = "/public/sliders"
        fun channelStream(channelId: String) = "/public/canales/$channelId/stream"
        fun streamStart()     = "/public/streams/start"
        fun streamHeartbeat(sessionId: String) = "/public/streams/$sessionId/heartbeat"
    }

    /** Endpoints de autenticación */
    object Auth {
        /** Login con cédula/ID — body: { idNumber, password, deviceId } */
        const val LOGIN_ID       = "/auth/app/id-login"
        /** Login con contrato — body: { contractNumber, password, deviceId } */
        const val LOGIN_CONTRACT = "/auth/app/contract-login"
        /** Perfil del usuario autenticado */
        const val ME             = "/auth/me"
        /** Renovar token — body: { refreshToken } */
        const val REFRESH        = "/auth/refresh"
        /** Cerrar sesión */
        const val LOGOUT         = "/auth/logout"
        /** OTP para recuperar contraseña */
        const val REQUEST_OTP    = "/auth/app/request-password-otp"
        /** Resetear contraseña con OTP */
        const val RESET_WITH_OTP = "/auth/app/reset-with-otp"
    }

    // ── SharedPreferences keys ──────────────────────────────────────────────

    const val PREFS_NAME          = "luki_prefs"
    const val KEY_ACCESS_TOKEN    = "luki_access_token"
    const val KEY_REFRESH_TOKEN   = "luki_refresh_token"
    const val KEY_USER_ID         = "luki_user_id"
    const val KEY_DISPLAY_NAME    = "luki_display_name"
    const val KEY_DEVICE_ID       = "luki_device_id"

    // ── Intent extras ──────────────────────────────────────────────────────

    const val EXTRA_IS_TV          = "extra_is_tv"
    const val EXTRA_STREAM_URL     = "extra_stream_url"
    const val EXTRA_CHANNEL_ID     = "extra_channel_id"

    // ── Timeouts y delays ──────────────────────────────────────────────────

    const val SPLASH_DELAY_MS      = 2300L   // Coincide con la animación del portal web
    const val API_TIMEOUT_MS       = 10_000  // 10 segundos

    // ── Android TV ─────────────────────────────────────────────────────────

    /** User-Agent que identifica la app Android en los logs del servidor */
    const val USER_AGENT_SUFFIX    = "LukiPlay-Android/1.0.0"
}
