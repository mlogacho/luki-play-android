// feature/login/RecoverPasswordScreen.kt
package com.luki.play.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Recuperación de contraseña por OTP — réplica del `ForgotForm` del portal:
 * mismo shell (gradiente + logo + tarjeta), indicador de 3 pasos, back-link
 * que cambia de etiqueta según el paso, y los mismos copys.
 */
@Composable
fun RecoverPasswordScreen(
    onBackToLogin: () -> Unit,
    viewModel: RecoverPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var idNumber by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    AuthScaffold {
        AuthBackLink(
            label = if (state.step == RecoverStep.VERIFY) "Cambiar cédula"
                    else "Volver al inicio de sesión",
            onClick = {
                if (state.step == RecoverStep.VERIFY) viewModel.backToIdentity() else onBackToLogin()
            },
        )

        AuthHeading(title = "Recuperar contraseña", subtitle = null)

        StepIndicator(stepIndex = state.step.ordinal)

        state.errorMessage?.let { AuthErrorBox(it) }
        state.infoMessage?.let { AuthInfoBox(it) }

        when (state.step) {
            RecoverStep.IDENTITY -> {
                AuthBodyText("Ingresa tu cédula y te enviaremos un código de verificación a tu correo registrado.")
                AuthInput(
                    label = "Cédula de identidad",
                    placeholder = "Ej: 1720345678",
                    value = idNumber,
                    onValueChange = { idNumber = it },
                    keyboardType = KeyboardType.Number,
                    enabled = !state.isLoading,
                )
                AuthPrimaryButton(
                    title = "Enviar código de recuperación",
                    isLoading = state.isLoading,
                    onClick = { viewModel.requestOtp(idNumber) },
                )
            }

            RecoverStep.VERIFY -> {
                AuthBodyText("Ingresa el código que enviamos a tu correo y elige tu nueva contraseña.")
                AuthInput(
                    label = "Código de verificación",
                    placeholder = "Ej: A1B2C3",
                    value = code,
                    // El backend exige exactamente 6; se normaliza igual que
                    // la web (mayúsculas, alfanumérico, tope 6).
                    onValueChange = { raw ->
                        code = raw.uppercase().filter { it.isLetterOrDigit() }.take(6)
                    },
                    enabled = !state.isLoading,
                )
                AuthInput(
                    label = "Nueva contraseña",
                    placeholder = PasswordPolicy.RULE_HINT,
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    secure = true,
                    keyboardType = KeyboardType.Password,
                    enabled = !state.isLoading,
                )
                AuthInput(
                    label = "Confirmar contraseña",
                    placeholder = "••••••••",
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    secure = true,
                    keyboardType = KeyboardType.Password,
                    enabled = !state.isLoading,
                )
                AuthPrimaryButton(
                    title = "Restablecer contraseña",
                    isLoading = state.isLoading,
                    onClick = { viewModel.resetPassword(code, newPassword, confirmPassword) },
                )
                AuthLink(
                    text = "Reenviar código",
                    onClick = { viewModel.requestOtp(idNumber) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            RecoverStep.DONE -> {
                AuthBodyText("Tu contraseña fue restablecida. Ya puedes iniciar sesión.")
                AuthPrimaryButton(
                    title = "Ir al inicio de sesión",
                    onClick = onBackToLogin,
                )
            }
        }
    }
}

/** Tres barras que marcan el avance, como el StepIndicator del portal. */
@Composable
private fun StepIndicator(stepIndex: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 20.dp),
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i <= stepIndex) AuthPalette.Accent else AuthPalette.InputBorder
                    )
            )
        }
    }
}
