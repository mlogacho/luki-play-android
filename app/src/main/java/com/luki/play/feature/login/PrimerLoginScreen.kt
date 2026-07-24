// feature/login/PrimerLoginScreen.kt
package com.luki.play.feature.login

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * "Configura tu cuenta" (primer login), réplica de `PrimerLoginForm` +
 * `VerifyEmailForm` de `frontend/app/(auth)/login.tsx`. Se llega tras un login
 * con clave temporal: el usuario crea su contraseña permanente y, opcionalmente,
 * registra/verifica un correo. Al terminar (o posponer) va al Home.
 *
 * @param onDone cuenta configurada → ir al Home.
 */
@Composable
fun PrimerLoginScreen(
    onDone: () -> Unit,
    viewModel: PrimerLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    AuthScaffold {
        when (state.step) {
            PrimerLoginStep.CONFIGURE -> {
                AuthHeading("Configura tu cuenta", null)
                state.userName.takeIf { it.isNotBlank() }?.let { AuthBodyText("Hola, $it") }
                AuthBodyText("Crea tu contraseña permanente. Opcionalmente registra tu correo para recuperarla si la olvidas.")
                state.errorMessage?.let { AuthErrorBox(it) }

                AuthInput(
                    label = "Nueva contraseña",
                    placeholder = PasswordPolicy.RULE_HINT,
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    secure = true,
                    enabled = !state.isLoading,
                )
                AuthInput(
                    label = "Confirmar contraseña",
                    placeholder = "Repite tu nueva contraseña",
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    secure = true,
                    enabled = !state.isLoading,
                )
                AuthInput(
                    label = "Correo electrónico (opcional)",
                    placeholder = "para recuperar tu cuenta",
                    value = email,
                    onValueChange = { email = it },
                    keyboardType = KeyboardType.Email,
                    enabled = !state.isLoading,
                )
                AuthPrimaryButton(
                    title = "Guardar y continuar",
                    onClick = { viewModel.completeSetup(newPassword, confirmPassword, email) },
                    isLoading = state.isLoading,
                )
            }

            PrimerLoginStep.VERIFY_EMAIL -> {
                AuthHeading("Verifica tu correo", "Te enviaremos un código de 6 dígitos para verificar tu correo electrónico.")
                state.errorMessage?.let { AuthErrorBox(it) }

                if (!state.codeSent) {
                    AuthPrimaryButton(
                        title = "Enviar código de verificación",
                        onClick = { viewModel.sendCode() },
                        isLoading = state.isLoading,
                    )
                } else {
                    AuthInfoBox("Código enviado a tu correo. Expira en 30 minutos.")
                    AuthInput(
                        label = "Código de verificación",
                        placeholder = "123456",
                        value = code,
                        onValueChange = { raw -> code = raw.filter { it.isDigit() }.take(6) },
                        keyboardType = KeyboardType.Number,
                        enabled = !state.isLoading,
                    )
                    AuthPrimaryButton(
                        title = "Verificar correo",
                        onClick = { viewModel.verifyCode(code) },
                        isLoading = state.isLoading,
                    )
                    AuthLink("Reenviar código", { viewModel.sendCode() }, modifier = Modifier.padding(top = 14.dp))
                }

                AuthLink("Verificar más tarde", { viewModel.skip() }, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
