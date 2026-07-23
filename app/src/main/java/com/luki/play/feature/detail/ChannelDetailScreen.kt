// feature/detail/ChannelDetailScreen.kt
package com.luki.play.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luki.play.player.StreamConfig
import com.luki.play.ui.LukiGradientBackground
import com.luki.play.ui.LukiPalette

/**
 * Ficha de canal para TV (10-foot). El portal no tiene pantalla de detalle en
 * móvil (va directo al player), pero en 10 pies una ficha SÍ encaja: da un
 * objetivo de foco claro para el mando antes de reproducir.
 *
 * Usa la línea gráfica de marca ([LukiPalette]) igual que el home de TV, no el
 * tema Material genérico.
 */
@Composable
fun ChannelDetailScreen(
    onPlay: (StreamConfig) -> Unit,
    onBack: () -> Unit,
    onRequestParentalGate: (onVerified: () -> Unit, onDismissed: () -> Unit) -> Unit = { _, _ -> },
    viewModel: ChannelDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(state.playRequest) {
        state.playRequest?.let {
            onPlay(it)
            viewModel.consumePlayRequest()
        }
    }

    LaunchedEffect(state.parentalGateRequired) {
        if (state.parentalGateRequired) {
            onRequestParentalGate(
                { viewModel.onParentalVerified() },
                { viewModel.dismissParentalGate() },
            )
        }
    }

    LukiGradientBackground {
        val channel = state.channel
        if (channel == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = LukiPalette.Accent,
            )
            return@LukiGradientBackground
        }

        // Auto-foco al botón: al entrar a la ficha, pulsar OK reproduce.
        LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            // Logo sobre blanco con distintivo EN VIVO.
            Box(
                modifier = Modifier
                    .size(width = 300.dp, height = 190.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 260.dp, height = 150.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Tv,
                        contentDescription = null,
                        tint = Color(0x26000000),
                        modifier = Modifier.size(88.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LukiPalette.LiveRed)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                    Text("EN VIVO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = channel.category.ifBlank { "En vivo" },
                color = LukiPalette.CardSubtitle,
                fontSize = 18.sp,
            )

            Spacer(Modifier.height(28.dp))

            TvPlayButton(
                loading = state.isLoadingStream,
                focusRequester = playFocus,
                onClick = { viewModel.requestPlay() },
            )

            state.errorMessage?.let {
                Spacer(Modifier.height(16.dp))
                Text(text = it, color = LukiPalette.LiveRed, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun TvPlayButton(
    loading: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "playScale")

    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(LukiPalette.Accent.copy(alpha = if (loading) 0.6f else 1f))
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 40.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = LukiPalette.OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Text("Cargando…", color = LukiPalette.OnAccent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = LukiPalette.OnAccent, modifier = Modifier.size(24.dp))
            Text("Ver ahora", color = LukiPalette.OnAccent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
