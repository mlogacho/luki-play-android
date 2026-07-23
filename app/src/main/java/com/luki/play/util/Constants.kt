package com.luki.play.util

/**
 * Constantes globales del proyecto Luki Play Android.
 */
object Constants {

    // ── URLs base ──────────────────────────────────────────────────────────

    /** Dominio de producción Luki Play */
    const val SERVER_HOST = "lukiplay.com"

    /** URL base del servidor (sin trailing slash) */
    const val SERVER_BASE = "https://$SERVER_HOST"

    /** URL del portal web que carga el WebView como pantalla principal */
    const val BASE_URL = "$SERVER_BASE/home"

    /** URL base de la API REST */
    const val API_BASE_URL = SERVER_BASE

    // ── Endpoints API ──────────────────────────────────────────────────────

    /** Endpoints públicos (no requieren auth) */
    object Public {
        const val CHANNELS = "/public/canales"
        const val SLIDERS  = "/public/sliders"
        /** Plan + suscripción del usuario. Pese al prefijo /public exige Bearer. */
        const val ME_PLAN  = "/public/me/plan"
        /** Dispositivos del usuario (exigen Bearer pese al prefijo /public). */
        const val DEVICES            = "/public/devices"
        const val DEVICES_REGISTER   = "/public/devices/register"
        const val DEVICE_BY_FINGERPRINT = "/public/devices/{fingerprint}"
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
        /** Cambiar contraseña (autenticado) — revoca todas las sesiones */
        const val CHANGE_PASSWORD = "/auth/change-password"

        // ── Activación de cuenta (sin auth) ──
        /** Verifica la cédula y devuelve el customerId */
        const val FIRST_ACCESS = "/auth/app/first-access"
        /** Envía el código de activación al correo REGISTRADO (solo customerId) */
        const val REQUEST_ACTIVATION_CODE = "/auth/app/request-activation-code"
        /** Verifica el código de activación */
        const val VERIFY_ACTIVATION_CODE = "/auth/app/verify-activation-code"
        /** Crea la contraseña y devuelve tokens (login) */
        const val ACTIVATE = "/auth/app/activate"

        // ── Emparejamiento de TV (device-code, sin auth) ──
        /** La TV crea una sesión de emparejamiento y recibe código + QR */
        const val TV_SESSION = "/auth/tv/session"
        /** La TV sondea hasta que el teléfono activa la sesión */
        const val TV_POLL = "/auth/tv/session/{sessionId}/poll"
    }

    // ── SharedPreferences keys ──────────────────────────────────────────────

    const val PREFS_NAME          = "luki_prefs"
    const val KEY_ACCESS_TOKEN    = "luki_access_token"
    const val KEY_REFRESH_TOKEN   = "luki_refresh_token"
    const val KEY_USER_ID         = "luki_user_id"
    const val KEY_DISPLAY_NAME    = "luki_display_name"
    const val KEY_EMAIL           = "luki_email"
    const val KEY_PLAN            = "luki_plan"
    const val KEY_DEVICE_ID       = "luki_device_id"

    // ── Intent extras ──────────────────────────────────────────────────────

    const val EXTRA_IS_TV          = "extra_is_tv"
    const val EXTRA_STREAM_URL     = "extra_stream_url"
    const val EXTRA_CHANNEL_ID     = "extra_channel_id"

    // ── Timeouts y delays ──────────────────────────────────────────────────

    const val SPLASH_DELAY_MS      = 1500L
    const val API_TIMEOUT_MS       = 10_000  // 10 segundos

    // ── Android TV ─────────────────────────────────────────────────────────

    /** User-Agent que identifica la app Android en los logs del servidor */
    const val USER_AGENT_SUFFIX    = "LukiPlay-Android/1.0.0"
}
