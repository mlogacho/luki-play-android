// feature/home/HomeScreen.kt
package com.luki.play.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.luki.play.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.data.catalog.domain.Slider

/**
 * Pantalla principal del móvil en Compose.
 *
 * Layout:
 *   ┌─ HeroCarousel (sliders)
 *   ├─ ChannelRow x N (agrupados por categoría)
 *   └─ Loading overlay si refresh inicial
 *
 * Tap en card → [onChannelClick]; tap en hero slider con OpenChannel → idem.
 */
@Composable
fun HomeScreen(
    onChannelClick: (Channel) -> Unit,
    onOpenSearch: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Fondo del portal: degradado vertical rich → deep → void con el
            // quiebre al 35 % (home.tsx: LinearGradient locations [0, .35, 1]).
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f    to HomePalette.GradientTop,
                        0.35f to HomePalette.GradientMid,
                        1f    to HomePalette.GradientBottom,
                    )
                )
            )
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    LukiTopBar(onSearchClick = onOpenSearch, onLogoutClick = onLogout)
                }
                if (state.sliders.isNotEmpty()) {
                    item { HeroCarousel(state.sliders, onChannelClick) }
                }
                items(state.rows, key = { it.category }) { row ->
                    ChannelRowView(row = row, onChannelClick = onChannelClick)
                }
            }

            if (state.isRefreshing && state.rows.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = HomePalette.Accent,
                )
            }
        }
    }
}

/** Colores del home del portal (`(tabs)/home.tsx`). */
private object HomePalette {
    val GradientTop    = Color(0xFF1E0B45)
    val GradientMid    = Color(0xFF0D0520)
    val GradientBottom = Color(0xFF05020C)
    val Header         = Color(0xFF1E0B45)
    val HeaderBorder   = Color(0x0FFFFFFF)   // rgba(255,255,255,0.06)
    val Accent         = Color(0xFFFFC107)   // ACCENT del home
    val OnAccent       = Color(0xFF140026)
}

/**
 * Barra superior del portal: fondo #1E0B45 de 43dp, logo horizontal a la
 * izquierda y acciones a la derecha. Respeta el inset de la barra de estado
 * — sin eso el logo quedaba debajo del reloj y de los iconos del sistema.
 *
 * Divergencia consciente: el portal pone aquí un avatar con menú y los
 * enlaces de sección; se replican el fondo y el logo, y se conservan las
 * acciones Buscar/Salir hasta que existan esas dos piezas nativas.
 */
@Composable
private fun LukiTopBar(onSearchClick: () -> Unit, onLogoutClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HomePalette.Header)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(43.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.luki_logo_h),
                contentDescription = "Luki Play",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(20.dp)
                    .weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            TopBarAction(text = "Buscar", onClick = onSearchClick)
            TopBarAction(text = "Salir", onClick = onLogoutClick)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HomePalette.HeaderBorder)
        )
    }
}

@Composable
private fun TopBarAction(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0x1FFFFFFF),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun HeroCarousel(
    sliders: List<Slider>,
    onChannelClick: (Channel) -> Unit,
) {
    // Coalesce duplicados por id; el backend ocasionalmente devuelve repetidos.
    val items = remember(sliders) { sliders.distinctBy { it.id } }
    val pagerState = rememberPagerState(pageCount = { items.size })

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        pageSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { page ->
        val slider = items[page]
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = slider.imageUrl,
                    contentDescription = slider.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    Text(
                        slider.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    slider.subtitle?.let {
                        Text(it, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRowView(row: ChannelRow, onChannelClick: (Channel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = row.category,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(row.channels, key = { it.id }) { ch ->
                ChannelCard(channel = ch, onClick = { onChannelClick(ch) })
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .size(width = 120.dp, height = 80.dp)
            .clip(RoundedCornerShape(8.dp)),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
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
}

