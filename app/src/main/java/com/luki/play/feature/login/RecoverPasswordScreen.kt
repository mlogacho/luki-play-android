// feature/login/RecoverPasswordScreen.kt
package com.luki.play.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Recuperación de contraseña por OTP — réplica del `ForgotForm` del front
 * web: paso 1 cédula, paso 2 código de 6 + nueva contraseña, paso 3 listo.
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(
                onClick = {
                    if (state.step == RecoverStep.VERIFY) viewModel.backToIdentity() else onBackToLogin()
                },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    if (state.step == RecoverStep.VERIFY) "Cambiar cédula"
                    else "Volver al inicio de sesión"
                )
            }

            Text(
                "Recuperar contraseña",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Text(
                "Paso ${state.step.ordinal + 1} de 3",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            state.errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            state.infoMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            when (state.step) {
                RecoverStep.IDENTITY -> {
                    Text(
                        "Ingresa tu cédula y te enviaremos un código de verificación a tu correo registrado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                    OutlinedTextField(
                        value = idNumber,
                        onValueChange = { idNumber = it },
                        label = { Text("Cédula de identidad") },
                        placeholder = { Text("Ej: 1720345678") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryActionButton(
                        text = "Enviar código de recuperación",
                        isLoading = state.isLoading,
                        onClick = { viewModel.requestOtp(idNumber) },
                    )
                }

                RecoverStep.VERIFY -> {
                    Text(
                        "Ingresa el código que enviamos a tu correo y elige tu nueva contraseña.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                    OutlinedTextField(
                        value = code,
                        // El backend exige exactamente 6; se normaliza igual
                        // que la web (mayúsculas, alfanumérico, tope 6).
                        onValueChange = { raw ->
                            code = raw.uppercase().filter { it.isLetterOrDigit() }.take(6)
                        },
                        label = { Text("Código de verificación") },
                        placeholder = { Text("Ej: A1B2C3") },
                        singleLine = true,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Nueva contraseña") },
                        supportingText = { Text(PasswordPolicy.RULE_HINT) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirmar contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    PrimaryActionButton(
                        text = "Restablecer contraseña",
                        isLoading = state.isLoading,
                        onClick = { viewModel.resetPassword(code, newPassword, confirmPassword) },
                    )
                    TextButton(
                        onClick = { viewModel.requestOtp(idNumber) },
                        enabled = !state.isLoading,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Reenviar código")
                    }
                }

                RecoverStep.DONE -> {
                    Text(
                        "Tu contraseña fue restablecida. Ya puedes iniciar sesión.",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                    PrimaryActionButton(
                        text = "Ir al inicio de sesión",
                        isLoading = false,
                        onClick = onBackToLogin,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .height(48.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(text)
        }
    }
}
