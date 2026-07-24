// data/auth/api/AuthDto.kt
package com.luki.play.data.auth.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs de la API de autenticación.
 *
 * Moshi codegen genera adaptadores en tiempo de compilación (sin reflexión),
 * lo que elimina la necesidad de reglas ProGuard extra.
 *
 * Contrato verificado contra el backend real (Luki-Play-OTT,
 * `contract-login.use-case.ts` / `id-number-login`, 2026-07-21): los logins
 * devuelven `{ accessToken, refreshToken, user: { id, name, email, plan } }`
 * — el usuario viene ANIDADO en `user`, no plano. `/auth/refresh` devuelve
 * `{ accessToken, refreshToken, canAccessOtt, restrictionMessage }` sin
 * `user`, por eso todos los campos de identidad son opcionales.
 */

@JsonClass(generateAdapter = true)
data class IdLoginRequest(
    @Json(name = "idNumber")  val idNumber: String,
    @Json(name = "password")  val password: String,
    @Json(name = "deviceId")  val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class ContractLoginRequest(
    @Json(name = "contractNumber") val contractNumber: String,
    @Json(name = "password")       val password: String,
    @Json(name = "deviceId")       val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    @Json(name = "refreshToken") val refreshToken: String,
)

/**
 * Solicitud de OTP de recuperación. El backend responde SIEMPRE 200 con el
 * mismo mensaje (anti-enumeración OWASP): no confirma si la cédula existe.
 */
@JsonClass(generateAdapter = true)
data class RequestPasswordOtpRequest(
    @Json(name = "idNumber") val idNumber: String,
)

/** Reset con OTP — backend exige código de 6 y política de contraseña. */
@JsonClass(generateAdapter = true)
data class ResetPasswordOtpRequest(
    @Json(name = "idNumber")    val idNumber: String,
    @Json(name = "otpCode")     val otpCode: String,
    @Json(name = "newPassword") val newPassword: String,
)

/** Respuesta genérica `{ message }` de los endpoints de OTP. */
@JsonClass(generateAdapter = true)
data class MessageResponseDto(
    @Json(name = "message") val message: String?,
)

// ── Activación de cuenta ────────────────────────────────────────────────────────

/** first-access: identifica al cliente por cédula. */
@JsonClass(generateAdapter = true)
data class FirstAccessRequest(
    @Json(name = "idNumber") val idNumber: String,
)

@JsonClass(generateAdapter = true)
data class FirstAccessDto(
    @Json(name = "customerId") val customerId: String?,
)

/**
 * request-activation-code: el cuerpo SOLO lleva el customerId. El backend
 * envía el código al correo REGISTRADO del cliente y devuelve su versión
 * enmascarada. Dejar que el cliente eligiera el destino permitía secuestrar
 * cuentas sin activar con solo la cédula (P0 corregido) — por eso aquí NO hay
 * campo de correo.
 */
@JsonClass(generateAdapter = true)
data class RequestActivationCodeRequest(
    @Json(name = "customerId") val customerId: String,
)

@JsonClass(generateAdapter = true)
data class ActivationCodeDto(
    @Json(name = "sent")            val sent: Boolean = false,
    /** true si el correo no se pudo enviar: hay que ir a soporte, no a teclear. */
    @Json(name = "needsSupportCode") val needsSupportCode: Boolean = false,
    @Json(name = "maskedEmail")      val maskedEmail: String? = null,
)

/** verify-activation-code: valida el código de 6 antes de crear la contraseña. */
@JsonClass(generateAdapter = true)
data class VerifyActivationCodeRequest(
    @Json(name = "customerId") val customerId: String,
    @Json(name = "code")       val code: String,
)

/**
 * activate: crea la contraseña y devuelve tokens (login). `email` es OPCIONAL y
 * solo sirve para notificaciones — NO es el destino del código, que ya se envió
 * al correo registrado en el paso anterior.
 */
@JsonClass(generateAdapter = true)
data class ActivateRequest(
    @Json(name = "customerId") val customerId: String,
    @Json(name = "otpCode")    val otpCode: String,
    @Json(name = "password")   val password: String,
    @Json(name = "email")      val email: String? = null,
)

/**
 * Solicitud de acceso de un no-cliente (`POST /auth/app/registration-request`).
 * `email`/`direccion` son opcionales; el backend valida cédula/RUC y el formato
 * del correo. Mismo shape que `SubmitRegistrationRequestDto` del backend.
 */
@JsonClass(generateAdapter = true)
data class RegistrationRequestBody(
    @Json(name = "nombres")   val nombres: String,
    @Json(name = "apellidos") val apellidos: String,
    @Json(name = "idNumber")  val idNumber: String,
    @Json(name = "telefono")  val telefono: String,
    @Json(name = "email")     val email: String? = null,
    @Json(name = "direccion") val direccion: String? = null,
)

/** Usuario anidado en la respuesta de login — shape real del backend. */
@JsonClass(generateAdapter = true)
data class AuthUserDto(
    @Json(name = "id")    val id: String?,
    @Json(name = "name")  val name: String?,
    @Json(name = "email") val email: String?,
    @Json(name = "plan")  val plan: String?,
)

/**
 * Solicitud de cambio de contraseña (autenticado). El Bearer lo adjunta el
 * [com.luki.play.data.network.AuthInterceptor]; el backend revoca todas las
 * sesiones al tener éxito.
 */
@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(
    @Json(name = "currentPassword") val currentPassword: String,
    @Json(name = "newPassword")     val newPassword: String,
)

/**
 * Perfil del usuario autenticado (`GET /auth/me`).
 *
 * Contrato verificado contra `UserProfileResponse` del portal
 * (`services/api/authApi.ts`). Solo se declaran los campos que la pantalla de
 * perfil usa; Moshi ignora las claves JSON extra. Todos son opcionales para
 * que una respuesta parcial no reviente el parseo (lección de `StreamDto`).
 */
@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "id")             val id: String? = null,
    @Json(name = "firstName")      val firstName: String? = null,
    @Json(name = "lastName")       val lastName: String? = null,
    @Json(name = "idNumber")       val idNumber: String? = null,
    @Json(name = "contractNumber") val contractNumber: String? = null,
    @Json(name = "email")          val email: String? = null,
    @Json(name = "serviceStatus")  val serviceStatus: String? = null,
    // El portal restringe el acceso solo cuando el backend lo dice explícito;
    // ante la duda (campo ausente) NO restringimos.
    @Json(name = "canAccessOtt")   val canAccessOtt: Boolean = true,
    @Json(name = "lastLoginAt")    val lastLoginAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    @Json(name = "accessToken")  val accessToken: String,
    @Json(name = "refreshToken") val refreshToken: String?,
    /** Presente en login; ausente en refresh. */
    @Json(name = "user")         val user: AuthUserDto? = null,
    // Banderas de primer login (nivel superior en id-login/contract-login). El
    // portal enruta a "Configura tu cuenta" si isTempPassword || primerLogin.
    @Json(name = "mustChangePassword") val mustChangePassword: Boolean = false,
    @Json(name = "primerLogin")        val primerLogin: Boolean = false,
    @Json(name = "isTempPassword")     val isTempPassword: Boolean = false,
    @Json(name = "emailVerificado")    val emailVerificado: Boolean = false,
    // Alias planos legacy — tolerancia por si algún endpoint viejo los usa.
    @Json(name = "userId")       val userId: String? = null,
    @Json(name = "id")           val idAlt: String? = null,
    @Json(name = "nombre")       val nombre: String? = null,
    @Json(name = "name")         val nameAlt: String? = null,
) {
    fun resolvedUserId(): String = user?.id ?: userId ?: idAlt ?: ""
    fun resolvedDisplayName(): String = user?.name ?: nombre ?: nameAlt ?: ""

    /** Requiere el flujo de primer login (misma condición que login.tsx:105). */
    fun requiresPrimerLogin(): Boolean = isTempPassword || primerLogin
}

/** Cambia la clave temporal del primer login (`complete-primer-login`, autenticado). */
@JsonClass(generateAdapter = true)
data class CompletePrimerLoginRequest(
    @Json(name = "newPassword")     val newPassword: String,
    @Json(name = "confirmPassword") val confirmPassword: String,
    @Json(name = "email")           val email: String? = null,
)

/** Respuesta de `complete-primer-login`: si falta verificar el correo. */
@JsonClass(generateAdapter = true)
data class PrimerLoginResultDto(
    @Json(name = "message")                  val message: String? = null,
    @Json(name = "requiresEmailVerification") val requiresEmailVerification: Boolean = false,
)

/** Envía el OTP de verificación de correo (`send-email-verification`, autenticado). */
@JsonClass(generateAdapter = true)
data class SendEmailVerificationRequest(
    @Json(name = "email") val email: String? = null,
)

/** Verifica el OTP de 6 dígitos del correo (`verify-email`, autenticado). */
@JsonClass(generateAdapter = true)
data class VerifyEmailRequest(
    @Json(name = "code") val code: String,
)
