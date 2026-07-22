// feature/detail/ChannelLaunchScreen.kt
package com.luki.play.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luki.play.player.StreamConfig
import com.luki.play.ui.LukiGradientBackground
import com.luki.play.ui.LukiPalette

/**
 * Paso de lanzamiento en móvil: resuelve el stream y abre el reproductor.
 *
 * El portal no tiene pantalla de detalle — desde el panel de acciones va
 * directo a `/player/[id]`. Aquí hace falta un destino intermedio de todos
 * modos porque el stream se pide al backend y el control parental puede
 * exigir PIN, pero no se muestra ficha: solo un indicador de carga, y en
 * cuanto el reproductor arranca esta ruta se saca del back-stack para que
 * volver atrás lleve al catálogo y no a una pantalla en blanco.
 *
 * TV conserva [ChannelDetailScreen], donde una ficha con su botón sí es lo
 * esperable en una interfaz de 10 pies.
 */
@Composable
fun ChannelLaunchScreen(
    onPlay: (StreamConfig) -> Unit,
    onBack: () -> Unit,
    onRequestParentalGate: (onVerified: () -> Unit, onDismissed: () -> Unit) -> Unit = { _, _ -> },
    viewModel: ChannelDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Una sola vez por entrada: sin esto, al volver del reproductor la
    // pantalla se relanzaría sola y quedaría en bucle.
    var launched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            viewModel.requestPlay()
        }
    }

    LaunchedEffect(state.playRequest) {
        state.playRequest?.let { config ->
            viewModel.consumePlayRequest()
            onPlay(config)
            onBack()
        }
    }

    LaunchedEffect(state.parentalGateRequired) {
        if (state.parentalGateRequired) {
            onRequestParentalGate(
                /* onVerified */ { viewModel.onParentalVerified() },
                // Cancelar el PIN vuelve al catálogo: quedarse aquí dejaría
                // una pantalla de carga que ya no espera nada.
                /* onDismissed */ {
                    viewModel.dismissParentalGate()
                    onBack()
                },
            )
        }
    }

    LukiGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val error = state.errorMessage
            if (error == null) {
                CircularProgressIndicator(color = LukiPalette.Accent)
            } else {
                Text(
                    text = "No se pudo abrir el canal",
                    color = Color.White,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = error,
                    color = LukiPalette.CardSubtitle,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}
