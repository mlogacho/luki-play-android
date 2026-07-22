// feature/search/SearchScreen.kt
package com.luki.play.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
 * Búsqueda de canales.
 *
 * En el portal esta pestaña también es un placeholder, así que no hay diseño
 * que copiar: se compone con las piezas del home (fondo de marca, cabecera de
 * sección y la misma card) para que no desentone con el resto.
 */
@Composable
fun SearchScreen(
    onChannelClick: (Channel) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

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
                title = "Buscar",
                horizontalPadding = horizontalPadding,
                icon = Icons.Outlined.Search,
            )
            Spacer(Modifier.height(14.dp))

            SearchField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.padding(horizontal = horizontalPadding),
            )
            Spacer(Modifier.height(16.dp))

            when {
                query.length < 2 -> SearchHint("Escribe al menos 2 letras")
                results.isEmpty() -> SearchHint("Sin resultados para \"$query\"")
                else -> LazyVerticalGrid(
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
                    items(results, key = { it.id }) { channel ->
                        LukiChannelCard(
                            channel = channel,
                            width = cardWidth,
                            isFavorite = channel.id in favorites,
                            onClick = { selectedChannelId = channel.id },
                        )
                    }
                }
            }
        }

        val selected = selectedChannelId?.let { id -> results.firstOrNull { it.id == id } }
        if (selected != null) {
            ChannelActionPanel(
                channel = selected,
                isFavorite = selected.id in favorites,
                onPlay = {
                    selectedChannelId = null
                    onChannelClick(selected)
                },
                onToggleFavorite = {
                    viewModel.toggleFavorite(selected.id, selected.id !in favorites)
                },
                onClose = { selectedChannelId = null },
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = LukiPalette.CardSubtitle,
            modifier = Modifier.size(18.dp),
        )
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Buscar canales",
                    color = LukiPalette.CardSubtitle,
                    fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(LukiPalette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchHint(text: String) {
    Text(
        text = text,
        color = LukiPalette.CardSubtitle,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 32.dp, end = 32.dp),
    )
}
