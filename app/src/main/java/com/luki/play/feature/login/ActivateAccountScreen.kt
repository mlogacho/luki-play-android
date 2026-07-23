// feature/login/ActivateAccountScreen.kt
package com.luki.play.feature.login

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Activación de cuenta nueva, réplica del sub-flujo "Activar cuenta" de
 * `frontend/app/(auth)/activar.tsx`. Usa el mismo sistema de diseño que el
 * login ([AuthScaffold] y compañía).
 *
 * @param onActivated cuenta activada y sesión iniciada → ir a Home (como login).
 * @param onBackToLogin volver a la pantalla de login.
 */
@Composable
fun ActivateAccountScreen(
    onActivated: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: ActivateAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var idNumber by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.activated) {
        if (state.activated) onActivated()
    }

    AuthScaffold {
        when (state.step) {
            ActivateStep.IDENTITY -> {
                AuthBackLink("Volver al inicio de sesión", onBackToLogin)
                AuthHeading("Activar cuenta", "Ingresa tu cédula para verificar tu identidad.")
                state.errorMessage?.let { AuthErrorBox(it) }
                AuthInput(
                    label = "Cédula de identidad",
                    placeholder = "Ej: 1720345678",
                    value = idNumber,
                    onValueChange = { idNumber = it },
                    keyboardType = KeyboardType.Number,
                )
                AuthPrimaryButton("Verificar identidad", { viewModel.verifyIdentity(idNumber) }, isLoading = state.isLoading)
                AuthFooterAction(
                    question = "¿Ya tienes contraseña?",
                    action = "Inicia sesión",
                    onClick = onBackToLogin,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            ActivateStep.REQUEST_CODE -> {
                AuthBackLink("Atrás", { viewModel.backTo(ActivateStep.IDENTITY) })
                AuthHeading("Código de activación", "Te enviaremos un código al correo que tienes registrado en Luki.")
                state.errorMessage?.let { AuthErrorBox(it) }
                AuthPrimaryButton("Enviar código a mi correo", { viewModel.requestCode() }, isLoading = state.isLoading)
            }

            ActivateStep.VERIFY_CODE -> {
                AuthBackLink("Atrás", { viewModel.backTo(ActivateStep.REQUEST_CODE) })
                val subtitle = state.maskedEmail
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "Enviamos un código de 6 caracteres a $it." }
                    ?: "Ingresa el código de 6 caracteres que recibiste."
                AuthHeading("Ingresa tu código", subtitle)
                state.errorMessage?.let { AuthErrorBox(it) }
                AuthInput(
                    label = "Código de activación",
                    placeholder = "Ej: A1B2C3",
                    value = code,
                    onValueChange = { raw -> code = raw.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                )
                AuthPrimaryButton("Verificar código", { viewModel.verifyCode(code) }, isLoading = state.isLoading)
                AuthLink("¿No recibiste el código? Volver", { viewModel.backTo(ActivateStep.REQUEST_CODE) }, modifier = Modifier.padding(top = 16.dp))
            }

            ActivateStep.CREATE_PASSWORD -> {
                AuthBackLink("Atrás", { viewModel.backTo(ActivateStep.VERIFY_CODE) })
                AuthHeading("Crea tu contraseña", "Último paso: elige una contraseña para tu cuenta.")
                state.errorMessage?.let { AuthErrorBox(it) }
                AuthInfoBox("Código verificado correctamente")
                AuthInput(
                    label = "Nueva contraseña",
                    placeholder = PasswordPolicy.RULE_HINT,
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    secure = true,
                )
                AuthInput(
                    label = "Confirmar contraseña",
                    placeholder = "Repite tu contraseña",
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    secure = true,
                )
                AuthInput(
                    label = "Correo electrónico (opcional)",
                    placeholder = "Para notificaciones",
                    value = email,
                    onValueChange = { email = it },
                    keyboardType = KeyboardType.Email,
                )
                AuthPrimaryButton("Activar cuenta", { viewModel.createPassword(newPassword, confirmPassword, email) }, isLoading = state.isLoading)
            }
        }
    }
}
