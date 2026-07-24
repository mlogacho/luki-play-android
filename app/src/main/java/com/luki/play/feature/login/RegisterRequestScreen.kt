// feature/login/RegisterRequestScreen.kt
package com.luki.play.feature.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * "Solicitar acceso" para no-clientes, réplica del `RegisterRequestForm` de
 * `frontend/app/(auth)/login.tsx`. Sustituye al único escape que quedaba al
 * WebView del portal (antes `onOpenPortal`). Usa el mismo sistema de diseño que
 * el login ([AuthScaffold] y compañía).
 *
 * @param onBackToLogin volver a la pantalla de login.
 */
@Composable
fun RegisterRequestScreen(
    onBackToLogin: () -> Unit,
    viewModel: RegisterRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var nombres by rememberSaveable { mutableStateOf("") }
    var apellidos by rememberSaveable { mutableStateOf("") }
    var idNumber by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var direccion by rememberSaveable { mutableStateOf("") }

    AuthScaffold {
        AuthBackLink("Volver al inicio de sesión", onBackToLogin)

        if (state.done) {
            AuthHeading("Solicitud enviada", null)
            AuthInfoBox("Tu solicitud fue recibida. Un agente de Luki te contactará pronto para activar tu cuenta.")
            AuthPrimaryButton("Volver al inicio", onBackToLogin)
            return@AuthScaffold
        }

        AuthHeading("Solicitar acceso", "Completa el formulario y un agente te contactará para activar tu cuenta.")
        state.errorMessage?.let { AuthErrorBox(it) }

        AuthInput(
            label = "Nombres *",
            placeholder = "Ej: Juan Carlos",
            value = nombres,
            onValueChange = { nombres = it },
            enabled = !state.isLoading,
        )
        AuthInput(
            label = "Apellidos *",
            placeholder = "Ej: Pérez López",
            value = apellidos,
            onValueChange = { apellidos = it },
            enabled = !state.isLoading,
        )
        AuthInput(
            label = "Cédula *",
            placeholder = "Ej: 1720345678",
            value = idNumber,
            onValueChange = { idNumber = it },
            keyboardType = KeyboardType.Number,
            enabled = !state.isLoading,
        )
        AuthInput(
            label = "Teléfono celular *",
            placeholder = "Ej: 0991234567",
            value = telefono,
            onValueChange = { telefono = it },
            keyboardType = KeyboardType.Phone,
            enabled = !state.isLoading,
        )
        AuthInput(
            label = "Correo electrónico (opcional)",
            placeholder = "tu@correo.com",
            value = email,
            onValueChange = { email = it },
            keyboardType = KeyboardType.Email,
            enabled = !state.isLoading,
        )
        AuthInput(
            label = "Dirección (opcional)",
            placeholder = "Ciudad, barrio...",
            value = direccion,
            onValueChange = { direccion = it },
            enabled = !state.isLoading,
        )

        AuthPrimaryButton(
            title = "Enviar solicitud",
            onClick = { viewModel.submit(nombres, apellidos, idNumber, telefono, email, direccion) },
            isLoading = state.isLoading,
        )
    }
}
