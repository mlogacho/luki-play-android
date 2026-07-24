// feature/login/ActivateTvScreen.kt
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
 * "Activar TV" desde el teléfono, réplica de la sub-pantalla `'tv'` de
 * `frontend/app/(auth)/activar.tsx` — la página a la que apunta el QR del
 * televisor. Permite conectar el TV sin salir de la app.
 *
 * @param onBackToLogin volver al login.
 * @param onActivateAccount la cuenta tiene clave temporal → "Activa tu cuenta".
 * @param onForgotPassword recuperar contraseña.
 */
@Composable
fun ActivateTvScreen(
    onBackToLogin: () -> Unit,
    onActivateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: ActivateTvViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var tvCode by rememberSaveable { mutableStateOf("") }
    var idNumber by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.needsAccountActivation) {
        if (state.needsAccountActivation) {
            onActivateAccount()
            viewModel.consumeNeedsAccountActivation()
        }
    }

    AuthScaffold {
        AuthBackLink("Volver al inicio de sesión", onBackToLogin)

        if (state.connected) {
            AuthHeading("¡TV activado!", null)
            AuthInfoBox("Tu televisor ya está conectado. Ya puedes empezar a ver.")
            AuthPrimaryButton("Listo", onBackToLogin)
            return@AuthScaffold
        }

        AuthHeading("Activar TV", "Ingresa el código que aparece en tu TV y tus credenciales de acceso.")
        state.infoMessage?.let { AuthInfoBox(it) }
        state.errorMessage?.let { AuthErrorBox(it) }

        AuthInput(
            label = "Código del TV",
            placeholder = "Ej: AB3K7P",
            value = tvCode,
            onValueChange = { raw -> tvCode = raw.uppercase().filter { it.isLetterOrDigit() }.take(6) },
            enabled = !state.isLoading,
        )
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
            title = "Activar TV",
            onClick = { viewModel.activate(tvCode, idNumber, password) },
            isLoading = state.isLoading,
        )

        AuthLink("¿Olvidaste tu contraseña?", onForgotPassword, modifier = Modifier.padding(top = 16.dp))
        AuthFooterAction(
            question = "¿Primera vez?",
            action = "Activa tu cuenta",
            onClick = onActivateAccount,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}
