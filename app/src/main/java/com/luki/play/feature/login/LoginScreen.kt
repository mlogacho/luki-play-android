// feature/login/LoginScreen.kt
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
 * Login de abonado — réplica visual y funcional del `LoginForm` del portal
 * (`frontend/app/(auth)/login.tsx`): mismo gradiente, logo, tarjeta,
 * tipografía, copys y orden de elementos.
 *
 * Nota de fidelidad: el portal ofrece SOLO cédula en esta pantalla. El
 * endpoint de contrato existe en la API (y en [LoginViewModel]) pero no
 * está expuesto en la UI web, así que aquí tampoco se expone.
 *
 * @param onActivateAccount  "Activa tu cuenta" — flujo de primer acceso.
 * @param onRequestAccess    "Solicitar acceso" — registro de no-clientes.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onActivateAccount: () -> Unit,
    onRequestAccess: () -> Unit,
    onPrimerLogin: () -> Unit,
    onActivateTv: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var idNumber by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            // Clave temporal / primer login → configurar la cuenta antes del Home
            // (igual que el portal: login.tsx enruta a 'primer-login').
            if (state.requiresPrimerLogin) onPrimerLogin() else onLoggedIn()
            viewModel.consumeLoggedIn()
        }
    }

    AuthScaffold {
        AuthHeading(
            title = "Bienvenido de nuevo",
            subtitle = "Inicia sesión con tu cédula y contraseña",
        )

        state.errorMessage?.let { AuthErrorBox(it) }

        AuthInput(
            label = "Cédula de identidad",
            placeholder = "Ej: 1720345678",
            value = idNumber,
            onValueChange = { idNumber = it },
            keyboardType = KeyboardType.Number,
            enabled = !state.isLoading,
        )

        AuthInput(
            label = "Contraseña",
            placeholder = "••••••••",
            value = password,
            onValueChange = { password = it },
            secure = true,
            keyboardType = KeyboardType.Password,
            enabled = !state.isLoading,
        )

        AuthPrimaryButton(
            title = "Iniciar sesión",
            isLoading = state.isLoading,
            onClick = { viewModel.login(idNumber, password) },
        )

        AuthLink(
            text = "¿Olvidaste tu contraseña?",
            onClick = onForgotPassword,
            modifier = Modifier.padding(top = 16.dp),
        )

        AuthFooterAction(
            question = "¿Primera vez?",
            action = "Activa tu cuenta",
            onClick = onActivateAccount,
            modifier = Modifier.padding(top = 24.dp),
        )

        AuthFooterAction(
            question = "¿No eres cliente Luki?",
            action = "Solicitar acceso",
            onClick = onRequestAccess,
            modifier = Modifier.padding(top = 12.dp),
        )

        // El portal llega a esto por la URL del QR del televisor; en la app se
        // ofrece aquí para poder conectar un TV sin salir a un navegador.
        AuthFooterAction(
            question = "¿Tienes un TV con código?",
            action = "Activar TV",
            onClick = onActivateTv,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
