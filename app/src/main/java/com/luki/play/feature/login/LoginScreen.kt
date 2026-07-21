// feature/login/LoginScreen.kt
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Login de abonado — réplica del front web (`app/(auth)/login.tsx`):
 * dos modos (cédula / contrato), teclado numérico para la cédula,
 * placeholder idéntico y validación mínima (trim + requerido).
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var credential by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            onLoggedIn()
            viewModel.consumeLoggedIn()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Iniciar sesión",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            TabRow(
                selectedTabIndex = state.mode.ordinal,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Tab(
                    selected = state.mode == LoginMode.CEDULA,
                    onClick = { viewModel.setMode(LoginMode.CEDULA) },
                    text = { Text("Cédula") },
                )
                Tab(
                    selected = state.mode == LoginMode.CONTRATO,
                    onClick = { viewModel.setMode(LoginMode.CONTRATO) },
                    text = { Text("Contrato") },
                )
            }

            OutlinedTextField(
                value = credential,
                onValueChange = { credential = it },
                label = {
                    Text(
                        when (state.mode) {
                            LoginMode.CEDULA   -> "Cédula de identidad"
                            LoginMode.CONTRATO -> "Número de contrato"
                        }
                    )
                },
                placeholder = {
                    Text(
                        when (state.mode) {
                            LoginMode.CEDULA   -> "Ej: 1720345678"
                            LoginMode.CONTRATO -> "Ej: C-000123"
                        }
                    )
                },
                singleLine = true,
                // Teclado numérico solo como HINT (igual que el keyboardType
                // "numeric" del front): no filtra caracteres — un RUC o un
                // contrato alfanumérico deben poder escribirse.
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (state.mode) {
                        LoginMode.CEDULA   -> KeyboardType.Number
                        LoginMode.CONTRATO -> KeyboardType.Text
                    }
                ),
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            state.errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Button(
                onClick = { viewModel.login(credential, password) },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(48.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Ingresar")
                }
            }
        }
    }
}
