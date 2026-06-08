// feature/detail/ChannelDetailScreen.kt
package com.luki.play.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luki.play.player.StreamConfig

@Composable
fun ChannelDetailScreen(
    onPlay: (StreamConfig) -> Unit,
    onBack: () -> Unit,
    onRequestParentalGate: (onVerified: () -> Unit, onDismissed: () -> Unit) -> Unit = { _, _ -> },
    viewModel: ChannelDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.playRequest) {
        state.playRequest?.let {
            onPlay(it)
            viewModel.consumePlayRequest()
        }
    }

    LaunchedEffect(state.parentalGateRequired) {
        if (state.parentalGateRequired) {
            onRequestParentalGate(
                /* onVerified */  { viewModel.onParentalVerified() },
                /* onDismissed */ { viewModel.dismissParentalGate() },
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val channel = state.channel
            if (channel == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxSize(),
                    ) { Box(Modifier, contentAlignment = Alignment.Center) {
                        Text(channel.name, color = MaterialTheme.colorScheme.onSurface)
                    } }
                }
            }

            Text(
                text = channel.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = channel.category,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Button(
                onClick = { viewModel.requestPlay() },
                enabled = !state.isLoadingStream,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isLoadingStream) "Cargando…" else "Reproducir")
            }

            state.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
