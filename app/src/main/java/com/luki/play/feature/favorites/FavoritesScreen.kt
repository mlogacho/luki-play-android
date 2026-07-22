// feature/favorites/FavoritesScreen.kt
package com.luki.play.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.ui.ChannelActionPanel
import com.luki.play.ui.ChannelCardGap
import com.luki.play.ui.LukiChannelCard
import com.luki.play.ui.LukiGradientBackground
import com.luki.play.ui.LukiPalette
import com.luki.play.ui.LukiSectionHeader
import com.luki.play.ui.rememberChannelCardWidth
import com.luki.play.ui.rememberRowPadding

/**
 * "Mi Lista".
 *
 * En el portal esta pestaña es un placeholder — una etiqueta centrada, nunca
 * se implementó. Aquí se construye de verdad reutilizando la card y la
 * cabecera del home, así que el aspecto sigue siendo el del portal aunque la
 * pantalla no exista allí.
 *
 * Se presenta en mosaico (no en fila deslizable) porque no hay categorías
 * que separar: es una única lista del usuario.
 */
@Composable
fun FavoritesScreen(
    onChannelClick: (Channel) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val horizontalPadding = rememberRowPadding()
    val cardWidth = rememberChannelCardWidth()

    var selectedChannelId by remember { mutableStateOf<String?>(null) }

    LukiGradientBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 20.dp)
        ) {
            LukiSectionHeader(
                title = "Mi Lista",
                horizontalPadding = horizontalPadding,
                icon = Icons.Outlined.FavoriteBorder,
            )
            Spacer(Modifier.height(14.dp))

            if (state.channels.isEmpty() && !state.isLoading) {
                EmptyFavorites(Modifier.fillMaxSize())
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cardWidth),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = 60.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(ChannelCardGap),
                    verticalArrangement = Arrangement.spacedBy(ChannelCardGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.channels, key = { it.id }) { channel ->
                        LukiChannelCard(
                            channel = channel,
                            width = cardWidth,
                            isFavorite = true,
                            onClick = { selectedChannelId = channel.id },
                        )
                    }
                }
            }
        }

        val selected = selectedChannelId?.let { id ->
            state.channels.firstOrNull { it.id == id }
        }
        if (selected != null) {
            ChannelActionPanel(
                channel = selected,
                isFavorite = true,
                onPlay = {
                    selectedChannelId = null
                    onChannelClick(selected)
                },
                onToggleFavorite = {
                    // Al quitarlo desaparece de la lista, así que el panel se
                    // cierra: quedaría apuntando a un canal que ya no está.
                    selectedChannelId = null
                    viewModel.removeFavorite(selected.id)
                },
                onClose = { selectedChannelId = null },
            )
        }
    }
}

@Composable
private fun EmptyFavorites(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = LukiPalette.CardSubtitle,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Tu lista está vacía",
            color = Color.White,
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Marca un canal como favorito desde Inicio y lo verás aquí.",
            color = LukiPalette.CardSubtitle,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
