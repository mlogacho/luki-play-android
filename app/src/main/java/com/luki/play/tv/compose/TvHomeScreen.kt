// tv/compose/TvHomeScreen.kt
package com.luki.play.tv.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.feature.home.ChannelRow
import com.luki.play.feature.home.HomeViewModel

/**
 * Home en Compose for TV. Reusa el mismo [HomeViewModel] que móvil — el
 * cambio respecto a [com.luki.play.feature.home.HomeScreen] son los
 * componentes TV (`TvLazyColumn`, `TvLazyRow`) y el manejo de focus.
 *
 * Navegación D-Pad nativa: el framework Compose-TV gestiona focus traversal
 * a través de elementos `focusable()`; no se necesita inyección JS.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvHomeScreen(
    onChannelClick: (Channel) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.isRefreshing && state.rows.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
        } else {
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item { TvTopBar() }
                items(state.rows) { row ->
                    TvChannelRow(row = row, onChannelClick = onChannelClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTopBar() {
    Text(
        text = "Luki Play",
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 48.dp),
        style = MaterialTheme.typography.headlineLarge,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvChannelRow(
    row: ChannelRow,
    onChannelClick: (Channel) -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = row.category,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 48.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(row.channels) { ch ->
                TvChannelCard(channel = ch, onClick = { onChannelClick(ch) })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvChannelCard(channel: Channel, onClick: () -> Unit) {
    val focused = remember { androidx.compose.runtime.mutableStateOf(false) }
    val containerColor = MaterialTheme.colorScheme.surface
    val borderColor = if (focused.value) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 124.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(borderColor)
            .padding(if (focused.value) 3.dp else 0.dp)
            .background(containerColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused.value = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logoUrl != null) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        } else {
            Text(
                text = channel.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
